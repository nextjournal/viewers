(ns nextjournal.devdocs
  "Views for devdocs:
  * a collection view
  * a document view

  The registry -- holding document and navigation structure -- is a nested collection (a map) of
  * `:devdocs` a sequence of clerk docs
  * `:collections` a sequence of sub-collections."
  (:require [clojure.string :as str]
            [nextjournal.ui.components.icon :as icon]
            [lambdaisland.deja-fu :as deja-fu]
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe])
  (:require-macros [nextjournal.util.macros :refer [for!]]))

(goog-define contentsTitle "contents")
(goog-define logoImage "https://cdn.nextjournal.com/images/nextjournal-logo-white.svg")

(defonce registry (reagent/atom []))

;; TODO: maybe compile into reitit router
(defn find-coll [reg path]
  (when-some [{:as coll p :path} (some #(and (str/starts-with? path (:path %)) %) (:collections reg))]
    [(= path p) coll]))
(defn find-doc [{:keys [devdocs]} path] (some #(and (= path (:path %)) %) devdocs))
(defn lookup [registry path]
  (or (when-some [doc (find-doc registry path)]
        (assoc doc :parent-collection registry))
      (loop [r registry]
        (when-some [[exact-match? {:as coll :keys [collections]}] (find-coll r path)]
          (if exact-match?
            (assoc coll :parent-collection r)
            (or (when-some [doc (find-doc coll path)]
                  (assoc doc :parent-collection coll))
                (when collections
                  (recur coll))))))))

#_(lookup @registry "README.md")
#_(lookup @registry "docs/reference.md")
#_(lookup @registry "docs/clerk")
#_(lookup @registry "docs/clerk/clerk.clj")

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

(defn collection-toc [{:keys [title path collections]}]
  [:<>
   [:a.block.hover:text-gray-400 {:href (rfe/href :devdocs/show {:path path})}
    (str "📁 " title)]
   (when (seq collections)
     (for! [c collections :into [:div.mt-1.ml-2]]
       [collection-toc c]))])

(defn devdocs-toc [{:keys [devdocs collections]} & [{:keys [inner?]}]]
  [:<>
   (for! [{:keys [title path toc]} devdocs
          :into [:<>]]
     [:div.mt-1
      [:a.hover:text-gray-400
       {:href (rfe/href :devdocs/show {:path path})} title]
      (when inner?                                          ;; TODO: conform into Clerk's toc
        [inner-toc path path (:children toc)])])
   (when (seq collections)
     (for! [c collections :into [:div.mt-2]]
       [collection-toc c]))])

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

(declare collection-inner-view)
(defn collection-view [{:as collection :keys [title parent-collection]}]
  [:div.flex.h-screen.devdocs-body
   [sidebar {:title title
             :footer [:a.hover:text-gray-400
                      {:href (rfe/href :devdocs/show {:path (or (:path parent-collection) "")})}
                      "← Back"]}
    [devdocs-toc collection]]
   [:div.overflow-y-auto.px-12.bg-white.flex-auto
    {:style {:padding-top 80 :padding-bottom 70}}
    [collection-inner-view collection]]])
(defn collection-inner-view [{:keys [path title devdocs collections level] :or {level 1}}]
  [:div
   [(str "h" level ".uppercase.tracking-wide.font-semibold.mb-2")
    [:a.hover:underline {:href (rfe/href :devdocs/show {:path path})} title]]
   (for! [{:keys [title path last-modified]} devdocs :into [:div]]
     [:div.mb-2.ml-2
      [:a.hover:underline.font-bold
       {:href (rfe/href :devdocs/show {:path path}) :title path} title]
      (when last-modified
        [:p.text-xs.text-gray-500.mt-1
         (-> last-modified
             deja-fu/local-date-time
             (deja-fu/format "MMM dd yyyy, HH:mm"))])])
   (when (seq collections)
     (for! [coll collections :into [:div.ml-2.mt-4]]
       [collection-inner-view (assoc coll :level (inc level))]))])

(defn devdoc-view [{:as doc :keys [parent-collection edn-doc fragment]}]
  (let [{:keys [path title]} parent-collection]
    [:div.flex.h-screen.devdocs-body
     [sidebar {:title [:a.hover:text-white {:href (rfe/href :devdocs/show {:path path})} title]
               :footer [:a.hover:text-white {:href (rfe/href :devdocs/show {:path path})} "← Back"]}
      [devdocs-toc parent-collection]]
     [:div.overflow-y-auto.bg-white.flex-auto.relative
      (cond-> {:style {:padding-top 45 :padding-bottom 70}}
        fragment (assoc :ref #(scroll-to-fragment fragment)))
      [:div.absolute.right-0.top-0.p-4
       [:div.text-gray-400.text-xs.font-mono.float-right (:path doc)]]
      [sci-viewer/inspect (try
                            (sci-viewer/read-string edn-doc)
                            (catch :default e
                              (js/console.error :clerk.sci-viewer/read-error e)
                              "Parse error..."))]]]))

(defn view [{:as data :keys [path]}]
  (if (or (nil? path) (contains? #{"" "/"} path))
    [collection-view @registry]
    (let [{:as node :keys [devdocs edn-doc]} (lookup @registry path)]
      (js/console.log :data data :node node
                      :coll? (some? devdocs)
                      :doc? (some? edn-doc)
                      :parent (:parent-collection node))
      (cond
        devdocs [collection-view node]
        edn-doc [devdoc-view node]))))

(defn devdoc-commands
  "For use with the commands/command-bar API"
  ([] (devdoc-commands @registry))
  ([{:keys [devdocs collections]}]
   (letfn [(doc->command [{:keys [title path]}] {:title title :dispatch [:router/push [:devdocs/show {:path path}]]})
           (collection->commands [{:keys [title path devdocs collections]}]
             {:title title
              :subcommands (-> [{:title (str "-" (str/upper-case title) "-")
                                 :dispatch [:router/push [:devdocs/show {:path path}]]}]
                               (into (map doc->command) devdocs)
                               (into (map collection->commands) collections))})]
     {:subcommands (-> [{:title "Index"
                         :dispatch [:router/push [:devdocs/show {:path ""}]]}]
                       (into (map doc->command) devdocs)
                       (into (map collection->commands) collections))})))

(defn view-data
  "Get the view and and path-params data from a reitit match. Pass this to the
  view functions above."
  [match]
  (let [{:keys [data path-params]} match]
    (merge data path-params)))
