(ns nextjournal.markdown
  (:require ["@toycode/markdown-it-class" :as md-class]
            ["markdown-it" :as md]
            ["markdown-it/lib/token" :as Token]
            ["markdown-it-task-lists" :as md-task-lists]
            ["markdown-it-texmath" :as md-texmath]
            ["katex" :as katex]
            [nextjournal.markdown.github-preamble :as github-preamble]
            [nextjournal.markdown.todo-lists :as todo-lists]
            [applied-science.js-interop :as j]
            [nextjournal.markdown.data :as markdown.data]))

(extend-type Token
  ILookup
  (-lookup [this key] (j/get this key)))

(def Markdown
  (delay
   (doto ^js (md #js{:breaks false :html true :linkify true})
     (.use md-task-lists #js{:label true})
     (.use md-texmath #js{:delimiters "dollars" :engine katex})  ;; $$ or $

     #_#_ ;; TODO: support these
     (use md-texmath #js{:delimiters "brackets"}) ;; \[ \] or \( \)
     (use md-texmath #js{:delimiters "latex"}) ;; \begin{env} \end{env} (block only)

     ;; add tailwind styling to lists
     (.use md-class #js{:ul "list-disc list-inside"
                        :ol "list-decimal list-inside"})
     (.use github-preamble/Plugin)
     (.use todo-lists/Plugin))))

(defn parse [text]
  (-> (.parse @Markdown text {}) markdown.data/<-tokens))

(comment
  (.render @Markdown "# Hello Markdown\nWhat's _going_ on?")
  (js/console.log  (.parse @Markdown "# Hello Markdown\nWhat's _going_ on?" {}))
  (parse "# Hello Markdown")
  )
