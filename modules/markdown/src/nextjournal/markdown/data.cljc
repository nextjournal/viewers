(ns nextjournal.markdown.data
  "Transforms a collection of markdown-it tokens obtained from parsing markdown text into a nested structure composed
   of nodes.

  Node:
  - node/type: a Node's type as keyword (:heading, :paragraph, :text, etc.)
  - info: (optional) fenced code info
  - content: (optional) a collection of Nodes representing nested content
  - text: (optional) content of text nodes, a collection of Nodes
  - marks: (optional) in text nodes, a collection of marks insisting on the node's text
  - level: (optional) heading level

  Mark:
  - mark/type: (:em, :strong, :link, etc.)
  - attrs: mark attributes
  ")

;; region node operations
(defn inc-last [path] (update path (dec (count path)) inc))

(defn node [type content] {:node/type type :content content})
(defn text-node [text marks] {:node/type :text :text text :marks marks})
(defn mark [type attrs] {:mark/type type :attrs attrs})

(defn open-node [{:as doc ::keys [path]} type]
  (-> doc
      (push-node (node type []))
      (update ::path into [:content -1])))

(defn empty-text-node? [{text :text t :node/type}]
  (and (= :text t) (empty? text)))

(defn push-node [{:as doc ::keys [path marks]} node]
  (cond-> doc
    (not (empty-text-node? node))                           ;; ⬅ mdit produces empty text tokens at mark boundaries, see edge cases below
    (-> #_doc
        (update ::path inc-last)
        (update-in (pop path) conj node))))

(defn close-node [doc] (update doc ::path (comp pop pop)))

(defn push-mark [doc type attrs]
  (update doc ::marks conj (mark type attrs)))

(defn close-mark [doc] (update doc ::marks pop))
;; endregion

;; region token handlers
(declare <-tokens)
(defmulti token-op (fn [_doc token] (:type token)))

(defmethod token-op "heading_open" [doc _token]
  (open-node doc :heading))

(defmethod token-op "heading_close" [doc _token]
  (close-node doc))

(defmethod token-op "paragraph_open" [doc _token]
  (open-node doc :paragraph))

(defmethod token-op "paragraph_close" [doc _token]
  (close-node doc))

(defmethod token-op "inline" [doc {:as _token ts :children}]
  (<-tokens doc ts))

(defmethod token-op "text" [{:as doc ms ::marks} {t :content}]
  (push-node doc (text-node t ms)))

;; marks
(defmethod token-op "em_open" [doc token] (push-mark doc :em {}))
(defmethod token-op "em_close" [doc token] (close-mark doc))
(defmethod token-op "strong_open" [doc token] (push-mark doc :strong {}))
(defmethod token-op "strong_close" [doc token] (close-mark doc))
(defmethod token-op "link_open" [doc token] (push-mark doc :link {:href "foo/link/bar"}))
(defmethod token-op "link_close" [doc token] (close-mark doc))
;; endregion

(def empty-doc {:type :document :content [] ::path [:content -1] ::marks []})

(defn <-tokens
  "Takes a doc and a collection of markdown-it tokens, applies tokes to doc. Uses an emtpy doc in arity 1."
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (reduce token-op doc tokens)))

(comment                                                    ;; path after call
  (-> empty-doc                                             ;; [:content -1]
      (open-node :heading)                                  ;; [:content 0 :content -1]
      (push-node {:node/type :text :text "foo"})                 ;; [:content 0 :content 0]
      (push-node {:node/type :text :text "foo"})                 ;; [:content 0 :content 1]
      close-node                                            ;; [:content 1]

      (open-node :paragraph)                                ;; [:content 1 :content]
      (push-node {:node/type :text :text "hello"})
      close-node
      (open-node :bullet-list)
      ;;
      )

  (-> "# Hello

some [alt](https://foo.com/bar) link"
      nextjournal.markdown/parse

      <-tokens
      ;;second
      ;;:children
      ;;seq
      )

  (-> "*some text **with*** **mark overlap**"
      nextjournal.markdown/parse
      <-tokens) ;; =>
  {:type :document,
   :content [{:node/type :paragraph,
              :content [{:node/type :text, :text "some text ", :marks [{:mark/type :em, :attrs {}}]}
                        {:node/type :text,
                         :text "with",
                         :marks [{:mark/type :em, :attrs {}} {:mark/type :strong, :attrs {}}]}
                        {:node/type :text, :text " ", :marks []}
                        {:node/type :text, :text "mark overlap", :marks [{:mark/type :strong, :attrs {}}]}]}],
   :nextjournal.markdown.data/path [:content 0],
   :nextjournal.markdown.data/marks []}


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
