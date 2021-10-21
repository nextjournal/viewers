(ns nextjournal.devcards.main
  (:require [nextjournal.devcards.routes :as router]
            [reagent.dom :as rdom]
            [nextjournal.commands.state :as commands.state]
            [re-frame.context :as rf]
            [reitit.frontend.easy :as rfe]))

(rf/reg-event-fx
  :init-commands
  (fn [{:keys [db]} [_ commands-ctx]]
    {:db (merge db commands-ctx)}))

(defn ^:export ^:dev/after-load init []
  (rf/dispatch-sync [:init-commands (commands.state/empty-db!)])
  (rfe/start! router/router #(reset! router/match %1) {:use-fragment @router/use-fragment?})
  (rdom/render [router/devcards] (js/document.getElementById "app")))
