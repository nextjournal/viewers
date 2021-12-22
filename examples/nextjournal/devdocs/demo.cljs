(ns nextjournal.devdocs.demo
  (:require [nextjournal.devdocs :as devdocs]
            [nextjournal.commands.core :as commands]
            [nextjournal.devdocs.routes :as routes]
            [reitit.frontend :as rf]))

(devdocs/collection "Simple"
                    [{:path "docs/simple.md"}
                     {:path "docs/reference.md"}])

(devdocs/collection "Frontend"
                    [{:path "docs/frontend.md"}])

(devdocs/collection "Clerk"
                    [{:path "docs/clerk.clj"}])

(defonce router
  (rf/router routes/routes))

(defn view [{:keys [data] :as match}]
  [:div.flex.h-screen.bg-white
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
    (if-let [view (get routes/views (:view data))]
      [view (devdocs/view-data match)]
      [:pre (pr-str match)])]])

(commands/register! :dev/docs (devdocs/devdoc-commands))
