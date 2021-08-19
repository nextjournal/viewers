(ns nextjournal.devcards.routes
  (:require [nextjournal.devcards-ui :as devcards-ui]
            [reagent.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.easy :as rfe]))
;;todo rename to router

(def use-fragment? (atom true))

(def routes
  [["/" {:name :devcards/root }]
   ["/:ns" {:name :devcards/by-namespace}]
   ["/:ns/:name" {:name :devcards/by-name}]])

(defn navigate-to
  "Navigate to the given path, then trigger routing."
  [new-path]
  (.pushState js/window.history nil "" new-path)
  (rfh/-on-navigate @rfe/history new-path))

(def router
  (rf/router routes))

(defonce match (r/atom nil))

(defn match->props [m]
  (let [{:keys [data]} m
        {:keys [name]} data]
    (merge
     (:path-params m)
     {:route-name name})))

(defn devcards []
  (if-let [{:keys [data]} @match]
    [devcards-ui/view (match->props @match)]
    [:pre "no match!"]))

(defn ^:export ^:dev/after-load start []
  (rfe/start! router #(reset! match %1) {:use-fragment @use-fragment?})
  (r/render [devcards] (js/document.getElementById "app")))
