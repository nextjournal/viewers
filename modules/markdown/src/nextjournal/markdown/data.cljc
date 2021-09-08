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
      (update ::path inc-last)
      (update-in (pop path) conj node)))

(defn open-node [{:as doc ::keys [path]} type]
  (-> doc
      (push-node {:type type :content []})
      (update ::path into [:content -1])))

(defn close-node [doc] (update doc ::path (comp pop pop)))

;; beginsection
(defmulti token-op (fn [_document token] (:type token)))
(defmethod token-op "heading_open" [{:as doc} {:as _token}]
  (open-node doc :heading))
(defmethod token-op "heading_close" [{:as doc} token]
  (close-node doc))
(defmethod token-op "inline" [{:as doc} {:as _token ts :children}]
  (reduce token-op doc ts))
(defmethod token-op "text" [doc {t :text}]
  (push-node doc {:type :text :text t}))
;; endsection

(def empty-doc {:type :document :content [] ::path [:content -1]})

(defn <-tokens
  "ingests tokens "
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

  (nextjournal.markdown/parse "# Hello")
  )
