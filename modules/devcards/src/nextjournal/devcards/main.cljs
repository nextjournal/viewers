(ns nextjournal.devcards.main
  (:require [nextjournal.devcards.routes :as router]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn ^:export ^:dev/after-load init []
  (rfe/start! router/router #(reset! router/match %1) {:use-fragment @router/use-fragment?})
  (r/render [router/devcards] (js/document.getElementById "app")))
