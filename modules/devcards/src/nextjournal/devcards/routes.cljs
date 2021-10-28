(ns nextjournal.devcards.routes
  (:require [nextjournal.devcards-ui :as devcards-ui]
            [re-frame.context :as re-frame]
            [reagent.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reitit.frontend.easy :as rfe]
            [reagent.dom :as rdom]
            [nextjournal.devcards :as dc]
            [nextjournal.commands.core :as commands]))

;;todo rename to router

(re-frame/reg-event-fx
  :router/push
  (fn [_ctx [_ routing-data]]
    {:push-history routing-data}))

(re-frame/reg-fx
  :push-history
  (fn [args] (apply rfe/push-state args)))

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

(defn view [match]
  (if-let [{:keys [data path-params]} match]
    [devcards-ui/layout (merge data path-params)]
    [:pre "no match!"]))

(defn devcards [] (view @match))

(re-frame/reg-event-fx
  :perform-fx
  (fn [{:keys [_db]} [_ fx]]
    fx))

(defn mount-app []
  (when-let [app-el (js/document.getElementById "app")]
    (rdom/unmount-component-at-node app-el)
    (rdom/render [devcards] app-el)))

(re-frame/reg-fx
  :mount-app
  (fn [_]
    (mount-app)))

(dc/when-enabled
  (commands/register! :dev/remount-app
                      {:title  "Re-mount App"
                       :keys   "Ctrl-R"
                       :action mount-app}))


