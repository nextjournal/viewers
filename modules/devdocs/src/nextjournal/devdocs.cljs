(ns nextjournal.devdocs
  "Views for devdocs:
  * a collection view
  * a document view

  The registry -- holding document and navigation structure -- is a nested collection (a map) of
  * `:devdocs` a sequence of clerk docs
  * `:collections` a sequence of sub-collections."
  (:require [clojure.string :as str]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.navbar :as navbar]
            [nextjournal.ui.components.localstorage :as ls]
            [lambdaisland.deja-fu :as deja-fu]
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe])
  (:require-macros [nextjournal.util.macros :refer [for!]]))

(goog-define contentsTitle "contents")
(goog-define logoImage "https://cdn.nextjournal.com/images/nextjournal-logo-white.svg")

(defonce registry (reagent/atom []))

;; TODO: maybe compile into reitit router
(defn find-coll [reg path] (some #(and (str/starts-with? path (:path %)) %) (:items reg)))
(defn find-doc [{:keys [items]} path] (some #(and (= path (:path %)) %) items))
(defn lookup [registry path]
  (or (find-doc registry path)
      (loop [r registry]
        (when-some [{:as coll :keys [items]} (find-coll r path)]
          (or (find-doc coll path)
              (when items (recur coll)))))))

#_(lookup @registry "README.md")
#_(lookup @registry "docs/reference.md")
#_(lookup @registry "docs/clerk")
#_(lookup @registry "docs/clerk/clerk.clj")

(defn scroll-to-fragment [el-id]
  (when-let [el (js/document.getElementById el-id)]
    (.scrollIntoView el)))

(declare collection-inner-view)

(defn item-view [{:as item :keys [title edn-doc path last-modified items]}]
  [:div
   (cond
     edn-doc                                                ;; doc
     [:div.mb-2
      [:a.hover:underline.font-bold
       {:href (when edn-doc (rfe/href :devdocs/show {:path path})) :title path}
       title]
      (when last-modified
        [:p.text-xs.text-gray-500.mt-1
         (-> last-modified
             deja-fu/local-date-time
             (deja-fu/format "MMM dd yyyy, HH:mm"))])]
     (seq items)                                            ;; collection
     [collection-inner-view item])])

(defn collection-inner-view [{:keys [title items level]}]
  [:div
   {:class (when-not title "pt-8")}
   (when title
     [:h3 {:style {:margin-top "2rem" :margin-bottom "1rem"}} title])
   (into [:div]
         (for! [item items] [item-view item]))])

(defn collection-view [collection]
  [:div.overflow-y-auto.bg-white.flex-auto.pb-12.font-sans
   [:div.w-full.max-w-prose.px-8.mx-auto
    [:h1.pt-8 "Devdocs"]
    [collection-inner-view collection]]])

(defn devdoc-view [{:as doc :keys [edn-doc fragment]}]
  [:div.overflow-y-auto.bg-white.flex-auto.relative.font-sans
   (cond-> {:style {:padding-top 45 :padding-bottom 70}}
           fragment (assoc :ref #(scroll-to-fragment fragment)))
   [:div.absolute.left-4.md:right-0.md:left-auto.top-0.p-4
    [:div.text-gray-400.text-xs.font-mono.float-right (:path doc)]]
   [sci-viewer/inspect (try
                         (sci-viewer/read-string edn-doc)
                         (catch :default e
                           (js/console.error :clerk.sci-viewer/read-error e)
                           "Parse error..."))]])

(defn navbar-items [items]
  (mapv (fn [{:as item :keys [items path]}]
          (assoc item :expanded? true
                      :path (rfe/href :devdocs/show {:path path})
                      :items (navbar-items items))) items))

(def local-storage-key "devdocs-navbar")
(defn navbar-state [{:as _registry :keys [items] :navbar/keys [theme]}]
  {:items (navbar-items items)
   :theme (merge {:slide-over "bg-slate-100 font-sans border-r"} theme)
   :width 220
   :mobile-width 300
   :local-storage-key local-storage-key
   :open? (ls/get-item local-storage-key)})

(defn view [{:as data :keys [path] }]
  (reagent/with-let [!state (reagent/atom (navbar-state @registry))]
    [:div.flex.h-screen
     [navbar/toggle-button !state
      [:<>
       [icon/menu {:size 20}]
       [:span.uppercase.tracking-wider.ml-1.font-bold
        {:class "text-[12px]"} "Nav"]]
      {:class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-3 text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 shadow md:shadow-none"}]
     [navbar/panel !state [navbar/navbar !state]]
     (if (or (nil? path) (contains? #{"" "/"} path))
       [collection-view @registry]
       (let [{:as node :keys [edn-doc]} (lookup @registry path)]
         (when edn-doc [devdoc-view node])))]))

(defn devdoc-commands
  "For use with the commands/command-bar API"
  ([] (devdoc-commands @registry))
  ([{:keys [items]}]
   (letfn [(item->command [{:keys [title path items]}]
             (cond-> {:title title}
               (not items) (assoc :dispatch [:router/push [:devdocs/show {:path path}]])
               items (assoc :subcommands (mapv item->command items))))]
     {:subcommands (-> [{:title "Index" :dispatch [:router/push [:devdocs/show {:path ""}]]}]
                       (into (map item->command) items))})))

(defn view-data
  "Get the view and and path-params data from a reitit match. Pass this to the
  view functions above."
  [match]
  (let [{:keys [data path-params]} match]
    (merge data path-params)))
