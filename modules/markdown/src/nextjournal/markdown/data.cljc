(ns nextjournal.markdown.data
  "Module for ingesting markdown-it tokens and return clojure map based data structures

 - [ ] decide for records

  Node:
  - type
  - attrs
  - info
  - content
  - [text]
  ")

(defn inc-last [path] (update path (dec (count path)) (fnil inc -1)))

;; region node operations
(defn push-node [{:as doc ::keys [path]} node]
  (-> doc
      (update ::path inc-last)
      (update-in (pop path) conj node)))

(defn open-node [{:as doc ::keys [path]} type]
  (-> doc
      (push-node {:type type :content []})
      (update ::path into [:content -1])))

(defn close-node [doc] (update doc ::path (comp pop pop)))
;; endregion
;; region token handlers
(defmulti token-op (fn [_doc token] (:type token)))

(defmethod token-op "heading_open" [doc _token]
  (open-node doc :heading))

(defmethod token-op "heading_close" [doc _token]
  (close-node doc))

(defmethod token-op "inline" [doc {:as _token ts :children}]
  (<-tokens doc ts))

(defmethod token-op "text" [doc {t :content}]
  (push-node doc {:type :text :text t}))
;; endregion

(def empty-doc {:type :document :content [] ::path [:content -1]})

(defn <-tokens
  "Takes a doc and a collection of markdown-it tokens, applies tokes to doc. Uses an emtpy doc in arity 1."
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (reduce token-op doc tokens)))

(comment                                                    ;; path after call
  (-> empty-doc                                             ;; [:content -1]
      (open-node :heading)                                  ;; [:content 0 :content -1]
      (push-node {:type :text :text "foo"})                 ;; [:content 0 :content 0]
      (push-node {:type :text :text "foo"})                 ;; [:content 0 :content 1]
      close-node                                            ;; [:content 1]

      (open-node :paragraph)                                ;; [:content 1 :content]
      (push-node {:type :text :text "hello"})
      close-node
      (open-node :bullet-list)
      ;;
      )

  (-> "# Hello"
      nextjournal.markdown/parse
      <-tokens
      )
  )
