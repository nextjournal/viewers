(ns test-runner
  (:require [clojure.test]
            [nextjournal.markdown-test]))

(defn run [_]
  (clojure.test/run-tests 'nextjournal.markdown-test))
