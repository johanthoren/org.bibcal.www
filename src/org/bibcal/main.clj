(ns org.bibcal.main
  (:require [trptcolin.versioneer.core :refer [get-version]]
            [hiccup.page :refer [html5 include-css include-js]]
            [xyz.thoren.luminary :as l]
            [org.bibcal.compute :as compute]
            [org.bibcal.util :refer [accordion-card verbose-date with-href]]
            [tick.core :as tick]))

(def version-number (get-version "org.bibcal" "www"))
(def bibcal-url "https://www.bibcal.org/")
(def github-url "https://github.com/johanthoren/")
(def email "johan@bibcal.org")
(def bs-css "https://cdn.jsdelivr.net/npm/bootstrap-dark-5@1.1.3/dist/css/bootstrap-dark.min.css")
(def bs-jq "https://code.jquery.com/jquery-3.7.0.slim.min.js")
(def bs-popper "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js")
(def bs-js "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js")

(def genesis-quote
  [:p {:class "lead"}
   [:figure
    [:blockquote {:class "blockquote"}
     "And God said, Let there be lights in the firmament of the heaven to divide the day from the night]; and let them be for signs, and for seasons, and for days, and years: And let them be for lights in the firmament of the heaven to give light upon the earth: and it was so."]
    [:figcaption {:class "blockquote-footer"}
     [:cite {:title "Genesis 1:14-15"} "Genesis 1:14-15"]]]])

(def intro
  [:div {:class ""}
   [:p
    "This page displays the current Biblical Calendar data for any given location based on the following:"
    [:ul
     [:li "The day starts at sunset."]
     [:li "The month starts on the first sunset following a lunar conjunction."]
     [:li "The year starts on the first month following the vernal equinox."]
     [:li "The start of a year or a month will always be normalized based on the conditions at the Temple Mount in Jerusalem, Israel."]]
    [:p
     "Based on these premises, we can mathematically calculate the date at any given location. By default, the location will be estimated based on your IP. To get the most accurate time, visit the site with your coordinates following a "
     [:code "/"]
     " in the URL."]
    [:p
     "Example: "
     [:code (str bibcal-url "59.332146,18.0397160")]]]])

(def get-bibcal-notice
  (->
   "For a multi-platform command-line app that doesn't require Internet access, get the free and open-source program bibcal. It has support for several ways of calculating historical and future dates and feast days. This website is only a simplified version of the app."
   (with-href "bibcal" (str github-url "bibcal"))))

(def get-luminary-notice
  (->
   "If you would like to build your own app to calculate biblical dates, take a look at the free and open source library Luminary, which is written in Clojure."
   (with-href "Luminary" (str github-url "luminary"))
   (with-href "Clojure" "https://clojure.org")))

(def get-in-touch-notice
  (->
   "If you have been blessed by this project, send me an email and let me know."
   (with-href "send me an email" (str "mailto:" email))))

(def feast-explanation
  "The dates represent the gregorian dates in which the sunset will mark the beginning of the feast day.")

(def cookie-notice
  "A small cookie has been placed on your device with information about your location necessary to perform the calculations for this site. Apart from your location and IP it contains no personal information and it is not shared with any third party. The cookie will expire after 24 hours.")

(def version-notice
  (->
    (str "Site version: " version-number)
    (with-href version-number (str github-url "org.bibcal.www/releases/latest"))))

(def copyright-notice
  (->
    "© 2021-2024 Copyright: Johan Thorén"
    (with-href "Johan Thorén" (str "mailto:" email))))

(defn cards
  [location t d]
  (let [year (tick/int (tick/year t))]
    [{:body (compute/current-time t d)
      :id "current"
      :expanded true
      :title "Current Date"}
     {:body (compute/current-time-details location d)
      :id "details"
      :expanded false
      :title "Details"}
     {:body [:div (compute/feast-days-in-current-year location t)
                  feast-explanation]
      :id "feast-current"
      :title (str "Feast days " year)}
     {:body [:div (compute/feast-days-in-next-year location t)
                  feast-explanation]
      :id "feast-next"
      :title (str "Feast days " (inc year))}]))

(defn time-accordion
  [location t d]
  [:div {:id "accordion"} (map accordion-card (cards location t d))])

(defn page
  [location]
  (let [{:keys [lat lon timezone]} location
        t (l/in-zone timezone (l/now))
        d (l/date lat lon t)
        verbose-d (verbose-date d)]
    (html5
     [:head
      (include-css bs-css)
      (include-js bs-jq)
      (include-js bs-popper)
      (include-js bs-js)
      (include-css "style.css")
      [:meta {:http-equiv "refresh" :content "300"}]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title verbose-d]
      [:link {:rel "shortcut icon" :type "image/svg" :href "logo.svg"}]
      [:body
       [:div {:class "container-xl d-sm-grid gap-4 p-4"}
        [:div {:class "row border-bottom"}
         [:h1 {:class "5display-1"} "BibCal"
          [:div {:class "row"}]
          [:h4 {:class "text-primary"} verbose-d]]]
        [:div {:class "row border-bottom"}
         [:div {:class "col-xl-6"}
          [:div {:class "border-bottom py-3"} genesis-quote]
          [:div
           {:class "py-3"}
           intro
           get-bibcal-notice
           get-luminary-notice
           get-in-touch-notice]]
         [:div {:class "col-xl-6"}
          [:div {:class "py-3"} (time-accordion location t d)]]]
        [:div {:class "row py-2"}
         [:div [:small {:class "text-muted"} cookie-notice]]]
        [:footer {:class "align-items-center text-center border-top py-4"}
         [:div {:class "row"}
          [:span {:class "text-muted"} version-notice]]
         [:div {:class "row"}
          [:span {:class "text-muted"} copyright-notice]]]]]])))
