(ns nextjournal.ui.components.d3-require
  (:require ["d3-require" :as d3]
            [applied-science.js-interop :as j]
            [reagent.core :as r]))

(defn with [{:keys [package loading-view]
                     :or {loading-view "Loading..."}} f]
  (r/with-let [!package (r/atom nil)]
    (case @!package
      nil (do
            (reset! !package :loading)
            (-> (if (string? package)
                  (d3/require package)
                  (apply d3/require package))
                (j/call :then #(reset! !package %)))
            loading-view)
      :loading loading-view
      (into (f @!package)))))

(comment
 ;; Usage
 [with {:package ["vega-embed@6.11.1"]}
  (j/fn [^:js {:keys [embed]}]
    [:div {:ref #(when % (embed % (clj->js value)))}])])
