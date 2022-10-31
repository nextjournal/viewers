(ns nextjournal.devcards.main
  (:require [nextjournal.devcards.routes :as router]
            [reagent.core :as reagent]
            ["react-dom/client" :as react-client]
            [nextjournal.commands.state :as commands.state]
            [re-frame.context :as rf]
            [reitit.frontend.easy :as rfe]))

(rf/reg-event-fx
 :init-commands
 (fn [{:keys [db]} [_ commands-ctx]]
   {:db (merge db commands-ctx)}))

(defonce react-root
  (when-let [el (and (exists? js/document) (js/document.getElementById "app"))]
    (react-client/createRoot el)))

(defn ^:export ^:dev/after-load init []
  (rf/dispatch-sync [:init-commands (commands.state/empty-db!)])
  (rfe/start! router/router #(reset! router/match %1) {:use-fragment @router/use-fragment?})
  (when react-root
    (.render react-root (reagent/as-element [router/devcards]))))
