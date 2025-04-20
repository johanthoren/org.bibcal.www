(ns org.bibcal.www
  (:require [clojure.string :as str]
            [compojure.core :as core]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.adapter.jetty :as jetty]
            [org.bibcal.compute :as compute]
            [org.bibcal.main :as main])
  (:gen-class))

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

(defn extract-first-ip
  "Extract the first IP address from a possibly comma-separated list of IPs."
  [ip-str]
  (when ip-str
    (first (map str/trim (str/split ip-str #",")))))

(defn default-handler
  [request & {:keys [lat lon] :or {lat nil lon nil}}]
  (try
    (let [raw-ip (or (get-in request [:headers "cf-connecting-ip"])
                     (get-in request [:headers "x-forwarded-for"])
                     (get-in request [:headers "x-real-ip"])
                     (:remote-addr request))
          ;; Extract only the first IP if multiple IPs are present in a comma-separated list
          ip (extract-first-ip raw-ip)]
      (try
        (let [location-cookie (get-in request [:cookies "location" :value])
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
            (status-500 request)))
        (catch Exception e
          (println "Error processing request:" (.getMessage e))
          {:status 500
           :headers {"Content-Type" "text/plain"}
           :body (str "Error processing request: " (.getMessage e))})))
    (catch Exception e
      (println "Critical error in default-handler:" (.getMessage e))
      {:status 500
       :headers {"Content-Type" "text/plain"}
       :body (str "Critical error processing request: " (.getMessage e))})))

(defn coordinates
  [request]
  (try
    (as-> (get-in request [:params :coordinates]) <>
          (str/split <> #",")
          (map read-string <>))
    (catch Exception e
      (println "Error parsing coordinates:" (.getMessage e))
      [0 0])))

(defn coordinates-handler
  [request]
  (try
    (let [coords (coordinates request)
          lat (first coords)
          lon (last coords)]
      (default-handler request :lat lat :lon lon))
    (catch Exception e
      (println "Error in coordinates-handler:" (.getMessage e))
      {:status 500
       :headers {"Content-Type" "text/plain"}
       :body (str "Error processing coordinates: " (.getMessage e))})))

(defn health-handler [_]
  (let [env (or (System/getenv "APP_ENV") "production")]
    (if (= env "test")
      ;; In test environment, don't verify API keys
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body "OK"}
      ;; In production, verify keys
      (try
        ;; Verify API keys are available without making API calls
        (compute/get-locationiq-api-key)
        (compute/get-ipgeolocation-api-key)
        
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body "OK"}
        (catch Exception e
          (println "Health check failed:" (.getMessage e))
          {:status 500
           :headers {"Content-Type" "text/plain"}
           :body (str "Health check failed: " (.getMessage e))})))))

(core/defroutes app-routes
  (core/GET "/" [] #'default-handler)
  (core/GET ["/:coordinates", :coordinates #"(-?\d+\.?\d*),(-?\d+\.?\d*)"]
            request
            (coordinates-handler request))
  (core/GET "/health" [] health-handler)
  (route/resources "/")
  (route/not-found "Not Found"))

;; Use more memory-efficient middleware settings
(def minimal-defaults
  (-> site-defaults
      ;; Reduce session store size
      (assoc-in [:session :cookie-attrs :max-age] 1800) ;; 30 minutes instead of default
      ;; Disable some middlewares we don't need
      (assoc-in [:security :xss-protection] false)
      (assoc-in [:security :frame-options] false)
      (assoc-in [:security :content-type-options] false)))

(def app
  (wrap-defaults app-routes minimal-defaults))

(defn -main []
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))
        host (or (System/getenv "HOST") "0.0.0.0")]
    (println (str "Starting server on " host ":" port))
    
    ;; Reduce logging to save memory
    (println (str "LOCATIONIQ_API_KEY present: " (if (System/getenv "LOCATIONIQ_API_KEY") "yes" "no")
                   ", IPGEOLOCATION_API_KEY present: " (if (System/getenv "IPGEOLOCATION_API_KEY") "yes" "no")))
    
    ;; Force GC multiple times to ensure startup memory is reclaimed
    (System/gc)
    (Thread/sleep 100) ;; Give GC a moment
    (System/gc)
    
    ;; Jetty configuration optimized for 512MB
    (jetty/run-jetty app {:port port 
                          :host host 
                          :join? false
                          ;; Balanced thread settings for 512MB memory
                          :min-threads 4
                          :max-threads 20
                          ;; Queue incoming requests for traffic spikes
                          :max-queued-requests 100
                          ;; Standard idle timeout
                          :thread-idle-timeout 60000})))
