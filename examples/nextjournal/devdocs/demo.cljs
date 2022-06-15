(ns nextjournal.devdocs.demo
  (:require [nextjournal.devdocs :as devdocs]
            [nextjournal.commands.core :as commands]
            [reitit.frontend :as rf])
  (:require-macros [nextjournal.devdocs :as devdocs]))

(reset! devdocs/registry (assoc (devdocs/build-registry {:paths ["docs/**.{clj,md}"]})
                                :navbar/theme
                                {:back "text-[12px] text-slate-300 hover:bg-white/10 font-normal px-5 py-1"
                                 :icon "text-slate-400"}))

(defonce router (rf/router ["/docs" ["/*path" {:name :devdocs/show}]]))

(defn view [match]
  [:div.flex.h-screen.bg-white
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
    [devdocs/view (devdocs/view-data match)]]])


(commands/register! :dev/docs (devdocs/devdoc-commands))
