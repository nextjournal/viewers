(ns nextjournal.devdocs.routes
  #?(:cljs (:require [nextjournal.devdocs :as devdocs])))

(def routes
  ["/docs"
   ["/"
    {:name :devdocs/index
     :view :devdocs/index}]
   ["/:collection"
    {:name :devdocs/collection
     :view :devdocs/collection}]
   ["/:collection/:devdoc"
    {:name :devdocs/devdoc
     :view :devdocs/devdoc}]
   ["/:collection/:devdoc/:fragment"
    {:name :devdocs/fragment
     :view :devdocs/fragment}]])

(def views
  #?(:cljs
     {:devdocs/index devdocs/index-view,
      :devdocs/collection devdocs/collection-view,
      :devdocs/devdoc devdocs/devdoc-view,
      :devdocs/fragment devdocs/devdoc-view}))
