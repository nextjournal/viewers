(ns nextjournal.devcards.routes
  (:require [nextjournal.devcards-ui :as devcards-ui]
            [reagent.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.easy :as rfe]))

(def use-fragment? (atom true))

(def routes
  [["/devcards" {:name :devcards/root :view devcards-ui/root}]
   ["/devcards/:ns" {:name :devcards/by-namespace :view devcards-ui/by-namespace}]
   ["/devcards/:ns/:name" {:name :devcards/by-name :view devcards-ui/by-name}]])

(defn navigate-to
  "Navigate to the given path, then trigger routing."
  [new-path]
  (.pushState js/window.history nil "" new-path)
  (rfh/-on-navigate @rfe/history new-path))

(def router
  (rf/router routes))

(defonce match (r/atom nil))

(defn ^:export start []
  (rfe/start! router #(reset! match %1) {:use-fragment @use-fragment?}))
