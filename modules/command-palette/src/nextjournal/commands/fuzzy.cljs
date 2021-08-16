(ns nextjournal.commands.fuzzy
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]))

;; modified from https://github.com/bevacqua/fuzzysearch/blob/master/index.js
;; to take into account word boundaries and consecutive characters

(defn boundary-code? [i]
  (and (> i 31)
       (< i 48)))

(defn match-codes? [c1 c2]
  (or (== c1 c2)
      (and (boundary-code? c1) (boundary-code? c2))))

(def START_WEIGHT 2.5)
(def BOUNDARY_WEIGHT 2)
(def CONSECUTIVE_WEIGHT 1.5)
(def EXACT_EXTRA 3)

(defn exact-score [needle haystack]
  (when-some [i (str/index-of haystack needle)]
    (let [exact-sum (+ EXACT_EXTRA (* CONSECUTIVE_WEIGHT (count needle)))]
      (if (zero? i)
        (+ exact-sum START_WEIGHT)
        (when (boundary-code? (j/call haystack :charCodeAt (dec i)))
          (+ BOUNDARY_WEIGHT exact-sum))))))

(defn match
  "Fuzzy-match `query` in `text`. Returns map of
   :score - number representing quality of match
   :chars - list of matched character indexes

  - Characters match in order
  - Weights are assigned to boundaries and consecutive characters"
  [{:keys [query text]}]
  (or (when-let [n (exact-score query text)]
        {:fuzzy/score n
         :fuzzy/chars (let [start (str/index-of text query)]
                        (when-not (neg? start)
                          (range start (+ start (count query)))))}) ;; if exact match exists, use its score
      (let [hlen (j/!get text :length)
            nlen (j/!get query :length)]
        (if (> nlen hlen)
          {:fuzzy/score 0}
          (loop [i 0
                 j 0
                 score 0
                 prev-matched? false
                 at-boundary? true
                 at-start? true
                 chars []]
            (cond (== i nlen) {:fuzzy/score score :fuzzy/chars chars}
                  (== j hlen) {:fuzzy/score 0}
                  :else
                  (let [nchar (j/call query :charCodeAt i)
                        hchar (j/call text :charCodeAt j)]
                    (if-let [match-weight (and (match-codes? nchar hchar)
                                               (cond at-start? START_WEIGHT
                                                     at-boundary? BOUNDARY_WEIGHT
                                                     prev-matched? CONSECUTIVE_WEIGHT
                                                     :else false))]
                      (recur (inc i)
                             (inc j)
                             (+ score match-weight)
                             true
                             (boundary-code? hchar)
                             false
                             (conj chars j))
                      (recur i
                             (inc j)
                             score
                             false
                             (boundary-code? hchar)
                             false
                             chars)))))))))

(comment

 (match "LT" "List Titan")

 (->> ["a" " a" BOUNDARY_WEIGHT
       "a" "ab" BOUNDARY_WEIGHT
       "ab " "a b " (+ BOUNDARY_WEIGHT BOUNDARY_WEIGHT CONSECUTIVE_WEIGHT)
       "ab" "a b" (* 2 BOUNDARY_WEIGHT)
       "ab" "ab" (* 2 EXACT_WEIGHT)
       "b" "ab" 1                                          ;; single char match
       "abc" "abdd" 0                                      ;; "c" is not in haystack, no match
       "ab" "abc" (+ BOUNDARY_WEIGHT CONSECUTIVE_WEIGHT)]
      (partition 3)
      (map (fn [[needle haystack score]] (= score (:fuzzy/score (match needle haystack)))))))

(defn highlight-chars
  "Wraps matched characters in :span.font-bold"
  ;; TODO
  ;; `c p` should only match `p` at word boundary
  ([s chars]
   (highlight-chars "font-bold" s chars))
  ([highlight-class s chars]
   (loop [out [:<>]
          i 0
          chars chars]
     (if-let [ch (first chars)]
       (recur (cond-> out
                (not= i ch)
                (conj [:span (subs s i ch)])
                true
                (conj [:span {:class highlight-class} (subs s ch (inc ch))]))
              (inc ch)
              (rest chars))
       (cond-> out
         (< i (count s))
         (conj [:span (subs s i)]))))))
