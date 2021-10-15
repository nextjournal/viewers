(ns nextjournal.commands.context-menu
  (:require ["react" :as react]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [clojure.set :as set]
            [goog.events :as events]
            [nextjournal.commands.state :as commands.state]
            [nextjournal.devcards :as dc]
            [nextjournal.ui.components.positioning :as positioning]
            [nextjournal.ui.dom :as ui.dom]
            [nextjournal.view :as v]
            [nextjournal.view.context :as view.context]
            [reagent.core :as r]
            [nextjournal.commands.core :as commands]
            [re-frame.context :as re-frame]
            [nextjournal.commands.state :as state]))

;; config
(goog-define GRAALJS false)

(def ssr? (or GRAALJS
              (and (exists? js/process)
                   (= "node" (some-> js/process .-release .-name)))))

(defonce menu-stack (react/createContext (list)))
(def z-base 1005)
;; temporary
(defn command->item [{:as command :keys [subcommands]}]
  (if (= command :separator)
    commands/separator
    (cond-> (set/rename-keys command {:action :value
                                      :title :label})
            subcommands
            (assoc :choices
                   (->> command
                        (commands/resolve-subcommands (commands.state/current-context))
                        :commands
                        (map command->item))))))

(defn item->command [command]
  (if (= command :separator)
    commands/separator
    (set/rename-keys command {:value :action
                              :label :title})))

(defn hide-menu! [!state]
  (swap! !state dissoc :visible-at :context-menu/event))

(defn show-menu! [!state event]
  (j/let [^:js {:keys [clientX clientY shiftKey]} event]
    (when-not shiftKey
      (j/call event :persist)
      (swap! !state assoc :visible-at {:left clientX :top clientY} :context-menu/event event))))

(defn on-context-menu [!state event]
  (when-not (.-shiftKey event)
    (.preventDefault event))
  (.stopPropagation event)
  (when-not (:visible-at @!state)
    (show-menu! !state event)))

(defn on-mouse-down [!state event]
  (j/let [^:js {:keys [button target]} event
          {:keys [visible-at]} @!state]
    (if (and (= button 2) (not (j/get event :shiftKey)))
      (do
        (.stopPropagation ^js event)
        (show-menu! !state event))
      (when (and visible-at (not (ui.dom/closest target ".nj-context-menu")))
        (hide-menu! !state)))))

(defonce menu-chevron
  [:svg {:width 16 :height 16 :viewBox "0 0 16 16" :fill "none" :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M10.9998 7.99976L4.99982 3.99976V11.9998L10.9998 7.99976Z"
           :fill "currentColor"}]])

(declare menu)

(v/defview menu-item [{::v/keys [state] :keys [choices disabled? hide! label value depth]}]
  (r/with-let [ref-fn #(when % (swap! state assoc :el %))]
    (let [{:keys [el sub-menu?]} @state
          hide #(swap! state dissoc :sub-menu?)
          !timer (atom nil)]
      [:div.flex.items-center.text-white.nj-context-menu-item.justify-between
       (cond-> {:class (when disabled? "opacity-50 cursor-normal")
                :style {:padding "5px 14px" :font-size 12}
                :ref ref-fn}
               disabled? (assoc :disabled "disabled")
               sub-menu? (assoc-in [:style :background] "rgba(255,255,255,.07)")
               (not (seq choices)) (assoc :on-click #(when (and (fn? value) (not disabled?))
                                                       (hide!)
                                                       (value)))
               (seq choices) (assoc :on-mouse-enter (fn [_]
                                                      (js/clearTimeout @!timer)
                                                      (swap! state assoc :sub-menu? true))
                                    :on-mouse-leave (fn [_]
                                                      (reset! !timer (js/setTimeout hide 200)))))
       label
       (when choices
         [:div.relative {:style {:right -4}} menu-chevron])
       (when sub-menu?
         [menu {:items choices
                :depth (inc depth)
                :el-props {:on-mouse-enter #(js/clearTimeout @!timer)
                           :on-mouse-leave #(hide)}
                :hide! hide!
                :starting-from (let [item-rect (.getBoundingClientRect el)]
                                 {:left (.-left item-rect)
                                  :top (- (.-top item-rect) 8)
                                  :width (.-width item-rect)})}])])))

(def panel-styles
  {:max-width 350
   :padding "8px 0"
   :background "#1f2937"
   :border-radius 2
   :box-shadow "0 2px 7px rgba(0,0,0,.15), 0 5px 17px rgba(0,0,0,.2)"
   :overflow-y "auto"})

(defn position [el {:keys [left top width]}]
  (j/let [^:js {:keys [innerWidth innerHeight]} js/window
          menu-height (.-scrollHeight el)
          menu-width (.-offsetWidth el)
          padding 10]
    (merge
     {:max-height (- innerHeight padding)}
     (if (< innerWidth (+ left menu-width width))
       {:right (- innerWidth left)}
       {:left (+ left width)})
     (if (< innerHeight (+ top menu-height))
       {:bottom padding}
       {:top top}))))

(v/defview menu
  {:component-did-update
   (fn [{!state ::v/state {:keys [starting-from]} ::v/props}]
     (let [{:keys [el] pos :position} @!state
           new-pos (position el starting-from)]
       (when-not (= new-pos pos)
         (swap! !state assoc :position new-pos))))}
  [{:keys [hide! items starting-from el-props depth]
    !state ::v/state}]
  (r/with-let [ref-fn #(when %
                         (swap! !state assoc :el % :position (position % starting-from)))]
    [positioning/portal
     (into [:div.inter.fixed
            (merge {:ref ref-fn
                    :style (merge panel-styles
                                  (or (:position @!state)
                                      {:visibility "hidden"})
                                  {:z-index (+ depth z-base)})}
                   el-props)]
           (comp (map command->item)
                 (map (fn [item]
                        (if (or (= :separator (:id item))
                                (= item :separator))
                          [:div.nj-context-menu-separator
                           {:style {:height 1 :background "#4e4e4e" :margin "8px 0"}}]
                          [menu-item (assoc item :hide! hide!)]))))
           items)]))

