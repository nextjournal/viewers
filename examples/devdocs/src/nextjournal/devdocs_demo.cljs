(ns nextjournal.devdocs-demo
  (:require [nextjournal.devdocs :as devdocs :refer [devdoc-collection show-card]]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.easy :as rfe]
            [reagent.core :as reagent]
            [reagent.dom :as reagent-dom]))

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
  (rf/router devdocs/default-routes))

(defonce match (reagent/atom nil))

(defn main []
  (let [{:keys [data path-params] :as match} @match]
    [:div.flex.h-screen.bg-white
     [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
      (if-let [view (:view data)]
        [view (merge data path-params)]
        [:pre (pr-str match)])]]))

(defn ^:export ^:dev/after-load init []
  (rfe/start! router #(reset! match %1) {:use-fragment true})
  (rfe/push-state :devdocs/index)
  (reagent-dom/render [main] (js/document.getElementById "app"))
  )
