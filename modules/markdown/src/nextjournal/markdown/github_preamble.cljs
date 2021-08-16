(ns nextjournal.markdown.github-preamble
  "MarkdownIt plugin for parsing github preamble,
   a simplified version of github.com/gojko/markdown-it-github-preamble"
  (:require [clojure.string :as str]
            [nextjournal.log :as log]
            [goog.object :as go]
            ["js-yaml" :as yaml]))

(defn Plugin
  {:jsdoc ["@constructor"]}
  [md opts]
  (this-as ^js this
    (let [count-markers (fn [state line-num]
                          (let [idx (+ (aget (.-bMarks state) line-num)
                                       (aget (.-tShift state) line-num))
                                line-len (inc (- (aget (.-eMarks state) line-num) idx))]
                            (if (not= (aget (.-src state) idx)
                                      "-")
                              0
                              (loop [counter 1]
                                (if (and (<= counter line-len)
                                         (= (aget (.-src state) (+ idx counter))
                                            "-"))
                                  (recur (inc counter))
                                  counter)))))

          find-preamble-end (fn [state startl endl]
                              (let [line (loop [line (inc startl)]
                                           (if (and (< line endl)
                                                    (< (count-markers state line) 3))
                                             (recur (inc line))
                                             line))]
                                (if (<= endl line) false line)))
          push-token (fn [state start end content]
                       (let [token (.push state "preamble" "" 0)]
                         (go/set token "info" content)
                         (go/set token "map" (array start end))
                         (go/set token "markup" "")
                         (go/set token "block" true)
                         (go/set state "line" (inc end))
                         true))
          parse-preamble (fn [state startl endl silent]
                           (if (or (not= startl 0) (> (.-blkIndent state) 0))
                             false
                             (if (< (count-markers state startl) 3)
                               false
                               (if silent
                                 true
                                 (let [block-end (find-preamble-end state startl endl)]
                                   (if (not block-end)
                                     false
                                     (try
                                       (let [pmb (yaml/safeLoad (subs (.-src state)
                                                                      (+ (aget (.-bMarks state) (inc startl))
                                                                         (aget (.-tShift state) (inc startl)))
                                                                      (inc (aget (.-eMarks state) (dec block-end)))))]
                                         (if (object? pmb)
                                           (push-token state startl block-end pmb)
                                           false))
                                       (catch js/Object e
                                         (log/warn :exception e)
                                         false))))))))]
      (.. md -block -ruler (before "fence"
                                   "preamble"
                                   parse-preamble
                                   (clj->js {:alt ["paragraph" "reference" "blockquote" "list"]}))))))
