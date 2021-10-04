(ns org.bibcal.util
  (:require [clojure.string :as str]))

(defn accordion-card
  "Plumbing to create a card for an accordion layout."
  [m]
  (let [{:keys [body id expanded title]} m]
    [:div {:class "card"}
     [:div {:class "card-header" :id (str "heading-" id)}
      [:h5 {:class "mb-0"}
       [:button {:class (str "btn btn-link" (when-not expanded " collapsed"))
                 :data-toggle "collapse"
                 :data-target (str "#collapse-" id)
                 :aria-expanded (if expanded "true" "false")
                 :aria-controls (str "collapse-" id)}
                title]]]
     [:div {:id (str "collapse-" id)
            :class (str "collapse" (when expanded " show"))
            :aria-labelledby (str "heading-" id)
            :data-parent "#accordion"}
      [:div {:class "card-body"} body]]]))

(defn verbose-date [m]
  (let [names (get-in m [:hebrew :names])
        dow (:day-of-week names)
        dom (:day-of-month names)
        moy (:month-of-year names)]
    (str "It's the " dow ", on the " dom " day of the " moy " month.")))

(defn with-href
  "Substitute a `sub-string` with a hiccup :a element using `url` for href.
  `x` can be either a string or a vector. Will only substitute the first
  occurance of `sub-string`. When working on a vector, `sub-string` is looked
  for in the last item which must therefore be a string."
  [x sub-string url & {:keys [target element]
                       :or {target "_blank", element :p}}]
  {:pre [(or (string? x) (coll? x))]}
  (let [m {:href url :target target}
        href (if-not (str/starts-with? url "http")
               (dissoc m :target)
               m)
        a [:a href sub-string]]
    (if (string? x)
      (->> (str/split x (re-pattern sub-string) 2)
           (interpose a)
           (remove #(= "" %))
           (cons element)
           vec)
      (->> (with-href (last x) sub-string url)
           rest
           (concat (pop x))
           vec))))
