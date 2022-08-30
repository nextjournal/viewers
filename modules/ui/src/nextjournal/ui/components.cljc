(ns nextjournal.ui.components
  (:require [nextjournal.devcards :as dc]
            #?@(:cljs [[nextjournal.commands.core :as keybind]
                       [nextjournal.commands.state :as commands.state]
                       [nextjournal.ui.components.navbar]])
            [nextjournal.ui.components.icon :as icon]
            [clojure.string :as str]))

(def theme
  {:code-cell {:background "#eff1f5"}
   :bar {:background "var(--dark-blue-color)"
         :background-selected "#181F28"
         :height "28px"}})

(def text-colors
  {:error "text-red-300"
   :success "text-green-300"})

(defn bar [& content]
  (let [options (first content)
        options? (map? options)]
    (into [:div.flex.items-center.justify-between.text-white.monospace
           {:style (cond-> {:background (get-in theme [:bar :background])
                            :height (get-in theme [:bar :height])
                            :font-size "12px"
                            :padding-left "10px" :padding-right "10px"}
                     options? (merge (:style options)))}]
          (if options? (rest content) content))))

(defn disclose-button [& content]
  (conj
   (into [:button.inline-flex.items-center] content)
   [icon/view "ChevronDown" {:size 12 :class "fill-current opacity-75"}]))

(dc/defcard disclose-button
  "Renders a text button showing a chevron next to it. Inherits colors, text styles; adjusts chevron color automatically."
  [:div.inter.text-xs.flex.items-center
   [:div.mr-4
    [disclose-button "There’s more to show"]]
   [bar [disclose-button "There’s more to show"]]])

#?(:cljs
   (defn command-button
     ([{:keys [class disabled? label show-binding? style]} {:as command :keys [title keys/printable]}]
      [:button.cursor-pointer
       {:class class
        :style style
        :disabled disabled?
        :on-click (when-not disabled? #(keybind/eval-command command))}
       (when (and show-binding? (not (str/blank? printable)))
         [:span.opacity-70.inter.mr-1 printable])
       (or label title)])
     ([command]
      [command-button {:show-binding? true} command])))

(dc/defcard command-button
  "Takes a command and renders a text button. If the command has a keybinding, it will prepend the binding.
   Renders a text button showing a chevron next to it. Inherits colors, text styles; adjusts chevron color automatically."
  [:div.inter.text-xs.flex.items-center
   [:div.mr-4
    [command-button (keybind/get-command :run/run)]]
   [bar [command-button (keybind/get-command :run/run)]]]
  (-> (commands.state/empty-db!)
      (keybind/register {:run/run {:action identity}})))

(def status-colors
  {:green "green-500"
   :red "red-500"
   :yellow "yellow-400"
   :gray "gray-400"
   :white "white"})

(defn status-light [{:keys [color size bright?] :or {color :gray size 8}}]
  [:div.rounded-full.border.flex-shrink-0
   {:class (str "bg-" (color status-colors))
    :style {:width size :height size
            :border (str "1px solid " (if bright? "rgba(53, 65, 82, 0.7)" "rgba(255, 255, 255, .85)"))}}])

(dc/defcard status-light
  (into [:div.flex.items-center]
        (map (fn [color]
               [:div
                [:div.mr-4.flex.flex-col.items-center
                 [:div.flex.items-center
                  [:div.flex.items-center.justify-center.bg-dark-blue
                   {:style {:width 20 :height 20}}
                   [status-light {:color color}]]
                  [:div.flex.items-center.justify-center
                   {:style {:background-color "#f2f4f5" :width 20 :height 20}}
                   [status-light {:color color :bright? true}]]]
                 [:div.text-xs.monospace.mt-1 (str color)]]])
             [:green :red :yellow :gray])))

(defn circle-spinner [{:keys [color size] :or {color :gray size 8}}]
  [:svg.animate-spin
   {:class (str "text-" (color status-colors))
    :style {:width size :height size}
    :viewBox "0 0 24 24"
    :fill "none"}
   [:circle.opacity-25
    {:stroke-width "4"
     :stroke "currentColor"
     :r "10"
     :cy "12"
     :cx "12"}]
   [:path.opacity-75
    {:d
     "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
     :fill "currentColor"}]])

(dc/defcard circle-spinner
  (into [:div.flex.items-center]
        (map (fn [color]
               [:div
                [:div.mr-4.flex.flex-col.items-center
                 [:div.flex.items-center
                  [:div.flex.items-center.justify-center.bg-dark-blue
                   {:style {:width 20 :height 20}}
                   [circle-spinner {:color color}]]
                  [:div.flex.items-center.justify-center
                   {:style {:background-color "#f2f4f5" :width 20 :height 20}}
                   [circle-spinner {:color color :bright? true}]]]
                 [:div.text-xs.monospace.mt-1 (str color)]]])
             [:green :red :yellow :gray])))

(defn panel-arrow [arrow]
  [:div.panel-arrow {:class arrow}])

(defn panel-base [& content]
  (conj
   (into [:div.panel-base] content)
   (when-let [{:keys [arrow]} (first content)]
     [panel-arrow arrow])))

(defn panel [& content]
  (let [options (first content)
        options? (map? options)
        els (if options? (rest content) content)]
    (if options?
      [panel-base options (into [:div.panel] els)]
      [panel-base els])))

(dc/defcard panel
  [panel
   {:style {:max-width 224}}
   [:span.text-xs "Auxiliary utilitarian"]])

(dc/defcard panel-with-arrow
  "There’s an `:arrow` option that you can use to make the panel point to something."
  (into [:div.flex]
        (map (fn [arrow]
               [panel
                {:arrow arrow :class "mr-4" :style {:max-width 160}}
                [:span.text-xs.monospace (str arrow)]])
             [:top-left :top-right :top-center :bottom-left :bottom-right :bottom-center])))

#_(defn with-popup
  ([actuator content]
   [with-popup {} actuator content])
  ([opts actuator content]
   [:span.relative
    actuator
    (into [:span.absolute] content)]))
