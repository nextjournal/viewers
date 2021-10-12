(ns nextjournal.commands.command-bar
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [cljs-time.core :as time.core]
            [cljs-time.coerce :as time.coerce]
            [cljs-time.format :as time.format]
            [nextjournal.devcards :as dc]
            [nextjournal.commands.fuzzy :as fuzzy]
            [nextjournal.commands.command-bar-state :as bar-state]
            [nextjournal.commands.core :as commands]
            [nextjournal.commands.shortcuts :as shortcuts]
            [nextjournal.commands.state :as state]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.spinner :as spinner]
            [nextjournal.ui.components :as ui]
            [nextjournal.view :as v]
            [re-frame.context :as re-frame]
            [reagent.core :as reagent]
            [reagent.impl.component :as comp]
            [re-frame.core :as rf.core]
            [goog.dom :as gdom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ui helpers
(def fmt-date-time "MMM dd yyyy, HH:mm")

(defn format-util-date [d & [fmt-str zone-name]]
  (let [fmt (or fmt-str fmt-date-time)]
    (and d (time.format/unparse (time.format/formatter fmt)
                                      (time.core/to-default-time-zone (time.coerce/from-date d))))))

(re-frame/reg-sub
  :db/get-in
  (fn [db [_ path not-found]]
    (get-in db path not-found)))

(re-frame/reg-sub
  :db/get
  (fn [db [_ key]]
    (get db key)))

(defn command-elements [element]
  (array-seq (j/call element :querySelectorAll "[data-result-index]")))

(defn scroll-to-child!
  "DOM: scrolls parent so that child is visible"
  [{:as view-state :keys [element]}]
  (when-let [child-index (bar-state/get-selected view-state)]
    (j/let [PADDING 4                                       ;; add some space around the child
            ^:js {:as element
                  scroller-scrollTop :scrollTop
                  scroller-height :offsetHeight} (j/call element :querySelector ".nj-commands-list")
            command-elements (command-elements (:element view-state))]
      (when-not (false? (nth command-elements child-index false))
        (j/let [^:js {:keys [offsetTop offsetHeight]} (nth command-elements child-index)
                visible-until (+ scroller-height scroller-scrollTop)
                bottom (+ offsetTop offsetHeight)]
          (cond (> bottom visible-until)
                (j/!set element :scrollTop (+ PADDING (- bottom scroller-height)))

                (< offsetTop scroller-scrollTop)
                (j/!set element :scrollTop (- offsetTop PADDING))))))))

(defn stack-label [context {:keys [title slug]}]
  (commands/resolve-with-context context (or slug title)))

(defonce cmd-height 27)
(def ^:private closest-supported? (exists? js/Element.prototype.closest))


(defn closest [^js el sel]
  (when el
    (let [el (if (instance? js/Text el)
               (.-parentElement el)
               el)]
      (if closest-supported?
        (.closest el sel)
        (gdom/getAncestor el (fn [node] (.matches node sel)) true)))))

(def dom-closest closest)

(defn command-item [!view-state {:as command :keys [id result-index command/layout search/index title disabled? view key]}]
  (let [{:as view-state :keys [context]} @!view-state
        selected (bar-state/get-selected view-state)]
    ^{:key (or key index id)}
    [:div.flex.items-center.justify-between.px-2
     {:tab-index 0
      :data-result-index result-index
      :data-test-label title
      :data-test-state (when (= result-index selected) "selected")
      :style (cond-> {:background (if (= result-index selected)
                                    "var(--teal-color)"
                                    (when-not (= layout :list) "rgba(0,0,0,.45)"))
                      :height cmd-height}
               (= layout :list)
               (assoc :height 24 :line-height "24px" :padding "0 14px" :font-size 12))
      :class (if disabled? "opacity-50 pointer-events-none " "cursor-pointer ")
      :on-click #(commands/eval-command command)}
     [:div.flex.items-center.overflow-ellipsis.overflow-hidden.whitespace-pre
      (cond-> title
               (string? title) (fuzzy/highlight-chars (:fuzzy/chars command))
               (fn? view) (->> (view context)))
      (when (:subcommands command)
        [icon/view "ChevronRight" {:size 14
                                   :class "fill-current ml-3 "
                                   :style {:margin "-1px 0 0 0"}}])]
     [:div.text-right.whitespace-nowrap.inter
      {:style {:color "rgba(255,255,255,.6)"
               :font-size 11}}
      (when-not (str/blank? (:keys/printable command))
        (:keys/printable command))]]))

(defmulti listing (fn [type _] type))

(defmethod listing :default [_ {state :!view-state :keys [candidates]}]
  [:div.text-white.nj-commands-list.p-4.pt-2
   {:style {:margin-left 0
            :font-size 12
            :z-index 992
            :overflow-y "auto"
            :background (get-in ui/theme [:bar :background])
            :max-height "40vh"
            :bottom (get-in ui/theme [:bar :height])}}
   (->> candidates
        (partition-by :category)
        (map
         (fn [[{:keys [title category]} :as commands]]
           ^{:key (str category title)}
           [:<>
            (->> commands
                 (map-indexed
                  (fn [i cmd]
                    ^{:key (str category title i)}
                    [:div
                     {:style {:break-inside "avoid"}}
                     (when (and (zero? i) category)
                       [:div.font-bold.text-white.px-2.flex.items-center
                        {:style {:height cmd-height}}
                        (fuzzy/highlight-chars "nj-commands-highlighted-category border-b-2 border-color-teal teal" category (:fuzzy/category-chars cmd))])
                     [command-item state cmd]]))
                 doall)]))
        doall)])

(defmethod listing :grid [_ {state :!view-state :keys [candidates visible-items]}]
  (let [cols (Math/ceil (/ visible-items 7))
        col-width 200]
    [:div.text-white.nj-commands-list.p-4.pt-2
     {:style {:margin-left 0
              :font-size 11
              :z-index 992
              :overflow-y "auto"
              :background (get-in ui/theme [:bar :background])
              :column-count cols
              :column-width col-width
              :max-width (* (inc cols) col-width)
              :max-height 500}}
     (->> candidates
          (partition-by :category)
          (map
           (fn [[{:keys [title category]} :as commands]]
             ^{:key (str category title)}
             [:<>
              (->> commands
                   (map-indexed
                    (fn [i cmd]
                      ^{:key (str category title i)}
                      [:div
                       {:style {:break-inside "avoid"}}
                       (when (zero? i)
                         [:div.font-bold.text-white.px-2.flex.items-center
                          {:style {:height cmd-height}}
                          (fuzzy/highlight-chars "nj-commands-highlighted-category border-b-2 border-color-teal teal" category (:fuzzy/category-chars cmd))])
                       [command-item state cmd]]))
                   doall)]))
          doall)]))

(defn notebook-row [!view-state {:as command :keys [id result-index title disabled? key]
                                 :article/keys [created-at last-edited-at published-at visibility]}]
  (let [selected (bar-state/get-selected @!view-state)]
    ^{:key (or key title id)}
    [:tr.border-b
     {:tab-index 0
      :data-result-index result-index
      :data-test-label title
      :data-test-state (when (= result-index selected) "selected")
      :style {:background (when (= result-index selected) "var(--teal-color)")
              :border-color "rgba(255,255,255,.05)"
              :color "rgba(255,255,255,.7)"}
      :class (if disabled? "opacity-50 pointer-events-none " "cursor-pointer ")
      :on-click #(commands/eval-command command)}
     [:td.pl-4.py-1
      {:style {:width 30}}
      (when (= visibility :article.visibility/private)
        [icon/view "Lock" {:class "fill-current"}])]
     [:td.py-1.text-right
      {:style {:width 120}}
      (format-util-date published-at "MMM dd yyyy")]
     [:td.py-1.text-right
      {:style {:width 120}}
      (format-util-date created-at "MMM dd yyyy")]
     [:td.py-1.text-right
      {:style {:width 120}}
      (format-util-date last-edited-at "MMM dd yyyy")]
     [:td.py-1.text-white.pl-8
      (some-> title (fuzzy/highlight-chars (:fuzzy/chars command)))]]))

(defmethod listing :notebooks [_ {:keys [!view-state candidates]}]
  (let [{:keys [stack]} @!view-state
        {:as parent-command :keys [panel-title] parent-title :title} (some-> stack last)]
    [:div.text-white
     {:style {:font-size 12
              :z-index 992
              :background (get-in ui/theme [:bar :background])
              :width "100%"}}
     [:div
      [:div.flex.items-center.border-b.w-full.uppercase
       {:style {:font-size 10
                :color "rgba(255,255,255,.7)"
                :border-color "rgba(255,255,255,.05)"}}
       [:div.pl-4.py-1 {:style {:width 30}}
        [icon/view "Lock" {:class "fill-current"}]]
       [:div.py-1.text-right {:style {:width 120}}
        "Last published"]
       [:div.py-1.text-right {:style {:width 120}}
        "Created"]
       [:div.py-1.text-right {:style {:width 120}}
        "Last edited"]
       [:div.py-1.flex-auto.pl-8
        "Title"]]
      [:div.overflow-y-auto.nj-commands-list
       {:style {:max-height 400}}
       [:table.w-full
        [:tbody
         (for [cmd candidates]
           ^{:key (str (:title cmd) " " (:result-index cmd))}
           [notebook-row !view-state cmd])]]]]]))

(defn component-frame [this]
  (binding [comp/*current-component* this]
    (re-frame/current-frame)))

(defn command-listener-ref []
  (let [!unlisten (atom nil)]
    (re-frame/bind-fn
     (fn [element]
       (if element
         (reset! !unlisten
                 (commands/listen! {:element element
                                    :get-registry state/get-registry}))
         (some-> ^js @!unlisten .call))))))

(v/defview view
  {:context-type     re-frame/frame-context
   ::v/initial-state (comp bar-state/initial-state ::v/props)
   :UNSAFE_component-will-mount
   (fn [this]
     (j/assoc! this :ref-fn
               (fn [el]
                 (swap! (::v/state this) assoc :element el))))
   :component-did-mount
   (fn [{:as this !view-state ::v/state}]
     ;; focus command-bar when view-state is set
     (add-watch !view-state ::focus-bar
                (fn [_ _ old new]
                  (when (and (not (bar-state/active-stack? old)) (bar-state/active-stack? new))
                    (bar-state/focus-bar-input! @!view-state))
                  (when (and (bar-state/active-stack? old)
                             (not (bar-state/active-stack? new)))
                    (bar-state/refocus! @!view-state))))

     (re-frame/bind-frame (component-frame this)
                          (state/set-context! :!view-state !view-state)))
   :component-did-update
   (fn [{!view-state ::v/state}]
     (scroll-to-child! @!view-state))
   :component-will-unmount
   (fn [{:as this !view-state ::v/state}]
     (re-frame/bind-frame (component-frame this)
                          (state/unset-context! :!view-state !view-state)))}
  [{:as this !view-state ::v/state :keys [ref]}]
  (reagent/with-let [ref-fn (command-listener-ref)]
    (let [{:as view-state :keys [stack context shortcuts]} @!view-state
          {:keys [subcommands/layout]} (last stack)
          candidates (bar-state/candidates view-state)
          category-count (count (into #{} (map :category) candidates))
          visible-items (+ category-count (count candidates))
          layout (or layout :grid) #_(cond
                                       layout layout
                                       (= 1 category-count) :list
                                       (<= rows 6) :list
                                       :else :grid)
          desktop? @(re-frame/subscribe [:desktop?])]
      [:div
       {:ref ref-fn}
       (when-not desktop?
         [:div.w-full.text-white.monospace.relative.text-md.overflow-x-scroll.min-w-full
          {:style {:background "rgba(31, 41, 55, 0.94)"}}
          (when-not (:context view-state)
            [shortcuts/view shortcuts])])
       [:div.w-full.text-white.monospace.command-bar.relative
        {:style {:z-index 1000 :background "#1f2937"}
         :ref (j/get this :ref-fn)}
        [:div
         (when (and context (= layout :list))
           {:class "absolute left-0 bottom-0"})
         [:div.flex.px-4.items-center
          (if context
            {:class "flex items-center"
             :style {:background "#11171e" :height 40 :font-size 13}}
            {:style {:font-size 13 :background (get-in ui/theme [:bar :background])
                     :height (if desktop?
                               (get-in ui/theme [:bar :height])
                               "50px")}})
          [:div.flex.items-center
           (when context
             {:class "flex-auto"
              :style {:background "#455568" :height 28 :border-radius 3
                      :padding-left 2 :padding-right 2
                      :box-shadow "inset 0 3px 3px rgba(0,0,0,.15)"
                      :border "1px solid rgba(255,255,255,.1)"}})
           (into [:div.flex.items-center.flex-shrink-0.nj-commands-stack]
                 (for [{:as item :keys [on-expose on-dispose]} (remove :invisible-stack? stack)
                       :let [label (stack-label (:context item) item)]]
                   [:div.relative.flex.items-center.whitespace-nowrap.pl-2.pr-1.mr-1
                    {:style {:background "#11171e" :border-radius 2
                             :height 22}
                     :ref (fn [el]
                            (if el
                              (when on-expose (on-expose (:context item) item))
                              (when on-dispose (on-dispose (:context item) item))))}
                    label
                    [icon/view "ChevronRight" {:size 12 :class "fill-current"}]]))
           [:input.outline-none.nj-commands-input
            {:value (bar-state/get-query @!view-state)
             :auto-complete "off"
             :style {:height (if desktop? 20 40) :z-index 3 :width 200 :margin-left 6}
             :placeholder (if context "Filter…" "⌘J Search commands…")
             :on-mouse-down #(re-frame/bind-frame (component-frame this)
                               (state/set-context! :!view-state !view-state)
                               (bar-state/activate-bar! !view-state))
             :on-change #(bar-state/set-query! !view-state (j/get-in % [:target :value]))
             :on-blur #(when-not (some-> (j/get % :relatedTarget) ;; check if we are clicking within the command palette
                                         (dom-closest ".command-bar"))
                         (bar-state/blur-command-bar! !view-state))}]
           (when (bar-state/subcommands-loading? view-state)
             [spinner/view {:size 24 :class "fill-current opacity-30 ml-3 -mr-2"}])]
          (when-not (or (:context view-state) (not desktop?))
            [shortcuts/view shortcuts])]
         (when (seq candidates)
           ^{:key "commands-list"}
           [listing layout {:!view-state !view-state
                            :candidates candidates
                            :visible-items visible-items}])]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; keyboard control of command palette

(defn- bounded [length i]
  (-> i
      (min (dec length))
      (max 0)))

(defn nav-to-visible-command [!view-state direction]
  (when-let [elements (some->> (:element @!view-state)
                               command-elements
                               (mapv (fn [^js el]
                                       (j/let [^:js {:keys [left top height]} (j/call el :getBoundingClientRect)]
                                         {:index (js/parseInt (j/get-in el [:dataset :resultIndex]))
                                          :left left
                                          :bottom (+ top height)})))
                               (sort-by (juxt :left :bottom)))]
    (let [selected (bar-state/get-selected @!view-state)
          get-el (reduce #(assoc %1 (:index %2) %2) {} elements)
          nearest (fn [n k coll] (->> coll (sort-by (comp Math/abs (partial - n) k)) first))
          {current-left :left current-bottom :bottom} (get-el selected)
          cols (partition-by :left elements)
          nav-to (case direction
                   :left (let [col (or (->> cols (take-while #(< (:left (first %)) current-left)) last)
                                       (last cols))]
                           (nearest current-bottom :bottom col))
                   :up (let [col (->> cols (drop-while #(< (:left (first %)) current-left)) first)]
                         (or (last (take-while #(< (:bottom %) current-bottom) col))
                             (last (->> cols (take-while #(< (:left (first %)) current-left)) last))
                             (last (last cols))))
                   :down (let [col (->> cols (drop-while #(< (:left (first %)) current-left)) first)]
                           (or (first (drop-while #(<= (:bottom %) current-bottom) col))
                               (first (->> cols (drop-while #(<= (:left (first %)) current-left)) first))
                               (first (first cols))))
                   :right (let [col (or (->> cols (drop-while #(<= (:left (first %)) current-left)) first)
                                        (first cols))]
                            (nearest current-bottom :bottom col)))]
      (when nav-to
        (swap! !view-state bar-state/set-selected! (:index nav-to))))))

(commands/register!
  (->> [:left :right :up :down]
       (map (fn [direction]
              [(keyword "command-bar" (name direction))
               {:requires #{:command-bar-focused?}
                :keys (str "Arrow" (str/capitalize (name direction)))
                :private? true
                :blur-command-bar? false
                :action (fn [{:keys [!view-state]}]
                          (nav-to-visible-command !view-state direction)
                          :stop)}]))
       (into {})))

(state/register-context-fn! :command-bar
  (fn [context]
    (when (some-> (:!view-state context) deref bar-state/active-stack?)
      {:command-bar-focused? true})))

(commands/register!
 #:command-bar {:back
                {:requires #{:command-bar-focused?}
                 :keys "Backspace"
                 :private? true
                 :blur-command-bar? false
                 :when (fn [{!view-state :!view-state}]
                         (let [{:keys [element stack]} @!view-state]
                           (and (> (count stack) 1)
                                (when-let [el (some-> element (j/call :querySelector "input"))]
                                  (= 0
                                     (j/get el :selectionStart)
                                     (j/get el :selectionEnd))))))
                 :action (fn [{:keys [!view-state]}]
                           (swap! !view-state update :stack pop))}
                :blur
                {:requires #{:command-bar-focused?}
                 :keys "Escape"
                 :private? true
                 :action (constantly nil)}

                :toggle
                {:keys "Mod-J"
                 :private? true
                 :blur-command-bar? false
                 :action (comp (constantly :stop)
                               bar-state/toggle-bar!
                               :!view-state)}

                #_#_:all-shortcuts
                {:blur-command-bar? false
                 :requires #{:!view-state}
                 :action (comp (constantly :stop)
                               bar-state/activate-bar!
                               :!view-state)}
                :select
                {:requires #{:command-bar-focused?}
                 :keys ["Tab" "Enter"]
                 :private? true
                 :blur-command-bar? false
                 :action (fn [{:keys [!view-state]}]
                           (let [candidates (bar-state/candidates @!view-state)
                                 selected (bar-state/get-selected @!view-state)]
                             (some-> (nth candidates selected nil)
                                     (commands/eval-command))))}})



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
                     :category "Text"}
                    {:title "Code cell: Python"
                     :action identity
                     :category "Code"}
                    {:title "Code cell: Bash"
                     :action identity
                     :category "Code"}
                    {:title "Code cell: Julia"
                     :action identity
                     :category "Code"}
                    {:title "Code cell: Clojure"
                     :action identity
                     :category "Code"}
                    {:title "Formula"
                     :action identity
                     :category "Math"}]}})
  (def editor-commands
    {:editor/insert-inline {:action identity
                            :category :editor}
     :editor/focus-block {:action identity
                          :category :editor}
     :editor/delete-block {:action identity
                           :category :editor}})
  (def run-commands {:run/all {:action identity}
                     :run/run-cells-below {:action identity}
                     :run/reset-all {:action identity}
                     :run/run-on-a-schedule {:action identity}})
  (def notebook-cmds {:notebook/open
                      {:title              "Open Notebook"
                       :subcommands/layout :notebooks
                       :subcommands        [{:title                  "Notebook 1"
                                             :action identity
                                             :disabled?              false
                                             :article/created-at     #inst"2021-10-05T12:51:30.865-00:00"
                                             :article/last-edited-at #inst"2021-10-05T12:51:30.865-00:00"
                                             :article/published-at   #inst"2021-10-05T12:51:30.865-00:00"}
                                            {:title                  "Notebook 2"
                                             :action identity
                                             :disabled?              false
                                             :article/created-at     #inst"2021-10-01T12:51:30.865-00:00"
                                             :article/last-edited-at #inst"2021-10-02T12:51:30.865-00:00"
                                             :article/published-at   #inst"2021-10-03T12:51:30.865-00:00"}
                                            {:title                  "Notebook 3"
                                             :action identity
                                             :disabled?              false
                                             :article/created-at     #inst"2021-10-07T12:51:30.865-00:00"
                                             :article/last-edited-at #inst"2021-10-08T12:51:30.865-00:00"
                                             :article/published-at   #inst"2021-10-09T12:51:30.865-00:00"}]}}))

(let [make-devcard-state! (comp #(bar-state/activate-bar! % {:!view-state %})
                                bar-state/initial-state)]
  (dc/defcard command-bar
    [view {::v/initial-state {:categories [:format]
                              :shortcuts {:format {:commands (vec (keys formatting-commands))}}}}]
    (-> (state/empty-db!)
        (commands/register formatting-commands)))

  (dc/defcard command-bar-grid
    [view {::v/initial-state #(-> (make-devcard-state!
                                    {:categories [:format :editor :run]}))}]
    (-> (state/empty-db!)
        (commands/register (merge formatting-commands insert-block-commands editor-commands run-commands))))

  (dc/defcard command-bar-list
    [:div.relative {:style {:min-height 360}}
     [:div.absolute.bottom-0.left-0.right-0
      [view {::v/initial-state #(-> (make-devcard-state!
                                      {:categories [:editor]})
                                    (doto (swap! bar-state/update-stack {:category :editor
                                                                         :normalized? true
                                                                         :stack-key (str (random-uuid))
                                                                         :title "Insert Block"
                                                                         :subcommands/layout :list
                                                                         :subcommands (-> insert-block-commands :editor/insert-block :subcommands)})))}]]]
              (-> (state/empty-db!)
                  (commands/register insert-block-commands)))

  (dc/defcard command-bar-table
    [view {::v/initial-state #(-> (make-devcard-state!
                                    {:categories [:notebook]})
                                  (doto (swap! bar-state/update-stack {:category :notebook
                                                                       :normalized? true
                                                                       :stack-key (str (random-uuid))
                                                                       :title "Notebooks"
                                                                       :subcommands/layout :notebooks
                                                                       :subcommands (-> notebook-cmds :notebook/open :subcommands)})))}]
              (-> (state/empty-db!)
                  (commands/register notebook-cmds))))