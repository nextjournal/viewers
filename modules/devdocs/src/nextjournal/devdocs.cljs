(ns nextjournal.devdocs
  (:require [cljs.reader :as cljs-reader]
            [clojure.string :as str]
            [flatland.ordered.map :as om]
            [nextjournal.ui.components.icon :as icon]
            [lambdaisland.deja-fu :as deja-fu]
            [nextjournal.devcards]
            [nextjournal.devcards-ui]
            [nextjournal.viewer :as v]
            [nextjournal.viewer.notebook]
            [re-frame.context :as re-frame :refer [defc]]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe])
  (:require-macros [nextjournal.devdocs :refer [devdoc-collection show-card]]
                   [nextjournal.util.macros :refer [for!]]))

(defonce registry (reagent/atom (om/ordered-map)))

(goog-define contentsTitle "contents")
(goog-define logoImage "https://cdn.nextjournal.com/images/nextjournal-logo.svg")

(def chevron-double-right
  [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :width 14 :height 14 :class "transition-all"}
   [:path {:fill-rule "evenodd" :d "M10.293 15.707a1 1 0 010-1.414L14.586 10l-4.293-4.293a1 1 0 111.414-1.414l5 5a1 1 0 010 1.414l-5 5a1 1 0 01-1.414 0z" :clip-rule "evenodd"}]
   [:path {:fill-rule "evenodd" :d "M4.293 15.707a1 1 0 010-1.414L8.586 10 4.293 5.707a1 1 0 011.414-1.414l5 5a1 1 0 010 1.414l-5 5a1 1 0 01-1.414 0z" :clip-rule "evenodd"}]])

(def chevron-double-left
  [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :width 14 :height 14 :class "transition-all"}
   [:path {:fill-rule "evenodd" :d "M15.707 15.707a1 1 0 01-1.414 0l-5-5a1 1 0 010-1.414l5-5a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 010 1.414zm-6 0a1 1 0 01-1.414 0l-5-5a1 1 0 010-1.414l5-5a1 1 0 011.414 1.414L5.414 10l4.293 4.293a1 1 0 010 1.414z" :clip-rule "evenodd"}]])

(def menu
  [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :width 16 :height 16}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]])

(defn scroll-to-fragment [el-id]
  (when-let [el (js/document.getElementById el-id)]
    (.scrollIntoView el)))

