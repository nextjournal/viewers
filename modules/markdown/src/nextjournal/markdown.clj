(ns nextjournal.markdown
  "Facility functions for handling markdown conversions

  - [ ] A tiny js file requiring md-it and setting up plugins, extensible
  - [ ] A clojure ns (this) for handling GraalJs stuff
  - [ ] a cljc file for handling tokens returned by parsing markdown
  "
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [org.graalvm.polyglot PolyglotException Context Engine Source Value]
           (clojure.lang ISeq Seqable ILookup)))

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
           (.eval "js" "const MD = shadow.js.require(\"module$node_modules$markdown_it$index\", {})({html: true})")
           (.eval "js" "function parseJ(text) { return JSON.stringify(MD.parse(text)) }")
           (.eval "js" "function parse(text)  { return MD.parse(text) }")))

(defn parse [markdown-text]
  (-> ctx
      (.eval "js" "parseJ")
      (.execute (into-array Object [markdown-text]))
      ;; ⬇ compare performance with polyglot.Value + ILookup approach
      .asString
      (json/read-str :key-fn keyword)))

(comment
  ;; build graal target `clj -M:examples:shadow watch graal browser`
  (parse "# Hello _there_")
  (parse "[some text](/some/url)")
  (-> (.execute (.eval ctx "js" "parse") (into-array ["# Hello"]))
      ;;.hasArrayElements
      ;;.getArraySize
      (.getArrayElement 1)
      ;;(.getMemberKeys)
      (.getMember "children")
      ;;.hasArrayElements)
      )

  ;; FIXME:
  ;; (.isMetaObject (.execute (.eval ctx "js" "parse") (into-array ["# Hello"])))
  ;; (.hasIterator Value) throws 'No matching field found: isIterator for class org.graalvm.polyglot.Value'
  ;; as if polyglot api would be older than what required in deps.edn
  ;; despite the claim: _Guest language arrays are iterable._

  ;; alternative to JSON encoding, with wrapped polyglot.Value s
  ;; a record would fix .-key access (but easier to get ILookup on the Token class on the cljs side)
  (defrecord Token [type tag attrs map nesting level children content markup info meta block hidden])
  ;; or wrap it in a reified ILookup instance
  (.-type (map->Token {:type "type" :tag "tag"}))
  (declare ->tokens)
  (defn ->token [polyglot-map]
    (map->Token
      (into {}
            (map (juxt keyword #(let [m (.getMember polyglot-map %)] ;; TODO: fix access for all field with reify+ILookup
                                  (cond-> m (and (not (.isNull m)) (= "children" %)) ->tokens))))
            (.getMemberKeys polyglot-map))))
  (defn ->tokens [polyglot-array]
    (map #(->token (.getArrayElement polyglot-array %))
         (range (.getArraySize polyglot-array))))

  ;; (.as Value Type) coercion
  ;; coerces single elements into com.oracle.truffle.polyglot.PolyglotMap which loses some compound values
  ;; https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-
  (defn ->coll [^Value polyglot-array-value]
    (.as polyglot-array-value java.util.List))

  ;; coerce with .as (can't extend Java types with ISeq )
  (-> (.execute (.eval ctx "js" "parse") (into-array Object ["# Hello
  ```py some info
  1 + 1
  ```"]))
      ->coll
      first)


  (source "function(text) { return MD.parse(text) " "parse.js")
  ;; set javascript es module mimetype
  ;; esm module approach fails because of imports targeting files in shadow bundle with .js extension
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import {markdown} from 'public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))
  (.eval ctx  (.build (-> (Source/newBuilder "js" "import * from './public/js/markdown.mjs';" "source.mjs") (.mimeType "application/javascript+module"))))
  )
