(ns nextjournal.viewer.markdown
  (:require [nextjournal.markdown :as md]))

(defn viewer [value]
  (when value
    {:nextjournal/value (.render ^js @md/Markdown value)
     :nextjournal/viewer :html}))