(v/defview context-menu*
  [{:keys [on-click?
           ancestor-commands
           ::v/props]
    !state ::v/state} content]
  ;; must pass in a literal hiccup vector with a props map
  {:pre [(and (vector? content) (map? (second content)))]}
  (let [with-ancestors (cons (select-keys props [:subcommands :context :title]) ancestor-commands)]
    [:<>
     [view.context/provide {menu-stack (if on-click?
                                         ancestor-commands
                                         with-ancestors)}
      (update content 1
              (partial merge-with
                       (fn [f1 f2]
                         (fn [event] (f1 event) (f2 event))))
              (if on-click?
                {:on-click (when on-click? (partial on-context-menu !state))
                 ;; enabling this can cause two menus to appear at the same time
                 ;; if right-clicking while context menu is open
                 #_#_:on-mouse-down (partial on-mouse-down !state)}
                {:on-context-menu (partial on-context-menu !state)}))]
     (let [{:keys [visible-at]} @!state]
       [:<>
        ;; show invisible background element when menu is visible,
        ;; prevents mouse movement from affecting context + scroll position
        (when visible-at
          [:div.fixed
           {:on-mouse-down #(do (hide-menu! !state)
                                (r/flush))
            :style {:left 0
                    :right 0
                    :bottom 0
                    :top 0
                    :z-index z-base}}])
        [:div.fixed.nj-context-menu
         (let [items #(->> (if on-click? [props] with-ancestors)
                           (keep (partial commands/resolve-subcommands
                                          (commands.state/current-context
                                           (select-keys @!state [:context-menu/event]))))
                           (reduce (fn [items {:keys [commands title]}]
                                     (cond (empty? commands) items
                                           (empty? items) (into items commands)
                                           :else (conj items
                                                       commands/separator
                                                       {:subcommands commands
                                                        :title title})))
                                   []))]
           (when visible-at
             [menu {:items (items)
                    :depth 1
                    :starting-from (assoc visible-at :width 0)
                    :hide! #(hide-menu! !state)}]))]])

     ;; handle escape key
     (r/with-let [unlisten (let [f (fn [e] (when (= "Escape" (j/get e :key))
                                             (hide-menu! !state)))]
                             (events/listen js/window "keydown" f)
                             #(events/unlisten js/window "keydown" f))]
       (finally (unlisten)))]))

(v/defview context-menu
  ;; provides context for context-menu*
  {:context-type menu-stack}
  [{:as this ::v/keys [props]} child]
  (if (or ssr? (-> props :enabled? false?))
    [:<> child]
    [context-menu* (assoc props :ancestor-commands (j/get this :context)) child]))

(dc/when-enabled
  (def formatting-commands
    {:format/bold {:action identity :keys "Mod-B"}
     :format/italic {:action identity :keys "Mod-I"}
     :format/link {:action identity :keys "Mod-K"}
     :format/monospace {:action identity :keys "Control-`"}
     :format/strikethrough {:action identity :keys "Shift-Mod-X"}})
  (def insert-block-commands
    {:editor/insert-block
     {:subcommands/layout :list
      :subcommands [{:title "Paragraph"
                     :action identity
                     :category "Text"}
                    {:title "Heading 1"
                     :action identity
                     :category "Text"}
                    {:title "Blockquote"
                     :action identity
                     :category "Text"}
                    {:title "Bullet List"
                     :action identity
                     :category "Text"}]}}))

(dc/defcard context-menu "Basic view of the context menu."
  [:div.relative {:style {:min-height 200}}
   [:div.absolute.top-0.left-0.right-0.h-full
    [context-menu {::v/initial-state {:visible-at {:left 380, :top 210}}
                   :on-click?        true
                   :enabled?         true
                   :title            "Format"
                   :subcommands      (into (keys formatting-commands) (keys insert-block-commands))}
     [:div.h-full.text-xs {:class ""} "Left-click anywhere in this devcard to open (or close) the menu."]]]]
       (-> (state/empty-db!)
           (commands/register (merge formatting-commands insert-block-commands))))
