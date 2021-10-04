(ns nextjournal.markdown
  (:require ["/js/markdown" :as md]
            ["markdown-it/lib/token" :as Token]
            ["katex" :as katex]
            [applied-science.js-interop :as j]
            [nextjournal.markdown.data :as markdown.data]))

(extend-type Token
  ILookup
  (-lookup [this key] (j/get this key)))

(def tokenize md/parse)

(defn parse
  "Turns a markdown string into a nested clojure structure."
  [markdown-text] (-> markdown-text tokenize markdown.data/<-tokens))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup {} markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.data/->hiccup ctx))))

(comment
  (js/console.log
   (tokenize "# Title
- [ ] one
- [x] two
"))

  (parse "# Hello Markdown

- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  (->hiccup "# Hello Markdown\nWhat's _going_ on?"))
