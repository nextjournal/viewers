(ns nextjournal.markdown.data
  "Transforms a collection of markdown-it tokens obtained from parsing markdown text into a nested structure composed
   of nodes.

  Node:
  - type: a Node's type as keyword (:heading, :paragraph, :text, etc.)
  - info: (optional) fenced code info
  - content: (optional) a collection of Nodes representing nested content
  - text: (optional) content of text nodes, a collection of Nodes
  - marks: (optional) in text nodes, a collection of marks insisting on the node's text
  - level: (optional) heading level

  Mark:
  - mark: the mark type (:em, :strong, :link, etc.)
  - attrs: mark attributes e.g. href in links
  ")

;; region node operations
(defn pairs->kmap [pairs] (into {} (map (juxt (comp keyword first) second)) pairs))
(defn inc-last [path] (update path (dec (count path)) inc))
(defn hlevel [{:as _token hn :tag}] (when (string? hn) (some-> (re-matches #"h([\d])" hn) second #?(:clj read-string :cljs cljs.reader/read-string))))

(defn node [type content attrs] (cond-> {:type type :content content} (seq attrs) (assoc :attrs (pairs->kmap attrs))))
(defn mark
  ([type] (mark type nil))
  ([type attrs] (cond-> {:mark type} (seq attrs) (assoc :attrs (pairs->kmap attrs)))))
(defn text-node
  ([text] (text-node text nil))
  ([text marks] (cond-> {:type :text :text text} (seq marks) (assoc :marks marks))))
(defn formula [text] {:type :formula :text text})

(defn empty-text-node? [{text :text t :type}] (and (= :text t) (empty? text)))

(defn push-node [{:as doc ::keys [path]} node]
  (cond-> doc
    (not (empty-text-node? node)) ;; ⬅ mdit produces empty text tokens at mark boundaries, see edge cases below
    (-> #_doc
        (update ::path inc-last)
        (update-in (pop path) conj node))))

(defn open-node
  ([doc type] (open-node doc type {}))
  ([doc type attrs]
   (-> doc
       (push-node (node type [] attrs))
       (update ::path into [:content -1]))))

(defn close-node [doc] (update doc ::path (comp pop pop)))

(defn open-mark
  ([doc type] (open-mark doc type {}))
  ([doc type attrs]
   (update doc ::marks conj (mark type attrs))))

(defn close-mark [doc] (update doc ::marks pop))
;; endregion

;; region token handlers
(declare apply-tokens)
(defmulti apply-token (fn [_doc token] (:type token)))

;; blocks
(defmethod apply-token "heading_open" [doc token] (open-node doc :heading {:heading-level (hlevel token)}))
(defmethod apply-token "heading_close" [doc _token] (close-node doc))

(defmethod apply-token "paragraph_open" [doc _token] (open-node doc :paragraph))
(defmethod apply-token "paragraph_close" [doc _token] (close-node doc))

(defmethod apply-token "bullet_list_open" [doc _token] (open-node doc :bullet-list))
(defmethod apply-token "bullet_list_close" [doc _token] (close-node doc))

(defmethod apply-token "ordered_list_open" [doc _token] (open-node doc :ordered-list))
(defmethod apply-token "ordered_list_close" [doc _token] (close-node doc))

(defmethod apply-token "list_item_open" [doc _token] (open-node doc :list-item))
(defmethod apply-token "list_item_close" [doc _token] (close-node doc))

(defmethod apply-token "math_block" [doc {text :content}] (-> doc (open-node :block-formula) (push-node (formula text))))
(defmethod apply-token "math_block_end" [doc _token] (close-node doc))

(defmethod apply-token "hr" [doc _token] (push-node doc {:type :ruler}))

(defmethod apply-token "blockquote_open" [doc _token] (open-node doc :blockquote))
(defmethod apply-token "blockquote_close" [doc _token] (close-node doc))

(defmethod apply-token "code_block" [doc {:as _token c :content}]
  (-> doc
      (open-node :code)
      (push-node (text-node c))
      close-node))
(defmethod apply-token "fence" [doc {:as _token i :info c :content}]
  (-> doc
      (open-node :code {:info i})
      (push-node (text-node c))
      close-node))

;; tables
;; table data tokens might have {:style "text-align:right|left"} attrs, maybe better nested node > :attrs > :style ?
(defmethod apply-token "table_open" [doc _token] (open-node doc :table))
(defmethod apply-token "table_close" [doc _token] (close-node doc))
(defmethod apply-token "thead_open" [doc _token] (open-node doc :table-head))
(defmethod apply-token "thead_close" [doc _token] (close-node doc))
(defmethod apply-token "tr_open" [doc _token] (open-node doc :table-row))
(defmethod apply-token "tr_close" [doc _token] (close-node doc))
(defmethod apply-token "th_open" [doc token] (open-node doc :table-header (:attrs token)))
(defmethod apply-token "th_close" [doc _token] (close-node doc))
(defmethod apply-token "tbody_open" [doc _token] (open-node doc :table-body))
(defmethod apply-token "tbody_close" [doc _token] (close-node doc))
(defmethod apply-token "td_open" [doc token] (open-node doc :table-data (:attrs token)))
(defmethod apply-token "td_close" [doc _token] (close-node doc))

(comment
  (->
"
| Syntax |  JVM                     | JavaScript                      |
|--------|-------------------------:|:--------------------------------|
|   foo  |  Loca _lDate_ ahoiii     | goog.date.Date                  |
|   bar  |  java.time.LocalTime     | some [kinky](link/to/something) |
|   bag  |  java.time.LocalDateTime | $\\phi$                         |
"
    nextjournal.markdown/parse-j
    nextjournal.markdown.data/<-tokens
    ))

;; inlines
(defmethod apply-token "inline" [doc {:as _token ts :children}] (apply-tokens doc ts))

(defmethod apply-token "text" [{:as doc ms ::marks} {text :content}] (push-node doc (text-node text ms)))

(defmethod apply-token "math_inline" [doc {text :content}] (push-node doc (formula text)))
(defmethod apply-token "math_inline_double" [doc {text :content}] (push-node doc (formula text)))

(defmethod apply-token "softbreak" [doc {text :content}] (push-node doc {:type :softbreak}))

;; marks
(defmethod apply-token "em_open" [doc _token] (open-mark doc :em))
(defmethod apply-token "em_close" [doc _token] (close-mark doc))
(defmethod apply-token "strong_open" [doc _token] (open-mark doc :strong))
(defmethod apply-token "strong_close" [doc _token] (close-mark doc))
(defmethod apply-token "s_open" [doc _token] (open-mark doc :strikethrough))
(defmethod apply-token "s_close" [doc _token] (close-mark doc))
(defmethod apply-token "link_open" [doc token] (open-mark doc :link (:attrs token)))
(defmethod apply-token "link_close" [doc _token] (close-mark doc))
(defmethod apply-token "code_inline" [{:as doc ms ::marks} {text :content}]
  (push-node doc (text-node text (conj ms (mark :monospace)))))
;; endregion

(def apply-tokens (partial reduce apply-token))

(def empty-doc {:type :doc :content [] ::path [:content -1] ::marks []})

(defn <-tokens
  "Takes a doc and a collection of markdown-it tokens, applies tokens to doc. Uses an emtpy doc in arity 1."
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (-> doc (apply-tokens tokens) (dissoc ::path ::marks))))

(comment                                                    ;; path after call
  ;; boot browser repl
  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :browser)

  (-> empty-doc                                             ;; [:content -1]
      (open-node :heading)                                  ;; [:content 0 :content -1]
      (push-node {:node/type :text :text "foo"})            ;; [:content 0 :content 0]
      (push-node {:node/type :text :text "foo"})            ;; [:content 0 :content 1]
      close-node                                            ;; [:content 1]

      (open-node :paragraph)                                ;; [:content 1 :content]
      (push-node {:node/type :text :text "hello"})
      close-node
      (open-node :bullet-list)
      ;;
      )

  (-> "# Markdown Data

some _emphatic_ **strong** [link](https://foo.com)

---

> some ~~nice~~ quote
> for fun

## Formulas

$$\\Pi^2$$

* and
* some $\\Phi_{\\alpha}$ latext
* bullets

## Fences

```py id=\"aaa-bbb-ccc\"
1
print(\"this is some python\")
2
3
```

Hline Section
-------------

### but also indented code

    import os
    os.listdir('/')

or monospace mark [`real`](/foo/bar) fun
"
      nextjournal.markdown/parse
      nextjournal.markdown.data/<-tokens
      ;;seq
      ;;(->> (take 10))
      ;;(->> (take-last 4))
      )

  ;; Edge Cases
  ;; * overlapping marks produce empty text nodes
  (-> "*some text **with*** **mark overlap**"
      nextjournal.markdown/parse
      second
      :children
      seq
      )

  ;; * a strong with __ produces surrounding empty text nodes:
  (-> "__text__" nextjournal.markdown/parse second :children  seq)
  (-> "__text__" nextjournal.markdown/parse <-tokens)

  )
