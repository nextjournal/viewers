(ns nextjournal.devcards.main
  (:require [nextjournal.devcards.routes :as router]
            [reagent.dom :as rdom]
            [reitit.frontend.easy :as rfe]))

(defn ^:export ^:dev/after-load init []
  (rfe/start! router/router #(reset! router/match %1) {:use-fragment @router/use-fragment?})
  (rdom/render [router/devcards] (js/document.getElementById "app")))
