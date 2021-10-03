(ns org.bibcal.www-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [org.bibcal.www :as www]))

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
