(ns nextjournal.viewer.plotly
  (:require [applied-science.js-interop :as j]
            [goog.functions :as gf]
            [nextjournal.log :as log]
            [nextjournal.ui.components.d3-require :as d3-require]
            [reagent.core :as r]))

(def default-layout
  (cond-> {"font" {"family" "'Verlag A', 'Verlag B', -apple-system, '.SFNSText-Regular', 'San Francisco', 'Roboto', 'Segoe UI', 'Helvetica Neue', 'Arial', sans-serif"
                   "size" 14
                   "color" "#343434"}
           "height" 450
           "autosize" false}
    (and (exists? js/window) (.hasOwnProperty js/window "ontouchstart"))
    (assoc "dragmode" false)))

(def default-graph-options #js {:displayModeBar false
                                :displayLogo false})

(def default-min-margin {"r" 0 "l" 30 "b" 0 "t" 20})

(defn adjust-layout-margins [layout]
  (update layout "margin"
          (fn [margin]
            (merge-with max
                        default-min-margin
                        margin
                        (when (get layout "title")
                          {"t" 60})))))

(defn deep-merge-maps
  "Like merge, but merges maps recursively."
  [m1 m2]
  (if (and (map? m1) (map? m2))
    (merge-with deep-merge-maps m1 m2)
    m2))

(defn graph-layout-with-defaults [layout]
  (let [layout (js->clj layout)
        layout (if (empty? layout) {} layout)]
    (-> (deep-merge-maps default-layout layout)
        adjust-layout-margins
        clj->js)))

(defn coerce-val [value]
  (cond
    (string? value)
    (.parse js/JSON value)
    (map? value)
    (clj->js value)
    :else
    value))

(defn viewer* [value]
  (let [this (r/current-component)]
    [d3-require/with {:package "plotly.js-dist@1.51.1"}
     (j/fn [^:js {:as Plotly :keys [relayout Plots]}]
       [:div.code-plotly-result
        {:class "flex justify-center items-center"
         :ref (fn [^js plotly-el]
                (if plotly-el
                  (j/let [^:js {:keys [layout] :as coerced} (coerce-val value)
                          value-object (-> coerced
                                           (j/select-keys [:data :frames])
                                           (j/assoc! :layout (graph-layout-with-defaults layout)
                                                     :config default-graph-options))]
                    (j/assoc! this :resize-listener
                              (gf/debounce
                               (fn []
                                 (relayout plotly-el (clj->js (graph-layout-with-defaults layout)))
                                 (-> Plots (.resize plotly-el)))
                               100))
                    (-> Plotly
                        (j/call :newPlot
                                plotly-el
                                value-object)
                        (j/call :catch
                                (fn new-plot-error [reason]
                                  (log/error ::insert-plot "Plotly Error" :el plotly-el :reason reason))))
                    (.addEventListener js/window "resize" (j/get this :resize-listener)))
                  (.removeEventListener js/window "resize" (j/get this :resize-listener))))}])]))

;; wrapper is used so that `viewer*` is not called as a function - it needs its own reagent component
(defn viewer [value]
  ^{:nextjournal/viewer :reagent} [viewer* value])
