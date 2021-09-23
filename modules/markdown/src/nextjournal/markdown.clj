(ns nextjournal.markdown
  "Facility functions for handling markdown conversions"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [nextjournal.markdown.data :as markdown.data])
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
    (entrySet [_this] (into #{} (map (fn [k] [(keyword k) (value-as k (.getMember v k))])) (.getMemberKeys v)))
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
  (.eval ctx (.build (Source/newBuilder "js" "import MD from './modules/markdown/resources/js/markdown.mjs'; MD" "source.mjs"))))

(defn make-js-fn [fn-name]
  (let [f (.getMember MD-imports fn-name)]
    (fn [& args] (.execute f (to-array args)))))

(def parse* (make-js-fn "parse"))
(def parseJ* (make-js-fn "parseJ"))

(comment
  (.execute (.getMember MD-imports "parse") (to-array ["# Hello"]))
  (parseJ* "# Hello")
  (json/read-str (.asString (parseJ* "# Hello"))))

(defn tokenize [markdown-text]
  (let [^Value token-collection (parse* markdown-text)]
    (polyglot-coll->token-iterator token-collection)))

(defn tokenize-j [markdown-text]
  (let [^Value tokens-json (parseJ* markdown-text)]
    (json/read-str (.asString tokens-json) :key-fn keyword)))

(defn parse
  "Takes a string of Markdown text, returns a nested Clojure structure."
  [markdown-text]
  (-> markdown-text
      tokenize-j ;; compare performances with `tokenize`
      markdown.data/<-tokens))

(comment
  ;; build graal target `clj -M:examples:shadow watch graal browser`
  (-> (tokenize "[some text](/some/url)") first)

  (seq (tokenize "# markdown-it rulezz!\n\n${toc}\n## with markdown-it-toc-done-right rulezz even more!"))
  (seq (tokenize "# Hello

- [ ] one
- [ ] two
"))

  (-> (parse* "# Hello")
      polyglot-coll->token-iterator
      second
      ;;get-raw
      (get :children)
      first
      (get :content)
      )

  ;; esm module approach fails because of imports targeting files in shadow bundle with .js extension
  (.eval ctx (.. (Source/newBuilder "js" "import * from './public/js/markdown.mjs';" "source.mjs") build))

  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :browser)
  )
