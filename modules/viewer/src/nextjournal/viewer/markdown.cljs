(ns nextjournal.viewer.markdown
  (:require [nextjournal.markdown :as md]))

(defn viewer [value]
  (when value
    {:nextjournal/value  (md/->hiccup value)
     :nextjournal/viewer :hiccup}))
