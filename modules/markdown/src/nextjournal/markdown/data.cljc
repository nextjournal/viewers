(ns nextjournal.markdown.data
  "Module for ingesting markdown-it tokens and return clojure map based data structures

 - [ ] decide for records

  Node:
  - type
  - attrs
  - info
  - content
  ")

(defn inc-last [path] (update path (dec (count path)) (fnil inc -1)))
(comment
  (inc-last [:content 3]))

(defn push-node [{:as doc ::keys [path]} node]
  (-> doc
      (update-in path conj node)))

(defn open-node [doc type]
  (-> doc
      (push-node {:type type :content []})
      (update ::path into [0 :content])))

(defn close-node [doc] (update doc ::path pop))

(defmulti token-op (fn [_document token] (:type token)))
(defmethod token-op "heading_open" [{:as doc} {:as _token}]
  (open-node doc :heading))
(defmethod token-op "heading_close" [{:as doc} token]
  (close-node doc))
(defmethod token-op "inline" [{:as doc} {:as _token ts :children}]
  (reduce token-op doc ts))
(defmethod token-op "text" [doc {t :text}]
  (push-node doc {:type :text :text t}))

(def empty-doc {:type :document :content [] ::path [:content]})

(defn <-tokens
  "ingests tokens "
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (reduce token-op doc tokens)))

(comment                                                    ;; path after call
  (-> empty-doc                                             ;; [:content]
      (open-node :heading)                                  ;; [:content 0 :content]


      (push-node {:type :text :text "foo"})                 ;; [:content 0 :content]
      (push-node {:type :text :text "foo"})                 ;; [:content 0 :content 2]
      ;;;;
      ;;close-node                                            ;; [:content 1]
      ;;(open-node :paragraph)                                ;; [:content 1 :content -1
      ;;(push-node {:type :text :text "hello"})
      ;;
      )



  (update-in {} [] inc)
  (ns-unmap *ns* 'token-op)

  (<-tokens)
  (nextjournal.markdown/parse "# Hello")

  )
