(ns nextjournal.viewer.code
  (:require ["@codemirror/language" :refer [syntaxHighlighting HighlightStyle]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :refer [EditorView]]
            ["@lezer/highlight" :refer [tags]]
            [nextjournal.clojure-mode :as cm-clj]
            [applied-science.js-interop :as j]))

(def cm6-theme
  (.theme EditorView
          (j/lit {"&.cm-focused" {:outline "none"}
                  ".cm-line" {:padding "0"
                              :line-height "1.6"
                              :font-size "15px"
                              :font-family "\"Fira Mono\", monospace"}
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

(def highlight-style
  (.define HighlightStyle
    (clj->js [{:tag (.-meta tags) :class "cmt-meta"}
              {:tag (.-link tags) :class "cmt-link"}
              {:tag (.-heading tags) :class "cmt-heading"}
              {:tag (.-emphasis tags) :class "cmt-italic"}
              {:tag (.-strong tags) :class "cmt-strong"}
              {:tag (.-strikethrough tags) :class "cmt-strikethrough"}
              {:tag (.-keyword tags) :class "cmt-keyword"}
              {:tag (.-atom tags) :class "cmt-atom"}
              {:tag (.-bool tags) :class "cmt-bool"}
              {:tag (.-url tags) :class "cmt-url"}
              {:tag (.-contentSeparator tags) :class "cmt-contentSeparator"}
              {:tag (.-labelName tags) :class "cmt-labelName"}
              {:tag (.-literal tags) :class "cmt-literal"}
              {:tag (.-inserted tags) :class "cmt-inserted"}
              {:tag (.-string tags) :class "cmt-string"}
              {:tag (.-deleted tags) :class "cmt-deleted"}
              {:tag (.-regexp tags) :class "cmt-regexp"}
              {:tag (.-escape tags) :class "cmt-escape"}
              {:tag (.. tags (special (.-string tags))) :class "cmt-string"}
              {:tag (.. tags (definition (.-variableName tags))) :class "cmt-variableName"}
              {:tag (.. tags (local (.-variableName tags))) :class "cmt-variableName"}
              {:tag (.-typeName tags) :class "cmt-typeName"}
              {:tag (.-namespace tags) :class "cmt-namespace"}
              {:tag (.-className tags) :class "cmt-className"}
              {:tag (.. tags (special (.-variableName tags))) :class "cmt-variableName"}
              {:tag (.-macroName tags) :class "cmt-macroName"}
              {:tag (.. tags (definition (.-propertyName tags))) :class "cmt-propertyName"}
              {:tag (.-comment tags) :class "cmt-comment"}
              {:tag (.-invalid tags) :class "cmt-invalid"}])))

(def ext #js [cm-clj/default-extensions
              (syntaxHighlighting highlight-style)
              (.. EditorView -editable (of false))
              cm6-theme])

(defn viewer* [value]
  ^{:key value}
  [:div {:ref #(when %
                 (let [prev-view (j/get % :editorView)]
                   (when (or (nil? prev-view)
                             (not= value (j/call-in prev-view [:state :doc :toString])))
                     (some-> prev-view (j/call :destroy))
                     (j/assoc! % :editorView
                               (EditorView. #js {:state (.create EditorState #js {:doc value :extensions ext})
                                                 :parent %})))))}])

(defn viewer [value]
  ^{:nextjournal/viewer :reagent} [viewer* value])
