(ns nextjournal.viewer.katex
  (:require [clojure.string :as str]
            ["katex" :as katex]
            [applied-science.js-interop :as j]))

(defn remove-unsupported-syntax [content]
  (some-> content
          (str/replace #"\\(begin|end)\{(align|eqnarray)\*?\}" "\\$1{aligned}")
          (str/replace #"\\(begin|end)\{equation[^}]*?\}" "")
          (str/replace #"\\mbox\{" "\\textrm{")
          (str/replace #"\\label" "")
          (str/replace #"\\\\\\\n" "\\\\\n")))

(defn to-html-string
  ([content] (to-html-string content nil))
  ([content opts]
   (when-some [content (remove-unsupported-syntax content)]
     (katex/renderToString content (j/extend! #js{:displayMode false
                                                  :throwOnError false
                                                  :errorColor "#FF5722"}
                                              opts)))))
