(ns nextjournal.devcards-ui
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            [nextjournal.commands.core :as commands]
            [nextjournal.commands.command-bar :as command-bar]
            [nextjournal.devcards :as dc]
            [nextjournal.log :as log]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.promises :as promises]
            [nextjournal.view :as v]
            [nextjournal.viewer :as data]
            [reitit.frontend.easy :as rfe]
            [re-frame.context :as rf]
            [re-frame.frame :as rf.frame]
            [reagent.core :as reagent]))

(defn render-md [md-str]
  [:div.devcard-desc.text-sm
   [:div.viewer-markdown [data/inspect (data/view-as :markdown md-str)]]])

(defn cards-link []
  [:a.text-gray-400.hover:text-gray-500 {:href (rfe/href :devcards/root)}
   [:svg.flex-shrink-0.h-5.w-5 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:d "M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z"}]]
   [:span.sr-only "Cards"]])

(defn shorten-ns [ns] (str/replace ns "nextjournal." ""))

(defn ns-link [ns]
  [:a.text-xs.font-medium.text-gray-500.hover:text-gray-500
   {:href (rfe/href :devcards/by-namespace {:ns ns})}
   (shorten-ns ns)])

(defn name-link [ns name]
  [:a.text-near-black {:href (rfe/href :devcards/card {:ns ns :name name})} name])

