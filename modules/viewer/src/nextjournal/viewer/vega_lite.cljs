(ns nextjournal.viewer.vega-lite
  (:require [applied-science.js-interop :as j]
            [nextjournal.ui.components.d3-require :as d3-require]))

(defn viewer [value]
  (when value
    ^{:nextjournal/viewer :reagent}
    [d3-require/with {:package ["vega-embed@6.11.1"]}
     (j/fn [^:js {:keys [embed]}]
       [:div {:style {:overflow-x "auto"}}
        [:div.vega-lite {:ref #(when % (embed % (clj->js value)))}]])]))
