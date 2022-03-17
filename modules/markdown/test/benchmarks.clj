(ns benchmarks
  (:require [clojure.test :refer :all]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]))

(defmacro time-ms
  "Pure version of `clojure.core/time`. Returns a map with `:result` and `:time-ms` keys."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     {:result ret#
      :time-ms (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(defn parse+ [extra-tokenizers text]
  (md.parser/parse (update md.parser/empty-doc :regex-tokenizers concat extra-tokenizers)
                   (md/tokenize text)))

;; TODO: add to github workflow
(defn run [_]
  (let [text (slurp "modules/markdown/test/reference.md")]
    (time
     (dotimes [_ 100] (md/parse text)))))

;; with internal link plugin                                      => 5348.398521 msecs
;; with handling hashtags _and_ internal-links in n.m.parser.cljc => 5439.111559 msecs

(comment
  (run {})

  ;; test timing of parsing 3 regexes => 5462.372797 msecs
  (let [text (slurp "modules/markdown/test/reference.md")]
    (time
     (dotimes [_ 100]
       (parse+ [{:regexp #"\{\{([^\{]+)\}\}"
                 :handler (fn [m] {:type :var :text (m 1)})}]
               text))))


  (parse+ [{:regexp #"\{\{([^\{]+)\}\}"
            :handler (fn [m] {:type :var :text (m 1)})}]
          (slurp "modules/markdown/test/reference.md"))

  (time-ms (md/parse (slurp "modules/markdown/test/reference.md")))
  (md/parse (slurp "modules/markdown/test/reference.md")))
