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
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [re-frame.context :as re-frame :refer [defc]]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe])
  (:require-macros [nextjournal.devdocs]
                   [nextjournal.util.macros :refer [for!]]))

(goog-define contentsTitle "contents")
(goog-define logoImage "https://cdn.nextjournal.com/images/nextjournal-logo-white.svg")

(defonce registry (reagent/atom []))

;; TODO: maybe compile into reitit router
(defn find-coll [reg path]
  (when-some [{:as coll p :path} (some #(and (str/starts-with? path (:path %)) %) reg)]
    [(= path p) coll]))
(defn find-doc [{:keys [devdocs]} path] (some #(and (= path (:path %)) %) devdocs))
(defn lookup [registry path]
  (loop [r registry]
    (when-some [[exact-match? {:as coll :keys [collections]}] (find-coll r path)]
      (if exact-match?
        coll
        (or (when-some [doc (find-doc coll path)]
              (assoc doc :parent-collection coll))
            (when collections (recur collections)))))))

#_(lookup (dirs->config ["dev/ductile/insights" "notebooks"]) "notebooks")
#_(lookup (dirs->config ["dev/ductile/insights" "notebooks"]) "notebooks/import_manufacturers.clj")
#_(lookup (dirs->config ["dev/ductile/insights" "notebooks"]) "dev/ductile/insights/edi_history")
#_(lookup (dirs->config ["dev/ductile/insights" "notebooks"]) "dev/ductile/insights/edi_history/model_names.clj")

(defn scroll-to-fragment [el-id]
  (when-let [el (js/document.getElementById el-id)]
    (.scrollIntoView el)))

(defn inner-toc [coll-id devdoc-id entries]
  (for! [{:keys [title children]} entries  [:div]]
    [:div
     (when title
       (let [el-id (-> title
                       (str/replace #"`" "")
                       (str/replace #" " "-")
                       str/lower-case
                       js/encodeURIComponent)]
         ;; TODO: implement fragment nav based on Clerk's toc
         [:a.block.mb-1 {:href (rfe/href :devdocs/show {:collection coll-id
                                                        :devdoc devdoc-id
                                                        :fragment el-id})}
          title]
         (when (seq children)
           [inner-toc coll-id devdoc-id children])))]))

(defn devdocs-toc [{:keys [devdocs]} & [{:keys [inner?]}]]
  (for! [{:keys [title path toc]} devdocs
         :into [:<>]]
    [:div.mt-1
     [:a.hover:text-gray-400
      {:href (rfe/href :devdocs/show {:path path})} title]
     (when inner? ;; TODO: conform into Clerk's toc
       [inner-toc path path (:children toc)])]))

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
  [:div.px-12.py-6.overflow-y-auto.flex-shrink-0.sidebar-content.text-white
   (if @mobile?
     {:class "fixed left-0 top-0 right-0 bottom-0 z-10" :style {:background-color "rgba(31, 41, 55, 1.000)"}}
     {:style {:width 330 :background-color "rgba(31, 41, 55, 1.000)"}
      :class (str "relative " (when @collapsed? "rounded"))})
   [:div.absolute.left-0.top-0.w-full.p-4.cursor-pointer.flex.items-center.group.hover:text-gray-400.hover:bg-black.hover:bg-opacity-25.transition-all
    {:on-click #(swap! collapsed? not)}
    (if @collapsed?
      [icon/chevron-double-right {:size 14}]
      [icon/chevron-double-left {:size 14}])
    [:span.text-xs.font-light.opacity-0.group-hover:opacity-100.transition-all
     {:style {:margin-left 13}}
     (str (if @collapsed?
            (if @mobile? "Show" "Pin")
            (if @mobile? "Hide" "Unpin"))
          " sidebar")]]
   [:a.block.mt-12.logo
    {:href (rfe/href :devdocs/show {:path ""})}
    [:img {:src logoImage :width "100%" :style {:max-width 235}}]]
   [:div.mt-12.pb-2.border-b.mb-2.uppercase.tracking-wide.text-base.md:text-sm
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
          [icon/menu]
          [:div.fixed.top-0.left-0.bottom-0.z-20.p-4.collapsed-sidebar
           {:class (if @mobile?
                     (str "mobile-sidebar " (if @collapsed? "hidden" "flex"))
                     "flex")}
           (into [sidebar-content options] content)]]
         (into [sidebar-content options] content))])))

;; :devdocs/index
(declare index-view* index-view**)
(defn index-view [_] [index-view* @registry])
(defn index-view* [collections]
  [:div.flex.h-screen.devdocs-body
   (for! [{:keys [title path]} collections
          :into [sidebar {:title contentsTitle}]]
     [:div.mt-1
      [:a.hover:text-gray-400.font-light.block
       {:href (rfe/href :devdocs/show {:path path})} title]])
   [:div.overflow-y-auto.px-12.bg-white.flex-auto
    {:style {:padding-top 80 :padding-bottom 70}}
    [:h1.text-4xl.uppercase.tracking-wide.font-semibold.mb-10 contentsTitle]
    [index-view** collections]]])
(defn index-view** [collections]
  (for! [{:keys [title path devdocs] sub-colls :collections} collections
         :into [:div]]
    [:div.mb-10
     [:div
      [:h2.text-xl.uppercase.tracking-wide.font-bold
       [:a.hover:underline
        {:href (rfe/href :devdocs/show {:path path})} title]]]
     (for! [{:keys [title path last-modified]} devdocs
            :into [:div]]
       [:div.mt-4.ml-4
        [:a.hover:underline.font-bold
         {:href (rfe/href :devdocs/show {:path path})
          :title path} title]
        (when last-modified
          [:p.text-xs.text-gray-500.mt-1
           (-> last-modified
               deja-fu/local-date-time
               (deja-fu/format "MMM dd yyyy, HH:mm"))])])
     (when (seq sub-colls)
       [:div.mt-4.ml-4
        [index-view** sub-colls]])]))

(defn breadcrumb [items]
  (into [:div.text-sm.mr-4]
        (->> items
             (map (fn [[title url]]
                    [:a.hover:underline {:href url} title]))
             (interpose [:span.text-gray-500 " / "]))))

;; :devdocs/collection
(defn collection-view [collection]
  (let [{:keys [path title devdocs]} collection]
    [:div.flex.h-screen.devdocs-body
     [sidebar {:title title
               :footer [:a.hover:text-gray-400
                        {:href (rfe/href :devdocs/show {:path ""})}
                        "← Back"]}
      [devdocs-toc collection]]
     [:div.overflow-y-auto.px-12.bg-white.flex-auto
      {:style {:padding-top 80 :padding-bottom 70}}
      [:h1.text-4xl.uppercase.tracking-wide.font-semibold.mb-8
       [:a.hover:underline
        {:href (rfe/href :devdocs/show {:path path})} title]]
      [:div
       (for! [{:keys [title path last-modified]} devdocs :into [:div]]
         [:div.mb-4
          [:a.hover:underline.font-bold
           {:href (rfe/href :devdocs/show {:path path}) :title path} title]
          (when last-modified
            [:p.text-xs.text-gray-500.mt-1
             (-> last-modified
                 deja-fu/local-date-time
                 (deja-fu/format "MMM dd yyyy, HH:mm"))])])]]]))

(defn- return [pred]
  (fn [val] (when (pred val) val)))

;; :devdocs/devdoc
(defn devdoc-view [{:keys [parent-collection edn-doc fragment]}]
  (let [{:keys [path title]} parent-collection]
    [:div.flex.h-screen.devdocs-body
     [sidebar {:title [:a.hover:text-white {:href (rfe/href :devdocs/show {:path path})} title]
               :footer [:a.hover:text-white {:href (rfe/href :devdocs/show {:path path})} "← Back"]}
      [devdocs-toc parent-collection]]
     [:div.overflow-y-auto.bg-white.flex-auto.relative
      (cond-> {:style {:padding-top 45 :padding-bottom 70}}
        fragment (assoc :ref #(scroll-to-fragment fragment)))
      [:div.absolute.right-0.top-0.p-4
       [:div.text-gray-400.text-xs.font-mono.float-right path]]
      [sci-viewer/inspect (try
                            (sci-viewer/read-string edn-doc)
                            (catch :default e
                              (js/console.error :clerk.sci-viewer/read-error e)
                              "Parse error..."))]]]))

(defn view [{:as data :keys [path]}]
  (if (or (nil? path) (contains? #{"" "/"} path))
    [index-view* @registry]
    (let [{:as node :keys [devdocs edn-doc]} (lookup @registry path)]
      (js/console.log :data data :node node
                      :coll? (some? devdocs)
                      :doc? (some? edn-doc)
                      :parent (when (some? edn-doc) (:parent-collection node)))
      (cond
        devdocs [collection-view node]
        edn-doc [devdoc-view node]))))

(defn devdoc-commands
  "For use with the commands/command-bar API"
  ([] (devdoc-commands @registry))
  ([collections]
   {:subcommands
    (fn [_]
      (into [{:title "Index"
              :dispatch [:router/push [:devdocs/show]]}]
            (map (fn collection->commands [{:keys [title path devdocs collections]}]
                   {:title title
                    :subcommands
                    (-> [{:title (str "-" (str/upper-case title) "-")
                          :dispatch [:router/push [:devdocs/show {:path path}]]}]
                        (into (map (fn [{:keys [title path]}]
                                     {:title title
                                      :dispatch [:router/push [:devdocs/show {:path path}]]}))
                              devdocs)
                        (cond-> collections
                          (into (map collection->commands) collections)))}))
            collections))}))

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
