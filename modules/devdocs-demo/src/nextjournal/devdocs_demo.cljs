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
 [{:path "simple.md"}])

(defonce router
  (rf/router devdocs/default-routes))

(defonce match (reagent/atom nil))

(defn main []
  (let [{:keys [data path-params] :as match} @match]
    (prn match)
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
