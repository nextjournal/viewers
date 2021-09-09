(ns nextjournal.markdown

  "Facility functions for handling markdown conversions

  - [ ] A tiny js file requiring md-it and setting up plugins, extensible
  - [ ] A clojure ns (this) for handling GraalJs stuff
  - [ ] a cljc file for handling tokens returned by parsing markdown
  "
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.graalvm.polyglot PolyglotException Context Engine Source Value]
           (clojure.lang ILookup)
           (java.util Iterator)
           (java.lang Iterable)))

(def engine (Engine/create))

;; TODO: build and source from io/resource
(defn source-file [path] (.build (Source/newBuilder "js" (io/resource path))))

(def context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
    (.engine engine)
    (.option "js.timer-resolution" "1")
    (.option "js.java-package-globals" "false")
    (.out System/out)
    (.err System/err)
    (.allowExperimentalOptions true)
    (.allowAllAccess true)
    (.allowNativeAccess true)))

(def ctx (doto (.build context-builder)
           (.eval (source-file "graal/js/markdown.js"))   ;; make this available at clj compile time ?
           ;; TODO: share js code with cljs (e.g. with es module target approach )
           ;; TODO: make (shadow.)require work with released js
           ;;(.eval "js" "const MD = require('markdown-it')")
           (.eval "js" "const MD = shadow.js.require(\"module$node_modules$markdown_it$index\", {})({html: true, linkify: true})")
           (.eval "js" "function parseJ(text) { return JSON.stringify(MD.parse(text, {})) }")
           (.eval "js" "function parse(text)  { return MD.parse(text, {}) }")))

(defn parse [markdown-text]
  (-> ctx
      (.eval "js" "parseJ")
      (.execute (into-array Object [markdown-text]))
      ;; ⬇ compare performance with polyglot.Value + ILookup approach
      .asString
      (json/read-str :key-fn keyword)))

(comment
  ;; build graal target `clj -M:examples:shadow watch graal browser`
  (parse "[some text](/some/url)")
  (parse "# Hello

- [ ] one
- [ ] two
")

  ;; alternative to JSON pass
  (declare value-as)

  (deftype Token [^Value v]
    ILookup
    (valAt [this key] (value-as (name key) (.getMember v (name key)))))

  (defn coll->token-iterator [^Value polyglot-coll]
    (when (.hasIterator polyglot-coll)
      (reify java.lang.Iterable
        (iterator [this]
          (let [iterator ^Value (.getIterator polyglot-coll)]
            (reify java.util.Iterator
              (hasNext [this] (.hasIteratorNextElement iterator))
              (next [this] (->Token (.getIteratorNextElement iterator)))))))))

  (defn value-as [key ^Value v]
    (when v
      (case key
        ("type" "tag" "content" "markup" "info") (.asString v)
        ("block" "hidden") (.asBoolean v)
        "children" (-> v coll->token-iterator)
        "level" (.asInt v)
        "attrs" nil
        "map" nil
        "nesting" nil
        "meta" nil
        nil)))

  (-> (.execute (.eval ctx "js" "parse") (into-array ["# Hello"]))
      coll->token-iterator
      second
      (get :children)
      first
      (get :content)
      )

  (-> (.execute (.eval ctx "js" "parse") (into-array ["# Hello"]))
      coll->token-iterator
      second
      .-v
      ;;(get :foo "bang")
      )

  ;; alternative ->Token for better printing
  (defn ->Token [^Value v]
    (proxy [java.util.Map] []
      (get [key] (value-as (name key) (.getMember v (name key))))
      (entrySet [] (into #{} (map (fn [k] [k (value-as k (.getMember v k))])) (.getMemberKeys v)))))

  (source "function(text) { return MD.parse(text) " "parse.js")
  ;; set javascript es module mimetype
  ;; esm module approach fails because of imports targeting files in shadow bundle with .js extension
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import {markdown} from 'public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import * from './public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))

  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :browser)
  )
