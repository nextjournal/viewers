(ns nextjournal.devcards.routes
  (:require [nextjournal.devcards-ui :as devcards-ui]
            [re-frame.context :as re-frame]
            [reagent.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.easy :as rfe]))
;;todo rename to router
(re-frame/reg-event-fx
  :router/push
  (fn [_ctx [_ routing-data]]
    {:push-history routing-data}))

(re-frame/reg-fx :push-history (fn [args] (apply rfe/push-state args)))


(def use-fragment? (atom true))

(def routes
  [["/" {:name :devcards/root :view devcards-ui/root}]
   ["/:ns" {:name :devcards/by-namespace :view devcards-ui/by-namespace}]
   ["/:ns/:name" {:name :devcards/by-name :view devcards-ui/by-name}]])

(defn navigate-to
  "Navigate to the given path, then trigger routing."
  [new-path]
  (.pushState js/window.history nil "" new-path)
  (rfh/-on-navigate @rfe/history new-path))

(def router
  (rf/router routes))

(defonce match (r/atom nil))

(defn devcards []
  (if-let [{:keys [data path-params]} @match]
    [devcards-ui/layout (merge data path-params)]
    [:pre "no match!"]))
