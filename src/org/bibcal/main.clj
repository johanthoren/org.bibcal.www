(ns org.bibcal.main
  (:require [trptcolin.versioneer.core :refer [get-version]]
            [hiccup.page :refer [html5 include-css include-js]]
            [xyz.thoren.luminary :as l]
            [org.bibcal.compute :as compute]))

(def version-number (get-version "org.bibcal" "www"))

(defn verbose-date [m]
  (let [names (get-in m [:hebrew :names])
        dow (:day-of-week names)
        dom (:day-of-month names)
        moy (:month-of-year names)]
    (str "It's the " dow ", on the " dom " day of the " moy " month.")))

(def genesis-quote
  [:p {:class "lead"}
   [:figure
    [:blockquote {:class "blockquote"}
     [:p (str "And God said, Let there be lights in the firmament of the "
              "heaven to divide the day from the night; and let them be for "
              "signs, and for seasons, and for days, and years: And let them "
              "be for lights in the firmament of the heaven to give light "
              "upon the earth: and it was so. ")]]
    [:figcaption {:class "blockquote-footer"}
     [:cite {:title "Genesis 1:14-15"} "Genesis 1:14-15"]]]])

(def intro
  [:div {:class ""}
   [:p
    (str "This page displays the current Biblical Calendar data for any given "
         "location based on the following:")
    [:ul
     [:li "The day starts at sunset."]
     [:li "The month starts on the first sunset following a lunar conjunction."]
     [:li "The year starts on the first month following the vernal equinox."]
     [:li (str "The start of a year or a month will always be normalized based "
               "on the conditions at the Temple Mount in Jerusalem, Israel.")]]
    [:p
     (str "Based on these premises, we can mathematically calculate the date "
          "at any given location. By default, the location will be estimated "
          "based on your IP. To get the most accurate time, visit the site "
          "with your coordinates following a ")
     [:code "/"]
     " in the URL."]
    [:p
     "Example: "
     [:code "https://www.bibcal.org/59.332146,18.0397160"]]]])

(def get-bibcal-notice
  [:p
   (str "For a multi-platform command-line app that doesn't require "
        "Internet access, get the free and open-source program  ")
   [:a {:href "https://github.com/johanthoren/bibcal"
        :target "_blank"}
       "bibcal"]
   (str ". It has support for several ways of calculating historical and "
        "future dates and feast days. This website is only a simplified "
        "version of the app.")])

(def get-luminary-notice
  [:p
   (str "If you would like to build your own app to calculate biblical "
        "dates, take a look at the free and open source library ")
   [:a {:href "https://github.com/johanthoren/luminary"
        :target "_blank"}
       "Luminary"]
   ". It is built using "
   [:a {:href "https://clojure.org"
        :target "_blank"}
    "Clojure"]
   " and can be used by languages running on the "
   [:a {:href "https://en.wikipedia.org/wiki/Java_virtual_machine"
        :target "_blank"}
    "JVM platform"]
   ", such as Clojure, "
   [:a {:href "https://www.java.com/"
        :target "_blank"}
    "Java"]
   ", or "
   [:a {:href "https://kotlinlang.org/"
        :target "_blank"}
    "Kotlin"]
   "."])

(def feast-intro
  (str "Below follows the feast days for the current and the next gregorian "
       "years. The dates represent the gregorian dates in which the sunset "
       "will mark the beginning of the feast day."))

(def cookie-notice
 (str "A small cookie has been placed on your device with information "
      "about your location necessary to perform the calculations for this "
      "site. Apart from your location and IP it contains no personal "
      "information and it is not shared with any third party. The cookie "
      "will expire after 24 hours."))

(def version-notice
  [:p "Site version: "
   [:a {:href "https://github.com/johanthoren/org.bibcal.www/releases/latest/"
        :target "_blank"}
       version-number]])

(defn page
  [location]
  (let [{:keys [lat lon timezone]} location
        t (l/in-zone timezone (l/now))
        d (l/date lat lon t)
        current-time (compute/current-time location t d)
        feast-days-current (compute/feast-days-in-current-year location)
        feast-days-next (compute/feast-days-in-next-year location)]
    (html5
     [:head
      (include-css (str "https://cdn.jsdelivr.net/npm/bootstrap"
                        "@5.1.0/dist/css/bootstrap.min.css"))
      (include-js (str "https://cdn.jsdelivr.net/npm/bootstrap"
                       "@5.1.0/dist/js/bootstrap.bundle.min.js"))
      (include-css "style.css")
      [:meta {:http-equiv "refresh" :content "300"}]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:title (verbose-date d)]
      [:body
       [:div {:class "container-xl d-sm-grid gap-4 p-4"}
        [:div {:class "row border-bottom"}
         [:h1 {:class "5display-1"} "BibCal"
          [:div {:class "row"}]
          [:h4 {:class "text-primary"} (verbose-date d)]]]
        [:div {:class "row border-bottom"}
         [:div {:class "col-xl-6"}
          [:div {:class "border-bottom py-3"} genesis-quote]
          [:div {:class "py-3"} intro get-bibcal-notice get-luminary-notice]]
         [:div {:class "col-xl-6"} current-time]]
        [:div {:class "row py-3"}
         [:div feast-intro]]
        [:div {:class "row py-1"}
         [:div {:class "col-md-6"} feast-days-current]
         [:div {:class "col-md-6"} feast-days-next]]
        [:div {:class "row py-2"}
         [:div [:small {:class "text-muted"} cookie-notice]]]
        [:footer {:class "align-items-center text-center border-top py-4"}
         [:div {:class "row"}
          [:span {:class "text-muted"} version-notice]]
         [:div {:class "row"}
          [:span {:class "text-muted"} "© 2021 Copyright: "
           [:a {:href "mailto:johan@thoren.xyz"}
            "Johan Thorén"]]]]]]])))
