(ns examples.main
  (:require [clojure.string :as str]
            [nextjournal.devcards.routes :as devcards.routes]
            [nextjournal.devdocs.demo :as devdocs.demo]
            [reagent.dom :as rdom]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe]))

(defonce match (reagent/atom nil))

(def routes
  [["/"           {:name ::home}]
   ["/devdocs/*"  {:name ::devdocs  :router devdocs.demo/router    :view devdocs.demo/view}]
   ["/devcards/*" {:name ::devcards :router devcards.routes/router :view devcards.routes/view}]
   ])

(defn main []
  (let [{:keys [data]} @match
        {:keys [submatch view]} data]
    (if (and submatch view)
      [view submatch]
      [:<>
       [:h1.flex "👋Nextjournal Viewers Examples"]
       [:ul
        [:a {:href "#/devdocs/docs/"} "Devdocs"]
        [:a {:href "#/devcards/"} "Devcards"]]])))

(def router
  "nested router composing examples"
  (let [router (rf/router routes)]
    (reify r/Router
      (match-by-path [_ path] (let [{:as m :keys [path data]} (r/match-by-path router path)
                                    submatch (when (and path (:router data))
                                               (r/match-by-path (:router data) (str/replace path #"^/[^/]+" "")))]
                                 (js/console.log :MBP path :M m :SM submatch)
                                 (cond-> m submatch (assoc-in [:data :submatch] submatch))))
      (match-by-name [this name] (r/match-by-name this name {}))
      (match-by-name [_ name params] (let [m (->> routes
                                                  (some (fn [[prefix {r :router}]]
                                                          (when-let [m (and r (r/match-by-name r name params))]
                                                            (update m :path #(str (str/replace prefix #"/\*$" "") %))))))]
                                       (js/console.log :MBN name :Params params :MP (:path m))
                                       m)))))

(defn ^:export ^:dev/after-load init []
  (rfe/start! router #(reset! match %1) {:use-fragment true})
  (rdom/render [main] (js/document.getElementById "app")))
