(ns nextjournal.markdown
  "Facility functions for handling markdown conversions"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform])
  (:import [org.graalvm.polyglot Context Context$Builder Engine Source Value]
           [clojure.lang MapEntry]
           [java.util Iterator Map]
           [java.lang Iterable]))

(set! *warn-on-reflection* true)

(def ^Engine engine (Engine/create))

(def ^Context$Builder context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
    (.engine engine)
    (.option "js.timer-resolution" "1")
    (.option "js.java-package-globals" "false")
    (.out System/out)
    (.err System/err)
    (.allowExperimentalOptions true)
    (.allowAllAccess true)
    (.allowNativeAccess true)))

(def ^Context ctx (.build context-builder))

(def ^Value MD-imports
  (.eval ctx (.build (Source/newBuilder "js"
                                        (str "import MD from '" (.getPath (io/resource "js/markdown.mjs")) "'; MD")
                                        "source.mjs"))))

(defn make-js-fn [fn-name]
  (let [f (.getMember MD-imports fn-name)]
    (fn [& args] (.execute f (to-array args)))))

(def parse* (make-js-fn "parseJ"))

(comment
  (.execute (.getMember MD-imports "parse") (to-array ["# Hello"]))
  (parse* "# Hello"))

(defn tokenize [markdown-text]
  (let [^Value tokens-json (parse* markdown-text)]
    (json/read-str (.asString tokens-json) :key-fn keyword)))

(defn parse
  "Turns a markdown string into a nested clojure structure."
  [markdown-text] (-> markdown-text tokenize markdown.parser/parse))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup markdown.transform/default-hiccup-renderers markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.transform/->hiccup ctx))))

(comment
  (parse "# Hello Markdown 👋
what _a_ parser with $$\\phi$$ formulas
- [x] one
- [ ] two
[[TOC]]
")

  (->hiccup "# Hello Markdown 🔥
- [x] done
- [ ] pending

![alt](/some/img/)"))
