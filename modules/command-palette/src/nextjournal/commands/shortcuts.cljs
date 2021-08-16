(ns nextjournal.commands.shortcuts
  "Namespace for registering groups of commands that update immediately based on context
   (eg. for displaying toolbars)"
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.commands.core :as commands]
            [nextjournal.ui.components :as ui]
            [re-frame.context :as re-frame]))

(defn view [shortcuts]
  (let [context @(re-frame/subscribe [:commands/reactive-context])
        desktop? @(re-frame/subscribe [:desktop?])]
     (->> shortcuts
         (into [:div.flex.items-center.flex-auto {:class (if-not desktop? "justify-start text-sm" "justify-end")}]
               (comp (mapcat (fn [[_ {:keys [commands requires]}]]
                               (when (commands/requirements-satisfied? context requires)
                                 (map (partial commands/apply-context context) commands))))
                     (map (fn [{:as command :keys [active?
                                                   context
                                                   title
                                                   shortcut/highlight?]}]
                            (let [highlighted? (and active? highlight? (highlight? context))]
                              [:div.flex.items-center.ml-2.pl-2.whitespace-no-wrap.truncate.flex-shrink-0
                               {:class (if active? "cursor-pointer " "opacity-50 ")
                                :on-mouse-down #(j/call % :preventDefault) ;; prevent blur, to keep valid context
                                :on-click #(commands/eval-command command context)}
                               [:div.border-b-2.border-transparent.hover:border-gray-300.flex.items-center
                                {:style (cond-> {:height (get-in ui/theme [:bar :height])}
                                          highlighted? (assoc :color "var(--teal-color)"
                                                              :border-color "var(--teal-color)"))}
                                [:div.flex.items-center
                                 (when (and (not (str/blank? (:keys/printable command)))
                                            desktop?)
                                   [:div.mr-1.font-normal.inter
                                    {:style (cond-> {:color "rgba(255,255,255,.7)"}
                                                     highlighted? (assoc :color "var(--teal-color)"))}
                                    (:keys/printable command)])
                                 (if (fn? title)
                                   (title context)
                                   title)]]]))))))))