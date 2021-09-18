(ns org.bibcal.compute
  (:require [tick.core :as tick]
            [cheshire.core :as json]
            [hiccup.table :refer [to-table1d]]
            [xyz.thoren.luminary :as l]
            [clojure.string :as str]))

(def locationiq-api-key
  (or (System/getenv "LOCATIONIQ_API_KEY")
      (first (str/split-lines (slurp "/etc/locationiq_api_key")))))

(def ipgeolocation-api-key
  (or (System/getenv "IPGEOLOCATION_API_KEY")
      (first (str/split-lines (slurp "/etc/ipgeolocation_api_key")))))

(defn- mask-key [s] (str/replace s #"[kK]ey=[^&]+&?" "key=***&"))

(defn- call-api
  [s]
  (try
    (json/parse-string (slurp s) true)
    (catch java.io.FileNotFoundException e
      (throw (Exception. (str "Not found: " (mask-key (.getMessage e))))))
    (catch Exception e
      (throw (Exception. (str "Unknown exception: " (mask-key (.getMessage e))))))))

(defn- lookup-ip
  [ip]
  (call-api (str "http://ip-api.com/json/" ip)))

(defn- lookup-location
  [lat lon]
  (call-api (str "https://eu1.locationiq.com/v1/reverse.php?"
                 "key=" locationiq-api-key
                 "&lat=" lat
                 "&lon=" lon
                 "&normalizeaddress" 1
                 "&limit=" 1
                 "&format=json")))

(defn- ipgeolocation-timezone [lat lon]
  (:timezone
    (try
      (call-api (str "https://api.ipgeolocation.io/timezone?"
                     "apiKey=" ipgeolocation-api-key
                     "&lat=" lat
                     "&long=" lon))
      (catch Exception _))))

(defn- locationiq-timezone [lat lon]
  (get-in
    (try
      (call-api (str "https://eu1.locationiq.com/v1/timezone.php?"
                     "key=" locationiq-api-key
                     "&lat=" lat
                     "&lon=" lon))
      (catch Exception _))
    [:timezone :name]))

(defn- lookup-timezone
  [lat lon]
  (or (ipgeolocation-timezone lat lon)
      (locationiq-timezone lat lon)))

(defn get-location
  ([ip]
   (let [loc (lookup-ip ip)]
     {:timezone (:timezone loc)
      :area (:city loc)
      :country (:country loc)
      :region (:regionName loc)
      :lat (:lat loc)
      :lon (:lon loc)
      :ip (:query loc)}))
  ([lat lon ip]
   (let [tz (or (lookup-timezone lat lon)
                (:timezone (lookup-ip ip)))
         loc (lookup-location lat lon)
         area (first (str/split (:display_name loc) #", "))
         region (get-in loc [:address :county])
         country (get-in loc [:address :country])]
     (assoc (get-location ip)
            :lat lat
            :lon lon
            :timezone tz
            :area area
            :region region
            :country country))))

(defn- feast-or-false
  [{:keys [name day-of-feast days-in-feast] :or {name nil}}]
  (cond
    (not name) false
    (< days-in-feast 3) name
    (= days-in-feast 8) (str (l/day-numbers (dec day-of-feast)) " day of " name)
    :else (str (l/day-numbers (dec day-of-feast)) " day of the " name)))

(defn trad-brief-date [m]
  (let [names (:names m)
        month (:traditional-month-of-year names)
        day-of-month (:day-of-month names)]
    (str day-of-month " of " month)))

(defn alt-brief-date [m]
  (let [names (:names m)
        month (:month-of-year names)
        day-of-month (:day-of-month names)]
    (str day-of-month " day of the " month " month")))

(defn- iso-date
  [y m d]
  (str y "-" (format "%02d" m) "-" (format "%02d" d)))

(defn- trad-iso-date
  [{:keys [traditional-year month-of-year day-of-month]}]
  (iso-date traditional-year month-of-year day-of-month))

(defn- alt-iso-date
  [{:keys [year month-of-year day-of-month]}]
  (iso-date year month-of-year day-of-month))

(defn current-time
  [m t d]
  (let [{:keys [lat lon area region country timezone ip]} m
        h (:hebrew d)
        dt (:time d)
        tf (tick/formatter "yyy-MM-dd HH:mm:ss")
        fmt-time #(tick/format tf (get-in dt [%1 %2]))
        msgs (remove #(nil? (first %))
                     [["Gregorian time" (tick/format tf t)]
                      ["Date" (alt-brief-date h)]
                      ["ISO date" (alt-iso-date h)]
                      ["Traditional date" (trad-brief-date h)]
                      ["Traditional ISO date" (trad-iso-date h)]
                      ["Day of week" (:day-of-week h)]
                      ["Sabbath" (:sabbath h)]
                      ["Major feast day" (feast-or-false (:major-feast-day h))]
                      ["Minor feast day" (feast-or-false (:minor-feast-day h))]
                      ["Start of year" (fmt-time :year :start)]
                      ["Start of month" (fmt-time :month :start)]
                      ["Start of week" (fmt-time :week :start)]
                      ["Start of day" (fmt-time :day :start)]
                      ["End of day" (fmt-time :day :end)]
                      ["End of week" (fmt-time :week :end)]
                      ["End of month" (fmt-time :month :end)]
                      ["End of year" (fmt-time :year :end)]
                      (when (seq area) ["City" area])
                      (when (seq region) ["Region" region])
                      (when (seq country) ["Country" country])
                      ["Coordinates" (str lat "," lon)]
                      ["Timezone" timezone]
                      ["IP" ip]])]
    (to-table1d msgs [0 "Key" 1 "Value"] {:table-attrs
                                          {:class "table table-striped"}
                                          :th-attrs
                                          {:scope "row"}})))

(defn- feast-days-in-year
  [y]
  (as-> (l/list-of-feast-days-in-year y) <>
        (map #(str/split % #" " 2) <>)
        (to-table1d <>
                    [0 "Date" 1 "Feast"]
                    {:table-attrs {:class "table table-striped"}})))

(defn- current-year
  [location]
  (->> (l/now)
       (l/in-zone (:timezone location))
       tick/year
       tick/int))

(defn feast-days-in-current-year
  [location]
  (let [year (current-year location)]
    [:div
     [:h3 year]
     [:p (feast-days-in-year year)]]))

(defn feast-days-in-next-year
  [location]
  (let [year (inc (current-year location))]
    [:div
     [:h3 year]
     [:p (feast-days-in-year year)]]))
