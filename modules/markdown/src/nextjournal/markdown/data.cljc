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
(defn inc-last [path] (update path (dec (count path)) inc))

(defn node [type content attrs] (assoc attrs :type type :content content))
(defn mark [type attrs] (cond-> {:mark type} (seq attrs) (assoc :attrs attrs)))
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

(defn push-mark [doc type attrs]
  (update doc ::marks conj (mark type attrs)))

(defn close-mark [doc] (update doc ::marks pop))
;; endregion

;; region token handlers
(declare <-tokens)
(defmulti token-op (fn [_doc token] (:type token)))

;; blocks
(defmethod token-op "heading_open" [doc _token] (open-node doc :heading))
(defmethod token-op "heading_close" [doc _token] (close-node doc))

(defmethod token-op "paragraph_open" [doc _token] (open-node doc :paragraph))
(defmethod token-op "paragraph_close" [doc _token] (close-node doc))

(defmethod token-op "bullet_list_open" [doc _token] (open-node doc :bullet-list))
(defmethod token-op "bullet_list_close" [doc _token] (close-node doc))

(defmethod token-op "ordered_list_open" [doc _token] (open-node doc :ordered-list))
(defmethod token-op "ordered_list_close" [doc _token] (close-node doc))

(defmethod token-op "list_item_open" [doc _token] (open-node doc :list-item))
(defmethod token-op "list_item_close" [doc _token] (close-node doc))

(defmethod token-op "math_block" [doc {text :content}] (-> doc (open-node :block-formula) (push-node (formula text))))
(defmethod token-op "math_block_end" [doc _token] (close-node doc))

(defmethod token-op "hr" [doc _token] (push-node doc {:type :ruler}))

(defmethod token-op "blockquote_open" [doc _token] (open-node doc :blockquote))
(defmethod token-op "blockquote_close" [doc _token] (close-node doc))

(defmethod token-op "fence" [doc {:as _token i :info c :content}]
  (-> doc
      (open-node :code {:info i})
      (push-node (text-node c))
      close-node))

(defmethod token-op "inline" [doc {:as _token ts :children}] (<-tokens doc ts))

;; inline
(defmethod token-op "text" [{:as doc ms ::marks} {text :content}] (push-node doc (text-node text ms)))

(defmethod token-op "math_inline" [doc {text :content}] (push-node doc (formula text)))
(defmethod token-op "math_inline_double" [doc {text :content}] (push-node doc (formula text)))
(defmethod token-op "softbreak" [doc {text :content}] (push-node doc {:type :softbreak}))

;; marks
(defmethod token-op "em_open" [doc token] (push-mark doc :em {}))
(defmethod token-op "em_close" [doc token] (close-mark doc))
(defmethod token-op "strong_open" [doc token] (push-mark doc :strong {}))
(defmethod token-op "strong_close" [doc token] (close-mark doc))
(defmethod token-op "link_open" [doc token] (push-mark doc :link (into {} (:attrs token))))
(defmethod token-op "link_close" [doc token] (close-mark doc))
;; endregion

(def empty-doc {:type :doc :content [] ::path [:content -1] ::marks []})

(defn <-tokens
  "Takes a doc and a collection of markdown-it tokens, applies tokes to doc. Uses an emtpy doc in arity 1."
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (reduce token-op doc tokens)))

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

  (-> "# Hello

some _emphatic_ **strong** [link](https://foo.com)

---

$$\\Pi^2$$

> some nice quote
> for fun

* and
* some $\\Phi_{\\alpha}$ latext
* bullets

```py id=\"aaa-bbb-ccc\"
1
print(\"this is some python\")
2
3
```
"
      nextjournal.markdown/parse
      nextjournal.markdown.data/<-tokens
      ;;seq
      ;;(->> (take 10))
      ;;(->> (take-last 3))
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
