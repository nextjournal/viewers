(ns examples
  (:require [clojure.string :as str]
            [nextjournal.devcards.routes :as devcards.routes]
            [nextjournal.devdocs.demo :as devdocs.demo]
            [nextjournal.ui.components.icon :as icon]
            [reagent.dom :as rdom]
            [reagent.core :as reagent]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defonce match (reagent/atom nil))

(def routes
  [["/"           {:name ::home}]
   ["/devdocs/*"  {:name ::devdocs  :router devdocs.demo/router    :view devdocs.demo/view}]
   ["/devcards/*" {:name ::devcards :router devcards.routes/router :view devcards.routes/view}]])

(defn view []
  (let [{:keys [data]} @match
        {:keys [view submatch]} data]
    (if (and view submatch)
      [:<>
       [view submatch]
       [:div {:style {:position "absolute" :bottom 0 :left 0 :margin ".2rem" :z-index 999}}
        [:a.underline.sans-serif.text-tiny.text-grey-800.px-8.border-grey-300 {:href "#/"} "viewers home"]]]
      ;; else home:
      [:div.bg-gray-200.w-screen.min-h-screen.flex.items-center.justify-center.relative.sans-serif
       [:div
        [:div.px-8.py-6
         [:img.inline-block {:src "https://cdn.nextjournal.com/images/nextjournal-logo.svg" :width 260}]]
        [:div.bg-white.shadow-lg.rounded-lg.overflow-hidden.border.border-gray-300.border-b-0
         [:ul.text-2xl
          (for [[route title] [[:devcards/root "Devcards"]
                               [:devdocs/index "Devdocs"]]]
            [:li
             [:a.text-gray-800.hover:text-indigo-600.block.px-8.justify-between.py-4.text-xl.border-b.border-gray-300.flex.items-center.bg-white-50.hover:bg-indigo-100.transition-all.ease-in-out.duration-150
              {:href (rfe/href route)}
              title
              [:span.ml-2.opacity-50 "▶"]]])]]]])))

(def router
  (let [router (rf/router routes)]
    (reify r/Router
      (match-by-path [_ path] (let [{:as match :keys [path data]} (r/match-by-path router path)
                                    submatch (when (and path (:router data))
                                               (r/match-by-path (:router data) (str/replace path #"^/[^/]+" "")))]
                                 (cond-> match submatch (assoc-in [:data :submatch] submatch))))
      (match-by-name [this name] (r/match-by-name this name {}))
      (match-by-name [_ name params] (->> routes
                                          (some (fn [[prefix {r :router}]]
                                                  (when-let [m (and r (r/match-by-name r name params))]
                                                    (update m :path #(str (str/replace prefix #"/\*$" "") %))))))))))

(defn ^:export ^:dev/after-load init []
  (rfe/start! router #(reset! match %1) {:use-fragment true})
  (rdom/render [view] (js/document.getElementById "app")))
