(ns examples
  (:require [clojure.string :as str]
            [nextjournal.devcards.routes :as devcards.routes]
            [nextjournal.devdocs.demo :as devdocs.demo]
            [nextjournal.devcards-ui :as devcards-ui]
            [nextjournal.clerk.sci-viewer :as clerk-sci-viewer]
            [nextjournal.clerk-sci-env]
            [nextjournal.clerk.static-app :as clerk-static-app]
            [nextjournal.ui.components.icon :as icon]
            [reagent.dom :as rdom]
            [reagent.core :as reagent]
            [nextjournal.commands.command-bar :as command-bar]
            [nextjournal.commands.state :as commands.state]
            [nextjournal.commands.core :as commands]
            [re-frame.context :as re-frame]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(re-frame/reg-event-fx
 :init-commands
 (fn [{:keys [db]} [_ commands-ctx]]
   {:db (merge db commands-ctx)}))

(defonce match (reagent/atom nil))

(def routes
  [["/"           {:name ::home}]
   ["/clerk/*"    {:name ::clerk    :router clerk-static-app/router :view clerk-static-app/root}]
   ["/devdocs/*"  {:name ::devdocs  :router devdocs.demo/router     :view devdocs.demo/view}]
   ["/devcards/*" {:name ::devcards :router devcards.routes/router  :view devcards.routes/view}]])

(commands/register! :go-to/home
                    {:title "Viewers Home"
                     :keys "Alt-H"
                     :dispatch [:router/push [::home]]})

(def commands-config
  {:categories [:go-to :dev]
   :shortcuts {:devcards/actions
               {:commands [:dev/devcards :dev/docs]}}})

(defn view []
  (let [{:keys [name data]} @match
        {:keys [view submatch]} data]
    [:<>
     [:div.fixed.bottom-0.left-0.right-0.z-20
      [command-bar/view commands-config]]
     (if (and view submatch)
       [view submatch]
       ;; else home:
       [:div.bg-gray-200.w-screen.min-h-screen.flex.items-center.justify-center.relative.sans-serif
        [:div
         [:div.px-8.py-6
          [:img.inline-block {:src "https://cdn.nextjournal.com/images/nextjournal-logo.svg" :width 260}]]
         [:div.bg-white.shadow-md.rounded-md.overflow-hidden.border.border-gray-300.border-b-0
          [:ul.text-2xl
           (for [[route title] [[:devcards/root "Devcards"]
                                [:devdocs/index "Devdocs"]]]
             ^{:key title}
             [:li
              [:a.text-gray-800.block.px-8.justify-between.py-4.text-lg.border-b.border-gray-300.flex.items-center.bg-white-50.hover:bg-indigo-50.transition-all.ease-in-out.duration-150
               {:href (rfe/href route)}
               title
               [icon/chevron-right {:size 20}]]])]]]])])) ;; FIXME: using ui.components.icon not working

(def router
  (let [router (rf/router routes)]
    (reify r/Router
      (match-by-path [_ path] (let [{:as match :keys [path data]} (r/match-by-path router path)
                                    submatch (when (and path (:router data))
                                               (r/match-by-path (:router data) (str/replace path #"^/[^/]+" "")))]
                                 (cond-> match submatch (assoc-in [:data :submatch] submatch))))
      (match-by-name [this name] (r/match-by-name this name {}))
      (match-by-name [_ name params]
        (or (->> routes
                 (some (fn [[prefix {r :router}]]
                         (when-let [m (and r (r/match-by-name r name params))]
                           (update m :path #(str (str/replace prefix #"/\*$" "") %))))))
            (r/match-by-name router name params))))))

(defn ^:export ^:dev/after-load init []
  (re-frame/dispatch-sync [:init-commands (commands.state/empty-db!)])
  (rfe/start! router #(reset! match %1) {:use-fragment true})
  (rdom/render [view] (js/document.getElementById "app")))
