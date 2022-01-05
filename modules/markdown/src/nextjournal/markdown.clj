(ns nextjournal.markdown
  "Facility functions for handling markdown conversions"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform])
  (:import (org.graalvm.polyglot Context Context$Builder Source Value)))

(set! *warn-on-reflection* true)

(def ^Context$Builder context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
    (.option "js.timer-resolution" "1")
    (.option "js.java-package-globals" "false")
    (.option "js.esm-eval-returns-exports", "true") ;; 🚨 returns module exports when evaluating an esm module file
    (.out System/out)
    (.err System/err)
    (.allowIO true)
    (.allowExperimentalOptions true)
    (.allowAllAccess true)
    (.allowNativeAccess true)
    (.option "engine.WarnInterpreterOnly" "false"))) ;; 🚨 silences warning on stock JVMs

;;  🚨: doesn't work with older (< 23.1) Graal JVM or Libraries, consumers might provide an alternative context by binding *ctx* dynamically.

(defn new-graal-context [] ^Context (.build context-builder))

(defn build-source []
  ;; Contructing a `java.io.Reader` first to work around a bug with graal on windows
  ;; see https://github.com/oracle/graaljs/issues/534 and https://github.com/nextjournal/viewers/pull/33
  (.build (-> (io/resource "js/markdown.mjs")
              io/input-stream
              io/reader
              (as-> r (Source/newBuilder "js" ^java.io.Reader r "markdown.mjs")))))

(defn new-context []
  (let [^Context ctx (new-graal-context)]
    {:graal-ctx ctx ;; reference to graal ctx for closing it
     :parse-fn (.. ctx (eval ^Source (build-source)) (getMember "default") (getMember "parseJ"))}))

(defn close-context [{::keys [graal-ctx]}] (.close ^Context graal-ctx))

(def ^:dynamic *ctx* (delay (new-context)))
(defn get-current-context [] (cond-> *ctx* (delay? *ctx*) deref))

(defn parse*
  ([text] (parse* (get-current-context) text))
  ([{:as _ctx :keys [parse-fn]} text]
   (.execute ^Value parse-fn (into-array String [text]))))

(defn tokenize [markdown-text]
  (let [^Value tokens-json (parse* markdown-text)]
    (json/read-str (.asString tokens-json) :key-fn keyword)))

(defn parse
  "Turns a markdown string into a nested clojure structure.

  A custom context might be provided in the caller thread by dynamically binding the *ctx* var, this might be useful for
  older versions of Graal which do not support options used to build the default context."
  [markdown-text] (-> markdown-text tokenize markdown.parser/parse))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup markdown.transform/default-hiccup-renderers markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.transform/->hiccup ctx))))

(comment
  ;; asks markdown-it parser for a sequence of tokens
  (tokenize "# Title
- [ ] one
- [x] two
")

  ;; parse markdonw into an "AST" of nodes
  (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  ;; default render
  (->hiccup "# Hello Markdown

What's _going_ on?
[[TOC]]")

  ;; custom overrides by type
  (->hiccup
   (assoc markdown.transform/default-hiccup-renderers
          :heading (fn [ctx node]
                     [:h1.some-extra.class
                      (markdown.transform/into-markup [:span.some-other-class] ctx node)]))
   "# Hello Markdown
What's _going_ on?
"))
