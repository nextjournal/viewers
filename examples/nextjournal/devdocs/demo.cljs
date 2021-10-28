(ns nextjournal.devdocs.demo
  (:require [nextjournal.devdocs :as devdocs :refer [devdoc-collection show-card]]
            [nextjournal.commands.core :as commands]
            [nextjournal.devdocs.routes :as routes]
            [reitit.frontend :as rf]))

(devdoc-collection
 "Simple"
 {:slug "simple" :cljs-eval? false :view-source? true}
 [{:path "docs/simple.md"}
  {:path "docs/reference.md"}])

(devdoc-collection
 "Frontend"
 {:slug "frontend" :cljs-eval? true :view-source? true}
 [{:path "docs/frontend.md"}])

(devdoc-collection
 "Clerk"
 {:slug "clerk" :clerk? true :view-source? true :resource? false}
 [{:path "resources/docs/clerk.clj"}])

(defonce router
  (rf/router routes/routes))

(defn view [{:keys [data] :as match}]
  [:div.flex.h-screen.bg-white
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
    (if-let [view (get routes/views (:view data))]
      [view (devdocs/view-data match)]
      [:pre (pr-str match)])]])

(commands/register! :dev/docs (devdocs/devdoc-commands))
