(ns nextjournal.markdown

  "Facility functions for handling markdown conversions

  - [ ] A tiny js file requiring md-it and setting up plugins, extensible
  - [ ] A clojure ns (this) for handling GraalJs stuff
  - [ ] a cljc file for handling tokens returned by parsing markdown
  "
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.graalvm.polyglot Context Context$Builder Engine Source Value]
           [clojure.lang MapEntry]
           [java.util Iterator Map]
           [java.lang Iterable]))

(set! *warn-on-reflection* true)

(declare value-as)

(defprotocol IRawValue (get-raw [this] "Retrieves original Polyglot value from a Token instance"))

(defn ->Token [^Value v]
  (reify
    Map
    (get [_this key] (value-as (name key) (.getMember v (name key))))
    (entrySet [_this] (into #{} (map (fn [k] [k (value-as k (.getMember v k))])) (.getMemberKeys v)))
    IRawValue
    (get-raw [_this] v)))

(defn polyglot-coll->token-iterator [^Value polyglot-coll]
  (when (.hasIterator polyglot-coll)
    (reify Iterable
      (iterator [_this]
        (let [^Value iterator (.getIterator polyglot-coll)]
          (reify Iterator
            (hasNext [_this] (.hasIteratorNextElement iterator))
            (next [_this] (->Token (.getIteratorNextElement iterator)))))))))

(defn map-like [^Value map-entries]
  (map (fn [idx] (let [^Value e (.getArrayElement map-entries idx)]
                   (MapEntry/create (keyword (.asString (.getArrayElement e 0)))
                                    (.asString (.getArrayElement e 1)))))
       (range (.getArraySize map-entries))))

(defn value-as [key ^Value v]
  (when (and v (not (.isNull v)))
    (case key
      ("type" "tag" "content" "markup" "info") (.asString v)
      "children" (polyglot-coll->token-iterator v)
      ("block" "hidden") (.asBoolean v)
      ("level" "nesting") (.asInt v)
      "attrs" (map-like v)
      "meta" v
      "map" [(as-> v val ^Value (.getArrayElement val 0) (.asInt val))
             (as-> v val ^Value (.getArrayElement val 1) (.asInt val))]
      nil)))

(def engine (Engine/create))

(defn source-file [path] (.build (Source/newBuilder "js" (io/resource path))))

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

(def ^Context ctx
  (doto (.build context-builder)
    (.eval (source-file "graal/js/markdown.js"))            ;; make this available at clj compile time ?
    ;; TODO: share js code with cljs
    (.eval "js" "const MD = module$node_modules$markdown_it$index({html: true, linkify: true})")
    (.eval "js" "function parseJ(text) { return JSON.stringify(MD.parse(text, {})) }")
    (.eval "js" "function parse(text)  { return MD.parse(text, {}) }")))

(defn parse [markdown-text]
  (let [^Value parse-fn (.eval ctx "js" "parse")
        ^Value token-collection (.execute parse-fn (into-array String [markdown-text]))]
    (polyglot-coll->token-iterator token-collection)))

;; ⬇ compare performances
(defn parse-j [markdown-text]
  (-> ctx
      (.eval "js" "parseJ")
      (.execute (into-array String [markdown-text]))
      .asString
      (json/read-str :key-fn keyword)))

(comment
  ;; build graal target `clj -M:examples:shadow watch graal browser`
  (-> (parse "[some text](/some/url)") first)

  (seq (parse "# Hello

- [ ] one
- [ ] two
"))

  (-> (.execute (.eval ctx "js" "parse") (into-array ["# Hello"]))
      polyglot-coll->token-iterator
      second
      (get :children)
      first
      (get :content)
      )

  (-> (.execute (.eval ctx "js" "parse") (into-array ["# Hello"]))
      polyglot-coll->token-iterator
      second
      get-raw
      ;;(get :foo "bang")
      )

  (source "function(text) { return MD.parse(text) " "parse.js")
  ;; set javascript es module mimetype
  ;; esm module approach fails because of imports targeting files in shadow bundle with .js extension
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import {markdown} from 'public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import * from './public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))

  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :browser)
  )
