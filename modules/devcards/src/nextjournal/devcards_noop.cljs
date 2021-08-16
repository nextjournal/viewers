(ns nextjournal.devcards-noop
  (:require-macros [nextjournal.devcards-noop]))

(def registry (atom {}))
(defn error-boundary [])
