(ns nextjournal.viewer.code
  (:require ["@codemirror/highlight" :as highlight]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :refer [EditorView]]
            [nextjournal.clojure-mode :as cm-clj]
            [applied-science.js-interop :as j]))

(def cm6-theme
  (.theme EditorView
          (j/lit {"&.cm-focused" {:outline "none"}
                  ".cm-line" {:padding "0"
                              :line-height "1.6"
                              :font-size "15px"
                              :font-family "var(--code-font)"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}

                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"
                                             :animation "steps(1) cm-blink 1.2s infinite"}
                  "&.cm-focused .cm-selectionBackground" {:background-color "Highlight"}
                  ".cm-tooltip" {:border "1px solid rgba(0,0,0,.1)"
                                 :border-radius "3px"
                                 :overflow "hidden"}
                  ".cm-tooltip > ul > li" {:padding "3px 10px 3px 0 !important"}
                  ".cm-tooltip > ul > li:first-child" {:border-top-left-radius "3px"
                                                       :border-top-right-radius "3px"}})))

(def ext #js [cm-clj/default-extensions
              highlight/defaultHighlightStyle
              (.. EditorView -editable (of false))
              cm6-theme])

(defn viewer* [value]
  ^{:key value}
  [:div {:ref #(when %
                 (EditorView. #js {:state (.create EditorState #js {:doc value :extensions ext})
                                   :parent %}))}])

(defn viewer [value]
  ^{:nextjournal/viewer :reagent} [viewer* value])
