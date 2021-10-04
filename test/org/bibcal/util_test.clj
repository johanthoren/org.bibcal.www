(ns org.bibcal.util-test
  (:require [org.bibcal.util :refer [with-href]]
            [clojure.test :refer [deftest is testing]]))

(def get-luminary-notice-test-string
   "If you would like to build your own app to calculate biblical dates, take a look at the free and open source library Luminary, which is written in Clojure.")

(def get-luminary-notice-test-element
  [:p
   (str "If you would like to build your own app to calculate biblical "
        "dates, take a look at the free and open source library ")
   [:a {:href "https://github.com/johanthoren/luminary"
        :target "_blank"}
       "Luminary"]
   ", which is written in "
   [:a {:href "https://clojure.org"
        :target "_blank"}
    "Clojure"]
   "."])

(deftest test-with-href
  (testing "that the correct collection is produced"
    (is (= [:p "bar " [:a {:href "https://foo.bar" :target "_blank"} "foo"] " bar foo"]
           (with-href "bar foo bar foo" "foo" "https://foo.bar")))
    (is (= [:p "bar " [:a {:href "https://foo.bar" :target "_blank"} "foo"] " bar " [:a {:href "https://foo.bar" :target "_blank"} "foo"]]
           (with-href [:p "bar " [:a {:href "https://foo.bar" :target "_blank"} "foo"] " bar foo"] "foo" "https://foo.bar")))
    (is (= get-luminary-notice-test-element
           (-> get-luminary-notice-test-string
               (with-href "Luminary" "https://github.com/johanthoren/luminary")
               (with-href "Clojure" "https://clojure.org"))))))