(defn inner-toc [coll-id devdoc-id entries]
  (for! [{:keys [title children]} entries
         :into [:div]]
    [:div
     (when title
       (let [el-id (-> title
                       (str/replace #"`" "")
                       (str/replace #" " "-")
                       str/lower-case
                       js/encodeURIComponent)]
         [:a.block.mb-1 {:href (rfe/href :devdocs/fragment {:collection coll-id
                                                            :devdoc devdoc-id
                                                            :fragment el-id})}
          title]
         (when (seq children)
           [inner-toc coll-id devdoc-id children])))]))

(defn devdocs-toc [devdocs & [{:keys [inner?]}]]
  (for! [{:keys [id title toc collection-id]} devdocs
         :into [:<>]]
    [:div.mt-1
     [:a.text-indigo-300.hover:text-white
      {:href (rfe/href :devdocs/devdoc {:collection collection-id :devdoc id})} title]
     (when inner?
       [inner-toc collection-id id (:children toc)])]))

(defn toc-container [{label :title up :up} & children]
  (reagent/with-let [pinned? (reagent/atom true)]
    [:div.toc
     {:class (when @pinned? "pinned")
      :style (cond-> {:top 0}
               @pinned?
               (assoc :position "relative"
                      :width "auto"
                      :top 0))}
     [:div.toc-button
      [:div.toc-label.text-base.flex
       label]]
     [:div.toc-content.border-r.border-black-10
      {:style (cond-> {:height "100vh"
                       :max-height "100vh"
                       :width "260px"
                       :padding-bottom 40
                       :padding-right 0}
                @pinned?
                (assoc :position "relative"
                       :top "auto"
                       :transform "none")
                (not @pinned?)
                (assoc :box-shadow "0 3px 20px rgba(0,0,0,.2)"))}
      [:div.toc-header.ml-4.border-b.pb-2.mb-2.mr-2
       {:style {:color "var(--near-black-color)"}}
       (if up
         [:div.text-black.rounded.flex.justify-center.items-center.cursor-pointer.cursor-pointer.hover:underline
          {:on-click #(re-frame/dispatch [:router/push up])}
          [icon/view "ChevronLeft" {:size 18 :class "relative flex-shrink-0" :style {:top -1}}]
          [:span.ml-1 label]]
         label)
       [:div.toc-pin.ml-2
        {:on-click #(swap! pinned? not)}
        (if @pinned? "Unpin" "Pin")]]
      (into [:div.pl-4] children)]]))

(defn sidebar-content [{:keys [title footer collapsed? mobile?]} & content]
  [:div.px-12.py-6.bg-indigo-900.overflow-y-auto.flex-shrink-0.sidebar-content
   (if @mobile?
     {:class "fixed left-0 top-0 right-0 bottom-0 z-10"}
     {:style {:width 330}
      :class (str "relative " (when @collapsed? "rounded"))})
   [:div.text-indigo-300.absolute.left-0.top-0.w-full.p-4.cursor-pointer.flex.items-center.group.hover:text-white.hover:bg-black.hover:bg-opacity-25.transition-all
    {:on-click #(swap! collapsed? not)}
    (if @collapsed?
      chevron-double-right
      chevron-double-left)
    [:span.text-xs.font-light.opacity-0.group-hover:opacity-100.transition-all
     {:style {:margin-left 13}}
     (str (if @collapsed?
            (if @mobile? "Show" "Pin")
            (if @mobile? "Hide" "Unpin"))
          " sidebar")]]
   [:a.block.mt-12
    {:href (rfe/href :devdocs/index)}
    [:img {:src logoImage :width "100%" :style {:max-width 235}}]]
   [:div.mt-12.pb-2.border-b.mb-2.uppercase.tracking-wide.text-base.md:text-sm.text-indigo-300
    {:style {:border-color "rgba(255,255,255,.2)"}}
    title]
   (into [:div.border-b.pb-2.text-base.md:text-sm.font-light
          {:style {:border-color "rgba(255,255,255,.2)"}}]
         content)
   (when footer
     [:div.mt-2.font-light.text-xs
      footer])])

(defn sidebar [options & content]
  (reagent/with-let [collapsed? (reagent/atom false)
                     mobile? (reagent/atom false)
                     resize #(if (< js/innerWidth 640)
                               (do
                                 (reset! mobile? true)
                                 (reset! collapsed? true))
                               (do
                                 (reset! mobile? false)
                                 (reset! collapsed? false)))
                     ref-fn #(when %
                               (js/addEventListener "resize" resize)
                               (resize))]
    (let [options (assoc options :collapsed? collapsed? :mobile? mobile?)]
      [:div.flex.h-screen
       {:ref ref-fn}
       (if @collapsed?
         [:div.fixed.left-0.top-0.p-4.text-indigo-900.flex.items-center.z-10.cursor-pointer.group
          {:on-click #(reset! collapsed? false)}
          menu
          [:div.fixed.top-0.left-0.bottom-0.z-20.p-4.collapsed-sidebar
           {:class (if @mobile?
                     (str "mobile-sidebar " (if @collapsed? "hidden" "flex"))
                     "flex")}
           (into [sidebar-content options] content)]]
         (into [sidebar-content options] content))])))

;; :devdocs/index
(defn index-view [match]
  [:div.flex.h-screen.devdocs-body
   (for! [{:keys [id title devdocs]} (vals @registry)
          :into [sidebar {:title contentsTitle}]]
     [:div.mt-1
      [:a.text-indigo-300.hover:text-white.font-light.block
       {:href (rfe/href :devdocs/collection {:collection id})} title]])
   [:div.overflow-y-auto.px-12.bg-white.flex-auto
    {:style {:padding-top 80 :padding-bottom 70}}
    [:h1.text-4xl.uppercase.tracking-wide.font-semibold.mb-10 contentsTitle]
    (for! [{:as collection
            :keys [id title devdocs clerk? cljs-eval?]} (vals @registry)
           :into [:div]]
      [:div.mb-10
       [:div
        [:h2.text-xl.uppercase.tracking-wide.font-bold
         [:a.hover:underline.text-indigo-900
          {:href (rfe/href :devdocs/collection {:collection id})} title]
         (when clerk?
           [:span.text-gray-500.border.border-indigo-100.rounded-full.bg-white.px-2.py-1.ml-2.relative
            {:style {:font-size 10 :top -4}}
            "Clerk"])
         (when cljs-eval?
           [:span.text-gray-500.border.border-indigo-100.rounded-full.bg-white.px-2.py-1.ml-2.relative
            {:style {:font-size 10 :top -4}}
            "Interaktiv"])]]
       (for! [{devdoc-id :id title :title :as devdoc} devdocs
              :into [:div]]
         [:div.mt-4.ml-4
          [:a.hover:underline.text-indigo-900.font-bold
           {:data-ids (pr-str {:collection id :devdoc devdoc-id})
            :href (rfe/href :devdocs/devdoc {:collection id :devdoc devdoc-id}) :title (:path devdoc)} title]
          (when-let [ts (:last-modified devdoc)]
            [:p.text-xs.text-gray-500.mt-1
             (-> ts
                 deja-fu/local-date-time
                 (deja-fu/format "MMM dd yyyy, HH:mm"))])])])]])

(defn breadcrumb [items]
  (into [:div.text-sm.mr-4]
        (->> items
             (map (fn [[title url]]
                    [:a.hover:underline {:href url} title]))
             (interpose [:span.text-gray-500 " / "]))))

;; :devdocs/collection
(defn collection-view [{:keys [collection]}]
  (let [{:keys [id title devdocs clerk? cljs-eval?] :as collection} (get @registry collection)]
    [:div.flex.h-screen.devdocs-body
     [sidebar {:title title
               :footer [:a.text-indigo-300.hover:text-white
                        {:href (rfe/href :devdocs/index)}
                        "← Back"]}
      [devdocs-toc (map #(update % :toc dissoc :children) devdocs)]]
     [:div.overflow-y-auto.px-12.bg-white.flex-auto
      {:style {:padding-top 80 :padding-bottom 70}}
      [:h1.text-4xl.uppercase.tracking-wide.font-semibold.mb-8
       [:a.hover:underline.text-indigo-900
        {:href (rfe/href :devdocs/collection {:collection id})} title]
       (when clerk?
         [:span.text-gray-500.border.border-indigo-100.rounded-full.bg-white.px-2.py-1.ml-2.relative
          {:style {:font-size 10 :top -9}}
          "Clerk"])
       (when cljs-eval?
         [:span.text-gray-500.border.border-indigo-100.rounded-full.bg-white.px-2.py-1.ml-2.relative
          {:style {:font-size 10 :top -9}}
          "Interaktiv"])]
      [:div
       (for! [{devdoc-id :id title :title :as devdoc} devdocs
              :into [:div]]
         [:div.mb-4
          [:a.hover:underline.text-indigo-900.font-bold
           {:href (rfe/href :devdocs/devdoc {:collection id :devdoc devdoc-id}) :title (:path devdoc)} title]
          (when-let [ts (:last-modified devdoc)]
            [:p.text-xs.text-gray-500.mt-1
             (-> ts
                 deja-fu/local-date-time
                 (deja-fu/format "MMM dd yyyy, HH:mm"))])])]]]))

(defn- return [pred]
  (fn [val] (when (pred val) val)))

;; :devdocs/devdoc
(defn devdoc-view [{:keys [collection devdoc fragment] :as data}]
  (let [{:keys [id title devdocs]} (get @registry collection)
        devdoc (some (return (comp #{devdoc} :id)) devdocs)]
    [:div.flex.h-screen.devdocs-body
     [sidebar {:title [:a.text-indigo-300.hover:text-white
                       {:href (rfe/href :devdocs/collection {:collection id})}
                       title]
               :footer [:a.text-indigo-300.hover:text-white
                        {:href (rfe/href :devdocs/collection {:collection id})}
                        "← Back"]}
      [devdocs-toc (map #(update % :toc dissoc :children) devdocs)]]
     [:div.overflow-y-auto.bg-white.flex-auto.relative
      (cond-> {:style {:padding-top 45 :padding-bottom 70}}
        fragment (assoc :ref #(scroll-to-fragment fragment)))
      [:div.absolute.right-0.top-0.p-4
       #_[breadcrumb [["Devdocs" (rfe/href :devdocs/index)]
                      [title (rfe/href :devdocs/collection {:collection collection})]
                      [(:title devdoc) (rfe/href :devdocs/devdoc {:collection collection :devdoc (:id devdoc)})]]]
       [:div.text-gray-400.text-xs.font-mono.float-right (:path devdoc)]]
      (:view devdoc)]]))

(defn devdoc-commands
  "For use with the commands/command-bar API"
  []
  {:subcommands
   (fn [context]
     (into [{:title "Index"
             :dispatch [:router/push [:devdocs/index]]}]
           (map (fn [{title :title coll-id :id devdocs :devdocs}]
                  {:title title
                   :subcommands
                   (into [{:title (str "-" (str/upper-case title) "-")
                           :dispatch [:router/push [:devdocs/collection {:collection coll-id}]]}]
                         (map (fn [{title :title devdoc-id :id}]
                                {:title title
                                 :dispatch [:router/push [:devdocs/devdoc {:collection coll-id
                                                                           :devdoc devdoc-id}]]}))
                         devdocs)}))
           (vals @registry)))})

(defn view-data
  "Get the view and and path-params data from a reitit match. Pass this to the
  view functions above."
  [match]
  (let [{:keys [data path-params]} match]
    (merge data path-params)))

(comment
  (first (vals @registry))

  (commands/register! :dev/devdocs
    (devdoc-commands)))
