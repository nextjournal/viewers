(ns nextjournal.devcards-ui
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            [nextjournal.commands.core :as commands]
            [nextjournal.devcards :as dc]
            [nextjournal.log :as log]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.promises :as promises]
            [nextjournal.markdown :refer [Markdown]]
            [nextjournal.view :as v]
            [nextjournal.viewer :as data]
            [reitit.frontend.easy :as rfe]
            [re-frame.context :as rf]
            [re-frame.frame :as rf.frame]
            [reagent.core :as r]))

(defn render-md [md-str]
  [:div.leading-normal
   {:style {:margin "15px 0"
            :font-size 15}
    :dangerouslySetInnerHTML
    {:__html (.render ^js @Markdown md-str)}}])

(def divider [:span.black-20.text-md.px-1 "/"])

(defn cards-link []
  [:a.black-50.hover:text-gray-900.hover:underline {:href (rfe/href :devcards/root)} "cards"])

(defn shorten-ns [ns] (str/replace ns "nextjournal." ""))

(defn ns-link [ns]
  [:a.black-50.hover:text-gray-900.hover:underline {:href (rfe/href :devcards/by-namespace {:ns ns})} (shorten-ns ns)])

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
  (r/with-let [label "Component Library"
               pinned? (r/atom true)]
    [:div.toc
     {:class (when @pinned? "pinned")
      :style (cond-> {:top 0}
               @pinned?
               (assoc :position "relative"
                      :width "auto"
                      :top 0))}
     [:div.toc-button
      [:div.toc-label
       {:style {:font-size 14}}
       label]]
     [:div.toc-content.border-r.border-black-10
      {:style (cond-> {:height "100vh"
                       :max-height "100vh"
                       :padding-bottom 40
                       :padding-right 0}
                @pinned?
                (assoc :position "relative"
                       :top "auto"
                       :transform "none")
                (not @pinned?)
                (assoc :box-shadow "0 3px 20px rgba(0,0,0,.2)"))}
      [:div.toc-header
       {:style {:color "var(--near-black-color)"}}
       [:a {:style {:color "inherit"}
            :href (rfe/href :devcards/root)} label]
       [:div.toc-pin
        {:on-click #(swap! pinned? not)}
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
            [:div.ml-4.mt-4.mb-2.font-bold.text-near-black
             {:style {:font-size 14}}
             (shorten-ns ns-parent)])
          [:a.flex-auto.flex.items-center.pl-8.pr-3
           {:href (rfe/href :devcards/by-namespace {:ns ns})
            :style (cond-> {:font-size 14
                            :padding-top "2px"
                            :padding-bottom "2px"
                            :color "var(--near-black-color)"}
                     (= ns current-ns)
                     (assoc :background-color "rgba(5, 118, 179, 0.1)"))}
           [:span ns-name]
           [:span.rounded.bg-black-05.black-60.ml-2
            {:style {:font-size 12 :padding "3px 4px" :line-height 1}}
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
                       :keys [main initial-db initial-state]}]
  (when main
    (r/with-let [app-db (let [{:keys [app-db]} (rf/current-frame)]
                          (when (seq initial-db)
                            (reset! app-db initial-db))
                          app-db)]
      (let [main (main)
            main (if (fn? main)
                   [main state]
                   main)]
        [:div
         [nextjournal.devcards/error-boundary
          [:div.p-3 main]]
         (when initial-state
           [inspector {:ratom state :label "state" :initial-value initial-state :label-aligned? initial-state}])
         (when (seq initial-db)
           [inspector {:ratom app-db :label "db" :initial-value initial-db :label-aligned? initial-state}])]))))

(v/defview show-card* [{card ::v/props :keys [class initial-state name ns doc loading-data?]}]
  (when card
    (when (= name "sidebar-elements")
      (prn :CARD card))
    [:div.mb-8
     [:div.page.mb-3.sans-serif.text-lg
      [:a.font-bold.text-near-black.text-md.sans-serif
       {:href (rfe/href :devcards/by-name {:ns ns :name name})} name]

      (when doc
        [render-md doc])]

     [:div {:class (or class "page")}
      [:div.bg-white.rounded-md.border.border-black-05
       (if loading-data?
         [:div.p-3 "Loading data..."]
         [show-main (assoc card ::v/initial-state initial-state)])]]]))

(defn format-data [{:as db ::dc/keys [state]}]
  {:initial-state state
   :initial-db (dissoc db ::dc/state)})

(v/defview show-card [{card ::v/props :keys [data compile-key]}]
  (let [frame (rf.frame/make-frame
               {:registry (:registry (rf/current-frame))
                :app-db (r/atom {})})]
    [rf/provide-frame frame
     (rf/bind-frame frame
       (let [data (when data (data))]
         (log/trace :devcards/show-card {:card card :frame-id (:frame-id (rf/current-frame))})
         [:div
          (if (fn? data)
            ^{:key compile-key}
            [promises/view
             {:promise (data)
              :on-value (fn [data]
                          [show-card* (merge card (format-data data))])
              :on-error (fn [error] [:div "Error loading data: " (str error)])
              :on-loading (fn [] [show-card* (assoc card :loading-data? true)])}]
            ^{:key compile-key} [show-card* (merge card (format-data data))])]))]))

(v/defview show-namespace [{:keys [cards ns]}]
  (when ns
    ^{:key ns}
    [:<>
     [:div.page.mt-3.mb-8.sans-serif
      {:style {:font-size 14}}
      (cards-link) divider (ns-link ns)]
     (doall
      (for [[name card] cards]
        ^{:key name} [show-card card]))]))

(v/defview root []
  [:div.page.sans-serif
   (doall
    (for [{:keys [ns
                  ns-parent
                  ns-name
                  ns-count
                  ns-first-child?]} (ns-listing)]
      ^{:key ns}
      [:<>
       (when ns-first-child?
         [:div.mt-8.text-md.mb-2 (shorten-ns ns-parent)])
       [:div.ml-6
        [:a.flex-auto.text-near-black.flex.items-center
         {:href (rfe/href :devcards/by-namespace {:ns ns})}
         [:span.font-bold ns-name]
         [:span.rounded.bg-black-05.black-60.ml-2
          {:style {:font-size 12 :padding "3px 4px" :line-height 1}}
          ns-count]]]]))])

(v/defview by-namespace [{:keys [ns ::v/props]}]
     [show-namespace (-> props
                      (assoc :cards (get @dc/registry ns)))])

(v/defview by-name [{:keys [ns name ::v/props]}]
  [:<>
   [:div.page.mt-3.mb-8.sans-serif
    {:style {:font-size 14}}
    (cards-link) divider (ns-link ns)]
   [show-card (-> props
                  (merge (get-in @dc/registry [ns name])))]])


(v/defview layout [{:keys [::v/props view ns]}]
  [:div.flex.h-screen
   {:style {:background "#f8f8f8"}}
   [toc {:current-ns ns}]
   [:div.h-screen.overflow-y-auto.flex-auto.devcards-content
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
            (ns-listing)))})

 (commands/register! :dev/remount-app
   {:title "Re-mount App"
    :keys "Ctrl-R"
    :action #(rf/dispatch [:perform-fx {:mount-app {}}])}))

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
