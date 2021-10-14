(ns nextjournal.devdocs.demo
  (:require [nextjournal.devdocs :as devdocs :refer [devdoc-collection show-card]]
            [nextjournal.devdocs.routes :as routes]
            [reitit.frontend :as rf]))

(devdoc-collection
 "Simple"
 {:slug "simple" :cljs-eval? false :view-source? true}
 [{:path "simple.md"}
  {:path "reference.md"}])

(devdoc-collection
 "Frontend"
 {:slug "frontend" :cljs-eval? true :view-source? true}
 [{:path "frontend.md"}])

(devdoc-collection
 "Clerk"
 {:slug "clerk" :clerk? true :view-source? true :resource? false}
 [{:path "docs/clerk.clj"}])

(defonce router
  (rf/router routes/routes))

(defn view [{:keys [data] :as match}]
  [:div.flex.h-screen.bg-white
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
    (if-let [view (get routes/views (:view data))]
      [view (devdocs/view-data match)]
      [:pre (pr-str match)])]])
