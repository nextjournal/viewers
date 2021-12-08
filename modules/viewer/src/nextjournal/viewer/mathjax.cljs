(ns nextjournal.viewer.mathjax
  (:require [applied-science.js-interop :as j]
            ["d3-require" :as d3]
            [nextjournal.util.cljs-extensions]
            [reagent.core :as r]))

(def output-format
  "the output format to use. Either `:svg` or `:html`. See
  http://docs.mathjax.org/en/latest/output/index.html for a discussion of
  tradeoffs. `:html` is currently not showing up."
  :svg)

;; generated from https://nextjournal.com/a/NSFQvyN7UnNdGKoc3QMC9
(def bundle-url
  {:html "https://run.nextjournalusercontent.com/data/QmaH3kKYJoHuVwUx7MYmxRFdFdvEyeJcGYFWChB3UUeDT3?filename=es5/tex-chtml-full.js&content-type=application/javascript"
   :svg "https://run.nextjournalusercontent.com/data/QmQadTUYtF4JjbwhUFzQy9BQiK52ace3KqVHreUqL7ohoZ?filename=es5/tex-svg-full.js&content-type=application/javascript"})

(defn viewer* [value {:as _opts :keys [inline?] :or {inline? false}}]
  (r/with-let [!el (r/atom nil)
               !mathjax (r/atom nil)]
    (-> (d3/require (bundle-url output-format))
        (j/call :then #(reset! !mathjax (.-MathJax js/window))))
    (when (and @!el @!mathjax value)
      (let [r (case output-format
                :svg (.tex2svg ^js @!mathjax value #js {:display (not inline?)})
                :html (.tex2chtml ^js @!mathjax value))]
        (if-let [c (.-firstChild @!el)]
          (.replaceChild @!el r c)
          (.appendChild @!el r))))
    [:span {:ref (fn [el] (when el (reset! !el el)))}]))

(defn viewer
  ([value] (viewer value {}))
  ([value opts]
   ^{:nextjournal/viewer :reagent} [viewer* value opts]))
