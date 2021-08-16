(ns nextjournal.markdown.todo-lists
  "MarkdownIt parsing plugin for parsing github todo lists,
   a simplified version of https://github.com/revin/markdown-it-task-lists
   which also sets 'checked' info on the list item token. This plugin doesn't
   implement html rendering of todo lists."
  (:require [clojure.string :as str]
            [nextjournal.log :as log]
            [goog.object :as go]))

(defn todo-item-checked [tokens idx]
  (when (and (= "inline"         (.-type (aget tokens idx)))
             (= "paragraph_open" (.-type (aget tokens (dec idx))))
             (= "list_item_open" (.-type (aget tokens (- idx 2)))))
    (cond
      (= 0 (.. (aget tokens idx) -content (indexOf "[ ] "))) false
      (= 0 (.. (aget tokens idx) -content (indexOf "[x] "))) true)))

(defn remove-checkbox-text! [token]
  (let [first-child (aget (.-children token) 0)]
    (go/set first-child "content" (.. first-child -content (slice 4)))))

(defn closest-bullet-list [tokens idx]
  (when (<= 0 idx)
    (if (= "bullet_list_open" (.-type (aget tokens idx)))
      (aget tokens idx)
      (recur tokens (dec idx)))))

(defn rule [state]
  (let [tokens (.-tokens state)]
    (doseq [i (range 2 (.-length tokens))]
      (when-some [checked? (todo-item-checked tokens i)]
        (remove-checkbox-text! (aget tokens i))
        (doto (aget tokens (- i 2))
          (.attrSet "todo-item" true)
          (.attrSet "checked" checked?))
        (when-some [ul (closest-bullet-list tokens (- i 3))]
          (.attrSet ul "todo-list" true))))))

(defn Plugin [md opts]
  (.. md -core -ruler (after "inline" "github-todo-list" rule)))
