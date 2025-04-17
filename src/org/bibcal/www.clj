(ns org.bibcal.www
  (:require [clojure.string :as str]
            [compojure.core :as core]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [org.bibcal.compute :as compute]
            [org.bibcal.main :as main]))

(defn status-500
  [request]
  {:status 500
   :headers {"Content-Type" "text/plain"}
   :body (str "Unable to fetch location data for IP: "
              (:remote-addr request))})

(defn parse-cookie
  [s]
  (when s
    (as-> (str/split s #"&") <>
          (map #(str/split % #"=") <>)
          (zipmap (map #(keyword (first %)) <>)
                  (map last <>))
          (update <> :lat read-string)
          (update <> :lon read-string))))

(defn with-cookie
  [m]
  (assoc m :saved-cookie true))

(defn default-handler
  [request & {:keys [lat lon] :or {lat nil lon nil}}]
  (let [ip (or (get-in request [:headers "cf-connecting-ip"])
               (get-in request [:headers "x-forwarded-for"])
               (get-in request [:headers "x-real-ip"]))
        location-cookie (get-in request [:cookies "location" :value])
        parsed-cookie (when location-cookie (parse-cookie location-cookie))
        location (if (and lat lon)
                   (if (and parsed-cookie
                            (= lat (:lat parsed-cookie))
                            (= lon (:lon parsed-cookie)))
                     (with-cookie parsed-cookie)
                     (compute/get-location lat lon ip))
                   (if (and parsed-cookie
                            (= ip (:ip parsed-cookie)))
                     (with-cookie parsed-cookie)
                     (compute/get-location ip)))]
    (if location
      {:status 200
       :headers {"Content-Type" "text/html"}
       :cookies {"location" {:value location
                             :secure true
                             :max-age 86400}}
       :body (main/page location)}
      (status-500 request))))

(defn coordinates
  [request]
  (as-> (get-in request [:params :coordinates]) <>
        (str/split <> #",")
        (map read-string <>)))

(defn coordinates-handler
  [request]
  (let [lat (first (coordinates request))
        lon (last (coordinates request))]
    (default-handler request :lat lat :lon lon)))

(core/defroutes app-routes
  (core/GET "/" [] #'default-handler)
  (core/GET ["/:coordinates", :coordinates #"(-?\d+\.?\d*),(-?\d+\.?\d*)"]
            request
            (coordinates-handler request))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
