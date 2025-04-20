(ns org.bibcal.www-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [org.bibcal.www :as www]
            [org.bibcal.compute :as compute]))

;; Mock API responses based on actual API returns
(def mock-locationiq-response
  {:place_id "141454544"
   :licence "https://locationiq.com/attribution"
   :osm_type "way"
   :osm_id "31243005"
   :lat "54.43213543188384"
   :lon "13.432739232460584"
   :display_name "Jägersruh, Bergen auf Rügen, Vorpommern-Rügen, Mecklenburg-Vorpommern, 18528, Germany"
   :address {:name "Jägersruh"
             :city "Bergen auf Rügen"
             :county "Vorpommern-Rügen"
             :state "Mecklenburg-Vorpommern"
             :postcode "18528"
             :country "Germany"
             :country_code "de"}
   :boundingbox ["54.4261255" "54.4328195" "13.4325275" "13.4329102"]})

;; Mock for IP API response
(def mock-ip-api-response
  {:status "success"
   :country "Germany"
   :countryCode "DE"
   :region "MV"
   :regionName "Mecklenburg-Vorpommern"
   :city "Bergen auf Rügen"
   :zip "18528"
   :lat 54.432145
   :lon 13.432554
   :timezone "Europe/Berlin"
   :query "127.0.0.1"})

;; Mock for timezone response
(def mock-timezone-response
  {:timezone {:name "Europe/Berlin"}})

;; Test fixture to replace API calls with mocks
(defn with-api-mocks [f]
  (with-redefs [compute/call-api (fn [url]
                                   (cond
                                     (.contains url "ip-api.com") mock-ip-api-response
                                     (.contains url "locationiq.com/v1/reverse.php") mock-locationiq-response
                                     (.contains url "locationiq.com/v1/timezone.php") mock-timezone-response
                                     (.contains url "ipgeolocation.io/timezone") {:timezone "Europe/Berlin"}
                                     :else {}))
                compute/get-locationiq-api-key (fn [] "mock-api-key")
                compute/get-ipgeolocation-api-key (fn [] "mock-api-key")]
    (f)))

(use-fixtures :each with-api-mocks)

(deftest test-app
  (testing "main route"
    (let [response (www/app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (re-find #"Current local time" (:body response)))
      (is (re-find #"Start of next day" (:body response)))))
  (testing "coordinates route"
    (let [response (www/app (mock/request :get "/54.432145,13.432554"))]
      (is (= (:status response) 200))
      (is (re-find #"Current local time" (:body response)))
      (is (re-find #"Start of next day" (:body response)))
      (is (re-find #"54.432145,13.432554" (:body response)))
      (is (re-find #"Jägersruh" (:body response)))
      (is (re-find #"Vorpommern-Rügen" (:body response)))
      (is (re-find #"Berlin" (:body response)))
      (is (re-find #"Europe/Berlin" (:body response)))))
  (testing "coordinates with space instead of comma"
    (let [response (www/app (mock/request :get "/54.432145%2013.432554"))]
      (is (= (:status response) 404))))
  (testing "not-found route"
    (let [response (www/app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
