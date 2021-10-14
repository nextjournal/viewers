(ns examples.main
  (:require [clojure.string :as str]
            [nextjournal.devcards.routes :as devcards.routes]
            [nextjournal.devdocs.routes :as devdocs.routes]
            [nextjournal.devdocs.demo :as devdocs.demo]
            [reagent.dom :as rdom]
            [reitit.core :as r]
            [reitit.frontend :as rf]
            [reitit.frontend.history :as rfh]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe]))

(defonce match (reagent/atom nil))

(def routes
  {::devdocs {:view devdocs.demo/view
              :router devdocs.demo/router}
   ::devcards {:view devcards.routes/view
               :router devcards.routes/router}})

(defn main []
  (let [{:keys [path data] :as m} @match
        {:keys [name router]} data

        _ (js/console.log :name name
                          :router router
                          :path path
                          :sub-path (when path (str/replace path #"^/[^/]+" ""))
                          :sub-match (when (and path name router)
                                       (r/match-by-path router (str/replace path #"^/[^/]+" ""))))

        submatch (when (and path name router)
                   (r/match-by-path router (str/replace path #"^/[^/]+" "")))
        view (when name (-> routes name :view))]
    (if (and submatch view)
      [view submatch]
      [:<>
       [:h1.flex "👋Nextjournal Viewers Examples"]
       [:ul
        [:a {:href "#/devdocs/docs/"} "Devdocs"]
        [:a {:href "#/devcards/"} "Devcards"]]])))




;; # markdon
;; - llakjdf

(def router

  (rf/router

   ;; nested devcards and devdocs routes
   [["/" {:name ::home}]
    ["/devdocs/*" {:name ::devdocs :router (-> routes ::devdocs :router)}]
    ["/devcards/*" {:name ::devcards :router (-> routes ::devcards :router)}]]


   #_ ;; route linear merge
   [
    (into ["/cards"] (r/routes devcards.routes/router))     ;; fix card hrefs
    devdocs.routes/routes]
   ))


(defn ^:export ^:dev/after-load init []
  (rfe/start! router
              #(do
                 (reset! match %1)
                 (js/console.log :M %1)

                 #_
                 (js/console.log :name (-> %1 :data :name)
                                 :R (:router @rfe/history)
                                 :SR (get-in routes [(-> %1 :data :name) :router]))
                 ;; this makes rfe/href work, but routing is broken


                 (when-some [r (get-in routes [(-> %1 :data :name) :router])]
                   (swap! rfe/history assoc :router r)
                   #_
                   (rfe/start! r
                               (constantly true)
                               {:use-fragment true})))
              {:use-fragment true})
  ;;(rfe/push-state "/")
  (rdom/render [main] (js/document.getElementById "app"))
  ;;(rfe/push-state "/devcards/index")
  ;;(rdom/render [devdocs.demo/main] (js/document.getElementById "app"))

  )


(comment
  (concat
   (into ["/cards"] (r/routes devcards.routes/router))
   devdocs.routes/routes)


  @rfe/history
  (js/console.log :r devdocs.demo/router)
  (str/replace "/foo/bar/dang/" #"^/[^/]+" "")

  (str/join (interpose "/" ["a" "b" ""]))
  (r/routes router)

  (js/console.log :ahoi)
  (r/match-by-path devcards.routes/router "/nextjournal.viewer")
  (r/match-by-path devdocs.demo/router "/docs/")

  (r/routes devdocs.demo/router)
  (r/routes devdocs.demo/router)
  (r/routes devcards.routes/router)
  (r/router
   (concat (r/routes devdocs.demo/router)
           (r/routes devcards.routes/router)))
  )