(defn ns-listing []
  (->> (seq @dc/registry)
       (sort-by key)
       (reduce (fn [out [ns cards]]
                 (let [[_ ns-parent ns-name] (re-matches #"(.*?).([^.]+)$" ns)]
                   (conj out {:ns ns
                              :ns-parent ns-parent
                              :ns-name ns-name
                              :ns-count (count cards)
                              :ns-first-child? (not= ns-parent (:ns-parent (peek out)))
                              :cards (vals cards)}))) [])
       (sort-by (juxt :ns-parent :ns))))

(v/defview toc [{:keys [current-ns]}]
  (reagent/with-let [label "Component Library"
                     pinned? (reagent/atom true)]
    [:div.toc.fixed.top-0.left-0.bottom-0.z-10.sans-serif
     {:class (when @pinned? "pinned")
      :style (cond-> {:top 0
                      :width 50}
               @pinned?
               (assoc :position "relative"
                      :width "auto"
                      :top 0))}
     [:div.absolute.left-0.opacity-100.w-0.h-0.transition-all.duration-300
      {:class "top-1/2"
       :style {:transform "rotate(-90deg)"
               :transform-origin "right top 0"}}
      [:div.text-center.uppercase.tracking-wide.white-space-nowrap.left-0.top-0.opacity-30
       {:style {:font-size 13
                :width 200
                :margin-left -100
                :margin-top 15}}
       label]]
     [:div.toc-content.absolute.left-0.top-0.border-r.border-black-10.text-sm.transition-all.duration-300.max-h-screen.overflow-y-auto.bg-white.px-4.h-screen.max-h-screen
      {:style (cond-> {:width 315}
                @pinned?
                (assoc :position "relative"
                       :top "auto"
                       :transform "none")
                (not @pinned?)
                (assoc :box-shadow "0 3px 20px rgba(0,0,0,.2)"))}
      [:div.flex.items-center.pl-3.pt-4.justify-between
       [:a.font-bold.text-xs.uppercase.tracking-wide.hover:underline
        {:href (rfe/href :devcards/root)}
        label]
       [:div.cursor-pointer.rounded-sm.border.border-gray-300.leading-none.px-2.py-1.text-gray-500.hover:text-gray-700.hover:border-gray-300
        {:on-click #(swap! pinned? not)
         :style {:font-size 11}}
        (if @pinned? "Unpin" "Pin")]]
      (doall
       (for [{:keys [ns
                     ns-parent
                     ns-name
                     ns-count
                     ns-first-child?]} (ns-listing)]
         ^{:key ns}
         [:<>
          (when ns-first-child?
            [:div.px-3.pt-5.font-medium.text-xs.mb-2
             (shorten-ns ns-parent)])
          [:a.flex-auto.flex.items-center.px-3.py-1.rounded-sm.text-gray-600.hover:bg-indigo-50.mt-1
           {:href (rfe/href :devcards/by-namespace {:ns ns})
            :style {:font-size 15}
            :class (when (= ns current-ns) "bg-indigo-100 text-indigo-600")}
           [:span ns-name]
           [:span.rounded-full.leading-none.border.px-2.ml-2
            {:style {:font-size 12 :padding-top 2 :padding-bottom 2}
             :class (if (= ns current-ns)
                      "border-indigo-300 text-indigo-600"
                      "border-gray-300")}
            ns-count]]]))]]))

(v/defview inspector [{:keys [ratom initial-value label label-aligned?]}]
  [:div.monospace.relative.flex
   {:class "block px-4 py-2 border-t border-black-05 relative"
    :style {:font-size 14}}
   [:div.flex.justify-end {:style (when label-aligned? {:width 65})}
    [:div.rounded-full.uppercase.tracking-wide.px-2.font-bold.mr-2.flex.items-center
     {:style {:font-size 10
              :height 20
              :line-height "20px"
              :background-color "rgba(5, 118, 179, 0.1)"}
      }
     label]]
   [:div.flex-auto.overflow-x-auto
    [data/inspect @ratom]]
   [:div.text-gray-500.hover:text-gray-800.cursor-pointer.bg-white.pl-3
    {:on-click #(reset! ratom initial-value)}
    [icon/view "Refresh" {:size 18 :class "fill-current"}]]])

(v/defview show-main [{::v/keys [state props]
                       :keys [main initial-db initial-state ::dc/class]}]
  (when main
    ;; reset app-db once (using with-let) when the component mounts
    ;; (further actions should not reset the db again)
    (reagent/with-let [_ (when (seq initial-db)
                           (reset! (:app-db (rf/current-frame)) initial-db))]
      (let [main (main)
            main (if (fn? main)
                   [main state]
                   main)]
        [:div
         [nextjournal.devcards/error-boundary
          [:div {:class (or class "p-3")} main]]
         (when initial-state
           [inspector {:ratom state :label "state" :initial-value initial-state :label-aligned? initial-state}])
         (when (seq initial-db)
           [inspector {:ratom (:app-db (rf/current-frame)) :label "db" :initial-value initial-db :label-aligned? initial-state}])]))))

(v/defview show-card* [{card ::v/props :keys [initial-state name ns doc loading-data?]}]
  (when card
    (when (= name "sidebar-elements")
      (prn :CARD card))
    [:div.mb-10
     [:div.mb-4.sans-serif.text-sm
      (when-not (false? (::dc/title? card))
        [:a.font-bold.text-lg
         {:href (rfe/href :devcards/by-name {:ns ns :name name})} name])

      (when (and doc (not (false? (::dc/description? card))))
        [render-md doc])]
     [:div.bg-white.rounded-md.border.border-gray-200.shadow-sm
      (if loading-data?
        [:div.p-4 "Loading data..."]
        [show-main (assoc card ::v/initial-state initial-state)])]]))

(def dc-opts [::dc/state ::dc/title? ::dc/description? ::dc/class])

(defn format-data [{:as db ::dc/keys [state]}]
  {:initial-state state
   :initial-db (apply dissoc db dc-opts)})

(defn extract-opts [data]
  (select-keys data dc-opts))

(v/defview show-card [{card ::v/props :keys [data compile-key]}]
  (reagent/with-let [frame (rf.frame/make-frame
                            {:registry (:registry (rf/current-frame))
                             :app-db (reagent/atom {})})]
    [rf/provide-frame frame
     (rf/bind-frame
      frame
      (let [data (when data (data))]
        (log/trace :devcards/show-card {:card card :frame-id (:frame-id (rf/current-frame))})
        [:div
         (if (fn? data)
           ;; `compile-key` is used to force a re-mount of show-card*
           ;; when the _literal code_ for the card's data has changed.
           ;; (a macro sets compile-key's value to the hash of the code)
           ^{:key compile-key}
           [promises/view
            {:promise (data)
             :on-value (fn [data]
                         [show-card* (merge card (format-data data) (extract-opts data))])
             :on-error (fn [error] [:div "Error loading data: " (str error)])
             :on-loading (fn [] [show-card* (merge card {:loading-data? true} (extract-opts data))])}]
           ^{:key compile-key}
           [show-card* (merge card (format-data data) (extract-opts data))])]))]))

(v/defview show-namespace [{:keys [cards ns]}]
  (when ns
    ^{:key ns}
    [:div.px-12.sans-serif
     [:div.py-4.border-b
      [:ol.flex.items-center.space-x-3 {:role "list"}
       [:li
        [:div.flex.items-center
         (cards-link)]]
       [:li
        [:div.flex.items-center
         icon/divider
         [:span.ml-3 (ns-link ns)]]]]]
     [:div.py-8
      (doall
        (for [[name card] cards]
             ^{:key name} [show-card card]))]]))

(v/defview root []
  [:div.sans-serif.flex.h-screen.justify-center.py-18
   "TODO: Render welcome devcard"])

(v/defview by-namespace [{:keys [ns ::v/props]}]
     [show-namespace (-> props
                      (assoc :cards (get @dc/registry ns)))])

(v/defview by-name [{:keys [ns name ::v/props]}]
  [:div.px-12.sans-serif
   [:div.py-4.border-b
    [:ol.flex.items-center.space-x-3 {:role "list"}
     [:li
      [:div.flex.items-center
       (cards-link)]]
     [:li
      [:div.flex.items-center
       icon/divider
       [:span.ml-3 (ns-link ns)]]]]]
   [:div.py-8
    [show-card (-> props
                   (merge (get-in @dc/registry [ns name])))]]])

(v/defview layout [{:keys [::v/props view ns]}]
  [:div.flex.h-screen.bg-white
   [toc {:current-ns ns}]
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
    [view props]]])

(dc/when-enabled
 (commands/register! :dev/devcards
   {:title "Browse Devcards"
    :keys "Alt-D"
    :subcommands/layout :list
    :subcommands
    (fn [_context]
      (into [{:title "Overview"
              :dispatch [:router/push [:devcards/root]]}]
            (map (fn [{:keys [ns cards]}]
                   {:title (shorten-ns ns)
                    :subcommands/layout :list
                    :subcommands
                    (cons {:title (str "All " (count cards) " cards")
                           :dispatch [:router/push [:devcards/by-namespace {:ns ns}]]}
                          (for [{:keys [ns name]} cards]
                            {:title name
                             :dispatch [:router/push [:devcards/by-name {:ns ns :name name}]]}))}))
            (ns-listing)))}))

(dc/defcard render-markdown-card
  "This is our `card` with **some** _formatting_.

$$\\frac{1}{v}=\\frac{1}{\\lambda^{3}} g_{3 / 2}(f)+\\frac{1}{V} \\frac{f}{1-f}$$

And a block and inline $e$ formula.

And lists with bullets:

* one
* two
* three

Numbers:

1. one
2. two
3. three

TODOs:

* [ ] Needs to be fixed
* [x] All done
"
  []
  [render-md "_not_ much to see here, move along."])

(dc/defcard promise-state
  "A devcard that loads state asynchronously"
  [state]
  [:div (str @state)]
  #(p/do (p/timeout 1000)
         {::dc/state "Hello!"
          :db-value (rand-int 101)}))
