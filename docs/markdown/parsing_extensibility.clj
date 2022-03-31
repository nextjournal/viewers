;; # 🏗 Extending Markdown Parsing

^{:nextjournal.clerk/visibility :hide-ns}
(ns ^:nextjournal.clerk/no-cache markdown.parsing-extensibility
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [edamame.core :as edamame]))

;; With recent additions to our `nextjournal.markdown/parser` we're able to add a parsing layer on top of the original
;; tokenization provided by markdownit (see [[n.markdown/tokenize]]).
;;
;; We're acting on the text leafs from the markdownit tokenization, splitting each of those into a collection of [[n.m.parser/Node]]s
;; according to the following handling of the extracted token.

;; ## Types
;; let's describe our problem in terms of types:
;;
;;    Match :: Any
;;    Handler :: Match -> Node
;;    IndexedMatch :: (Match, Int, Int)
;;    TokenizerFn :: String -> [IndexedMatch]
;;    Tokenizer :: {:tokenizer-fn :: TokenizerFn,
;;                  :handler :: Handler}
;;
;; ## Regex-based tokenization
;; filling in a `:tokenizer-fn`:
(def regex-tokenizer
  (md.parser/->tokenizer
   {:regex #"\[\[([^\]]+)\]\]"
    :handler (fn [match] {:type :internal-link :text (match 1)})}))

((:tokenizer-fn regex-tokenizer) "some [[set]] of [[wiki]] link")

(md.parser/tokenize-text regex-tokenizer "some [[set]] of [[wiki]] link")

;; ## Read-based tokenization
;; we want to be able to parse text like
(def text "At some point in text a losange
will signal ◊(foo \"one\" [[vector]]) we'll want to write
code and ◊not text. Moreover it has not to conflict with
existing [[links]] or #tags")

;; Note: _losange_ is 🇫🇷 for _◊_.

;; We're taking inspiration from `clojure.core/re-seq` (amazing lazy-seq):
^{::clerk/visibility :hide}
(clerk/html
 [:div.viewer-code
  (clerk/code
   (with-out-str
     (clojure.repl/source re-seq)))])

(defn match->data+indexes [m text]
  (let [start (.start m)
        end (.end m)
        s (subs text end)
        p (edamame/parse-string s)
        {:keys [end-col]} (meta p)]

    [p start (+ end (dec end-col))]))

(defn losange-tokenizer-fn [text]
  (let [m (re-matcher #"◊" text)
        step (fn step []
               (when (.find m)
                 (cons (match->data+indexes m text)
                       (lazy-seq (step)))))]
    (step)))


(losange-tokenizer-fn text)
(losange-tokenizer-fn "non matching text")

(def losange-tokenizer
  {:tokenizer-fn losange-tokenizer-fn
   :handler (fn [clj-data] {:type :losange
                            :data clj-data})})

(md.parser/tokenize-text losange-tokenizer text)

;; putting it all together
(md.parser/parse (update md.parser/empty-doc :text-tokenizers #(cons losange-tokenizer %))
                 (md/tokenize text))

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 8888})



  )
