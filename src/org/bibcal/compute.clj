(ns org.bibcal.compute
  (:require [tick.core :as tick]
            [cheshire.core :as json]
            [hiccup.table :refer [to-table1d]]
            [xyz.thoren.luminary :as l]
            [clojure.string :as str]))

(println "Loading org.bibcal.compute namespace")

;; API response cache with 24-hour TTL and 1000 entry limit
(def max-cache-size 1000)
(def cache-ttl-ms (* 24 60 60 1000)) ;; 24 hours in ms
(def api-cache (atom {}))

(defn- prune-cache
  "Remove expired entries and enforce size limit using LRU policy"
  []
  (let [now (System/currentTimeMillis)
        entries (seq @api-cache)
        ;; Remove expired entries
        valid-entries (filter #(< now (:expires (val %))) entries)]
    ;; If still over limit, sort by last-accessed time and keep only most recent
    (when (> (count valid-entries) max-cache-size)
      (let [sorted-entries (sort-by #(:last-accessed (val %)) > valid-entries)
            entries-to-keep (take max-cache-size sorted-entries)]
        (reset! api-cache (into {} entries-to-keep))))))

(defn- cache-get [key]
  (let [entry (get @api-cache key)
        now (System/currentTimeMillis)]
    (when (and entry (< now (:expires entry)))
      ;; Update last-accessed time
      (swap! api-cache assoc-in [key :last-accessed] now)
      (:data entry))))

(defn- cache-put [key data]
  (let [now (System/currentTimeMillis)
        expires (+ now cache-ttl-ms)]
    ;; Prune cache before adding new entry if it's getting full
    (when (>= (count @api-cache) max-cache-size)
      (prune-cache))
    ;; Add new entry with current timestamp as last-accessed
    (swap! api-cache assoc key {:data data 
                                :expires expires 
                                :last-accessed now})
    data))

(defn get-locationiq-api-key []
  (or (System/getenv "LOCATIONIQ_API_KEY") 
      (throw (Exception. "LOCATIONIQ_API_KEY environment variable is not set"))))

(defn get-ipgeolocation-api-key []
  (or (System/getenv "IPGEOLOCATION_API_KEY")
      (throw (Exception. "IPGEOLOCATION_API_KEY environment variable is not set"))))

(defn- mask-key [s] (str/replace s #"[kK]ey=[^&]+&?" "key=***&"))

(defn- call-api
  "Call API endpoint with caching (24 hour TTL)"
  [url]
  (let [cache-key url]
    (if-let [cached-data (cache-get cache-key)]
      (do 
        (println "Cache hit for" (mask-key url))
        cached-data)
      (try
        (println "Cache miss for" (mask-key url))
        (let [result (json/parse-string (slurp url) true)]
          (cache-put cache-key result)
          result)
        (catch java.io.FileNotFoundException e
          (throw (Exception. (str "Not found: " (mask-key (.getMessage e))))))
        (catch Exception e
          (throw (Exception. (str "Unknown exception: " (mask-key (.getMessage e))))))))))

(defn- lookup-ip
  [ip]
  (try
    (call-api (str "http://ip-api.com/json/" ip))
    (catch Exception e
      (println "Error looking up IP information for" ip ":" (.getMessage e))
      {})))

(defn- lookup-location
  [lat lon]
  (try
    (let [api-key (get-locationiq-api-key)]
      (call-api (str "https://eu1.locationiq.com/v1/reverse.php?"
                   "key=" api-key
                   "&lat=" lat
                   "&lon=" lon
                   "&normalizeaddress" 1
                   "&limit=" 1
                   "&format=json")))
    (catch Exception e
      (println "Error in lookup-location:" (.getMessage e))
      {})))

(defn- ipgeolocation-timezone [lat lon]
  (:timezone
    (try
      (let [api-key (get-ipgeolocation-api-key)]
        (call-api (str "https://api.ipgeolocation.io/timezone?"
                       "apiKey=" api-key
                       "&lat=" lat
                       "&long=" lon)))
      (catch Exception e
        (println "Error in ipgeolocation-timezone:" (.getMessage e))
        nil))))

(defn- locationiq-timezone [lat lon]
  (get-in
    (try
      (let [api-key (get-locationiq-api-key)]
        (call-api (str "https://eu1.locationiq.com/v1/timezone.php?"
                       "key=" api-key
                       "&lat=" lat
                       "&lon=" lon)))
      (catch Exception e
        (println "Error in locationiq-timezone:" (.getMessage e))
        nil))
    [:timezone :name]))

(defn- lookup-timezone
  [lat lon]
  (or (ipgeolocation-timezone lat lon)
      (locationiq-timezone lat lon)))

(defn get-location
  ([ip]
   (try
     (let [loc (lookup-ip ip)]
       (if (empty? loc)
         (do
           (println "Warning: Empty location data from IP lookup for: " ip)
           {:timezone "UTC"
            :area "Unknown"
            :country "Unknown"
            :region "Unknown"
            :lat 0
            :lon 0
            :ip ip})
         {:timezone (:timezone loc)
          :area (:city loc)
          :country (:country loc)
          :region (:regionName loc)
          :lat (:lat loc)
          :lon (:lon loc)
          :ip (:query loc)}))
     (catch Exception e
       (println "Error in get-location[ip]:" (.getMessage e))
       {:timezone "UTC"
        :area "Unknown"
        :country "Unknown"
        :region "Unknown"
        :lat 0
        :lon 0
        :ip ip})))
  ([lat lon ip]
   (try
     (let [ip-loc (get-location ip)
           tz (or (lookup-timezone lat lon)
                  (:timezone ip-loc))
           loc (lookup-location lat lon)
           area (if (and loc (:display_name loc))
                  (first (str/split (:display_name loc) #", "))
                  "Unknown")
           region (get-in loc [:address :county] "Unknown")
           country (get-in loc [:address :country] "Unknown")]
       (assoc ip-loc
              :lat lat
              :lon lon
              :timezone (or tz "UTC")
              :area area
              :region region
              :country country))
     (catch Exception e
       (println "Error in get-location[lat,lon,ip]:" (.getMessage e))
       {:timezone "UTC"
        :area "Unknown"
        :country "Unknown"
        :region "Unknown"
        :lat lat
        :lon lon
        :ip ip}))))

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

(defn time-table [msgs]
  (to-table1d (remove nil? msgs)
              [0 "Key" 1 "Value"] {:table-attrs
                                     {:class "table table-striped"}
                                     :th-attrs
                                     {:scope "row"}}))

(defn current-time
  [t d]
  (let [h (:hebrew d)
        dt (:time d)
        tf (tick/formatter "yyy-MM-dd HH:mm:ss")
        next-day (l/go-forward 1 :seconds (get-in dt [:day :end]))
        sabbath (:sabbath h)
        major-f (feast-or-false (:major-feast-day h))
        minor-f (feast-or-false (:minor-feast-day h))]
    (time-table [["Date" (alt-brief-date h)]
                 ["ISO date" (alt-iso-date h)]
                 ["Traditional date" (trad-brief-date h)]
                 ["Traditional ISO date" (trad-iso-date h)]
                 ["Day of week" (:day-of-week h)]
                 (when sabbath ["Sabbath" "Yes"])
                 (when major-f ["Major feast day" major-f])
                 (when minor-f ["Minor feast day" minor-f])
                 ["Current local time" (tick/format tf t)]
                 ["Start of next day" (tick/format tf next-day)]])))

(defn current-time-details
  [m d]
  (let [{:keys [lat lon area region country timezone ip]} m
        h (:hebrew d)
        dt (:time d)
        sabbath (:sabbath h)
        major-f (feast-or-false (:major-feast-day h))
        minor-f (feast-or-false (:minor-feast-day h))
        tf (tick/formatter "yyy-MM-dd HH:mm:ss")
        fmt-time #(tick/format tf (get-in dt [%1 %2]))]
    (time-table [["Sabbath" (if sabbath "Yes" "No")]
                 ["Major feast day" (or major-f "No")]
                 ["Minor feast day" (or minor-f "No")]
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
                 ["IP" ip]])))

(defn feast-table
  [t coll]
  (to-table1d coll
              [0 "Date" 1 "Feast"]
              {:table-attrs {:class "table table-striped"}
               :data-td-attrs (fn [label-key val]
                                (when (= label-key 0)
                                  (when (tick/> (tick/date t)
                                                (tick/date val))
                                    {:class "past-date"})))}))

(defn mute-past-feasts
  [coll]
  (mapv #(if-not (vector? %)
           %
           (vec (for [v %]
                  (if-not (seq? v)
                    v
                    (for [i v]
                      (if-not (vector? i)
                        i
                        (if (= (:class (second (first (nth i 2)))) "past-date")
                          [(first i) {:class "past-feast text-muted"} (nth i 2)]
                          [(first i) nil (nth i 2)])))))))
        coll))

(defn feast-days-in-year
  [y t]
  (->> (l/list-of-feast-days-in-year y)
       (map #(str/split % #" " 2))
       (feast-table t)
       mute-past-feasts))

(defn- current-year
  [location]
  (try
    (let [tz (or (:timezone location) "UTC")]
      (->> (l/now)
           (l/in-zone tz)
           tick/year
           tick/int))
    (catch Exception e
      (println "Error getting current year:" (.getMessage e))
      (tick/int (tick/year (tick/now))))))

(defn feast-days-in-current-year
  [location t]
  (try
    (let [year (current-year location)
          tz (or (:timezone location) "UTC")
          zoned-t (try
                    (l/in-zone tz t)
                    (catch Exception e
                      (println "Error zoning time for feast days:" (.getMessage e))
                      t))]
      [:div
       (feast-days-in-year year zoned-t)])
    (catch Exception e
      (println "Error generating feast days for current year:" (.getMessage e))
      [:div "Feast day information not available"])))

(defn feast-days-in-next-year
  [location t]
  (try
    (let [year (inc (current-year location))
          tz (or (:timezone location) "UTC")
          zoned-t (try
                    (l/in-zone tz t)
                    (catch Exception e
                      (println "Error zoning time for next year feast days:" (.getMessage e))
                      t))]
      [:div
       (feast-days-in-year year zoned-t)])
    (catch Exception e
      (println "Error generating feast days for next year:" (.getMessage e))
      [:div "Next year feast day information not available"])))