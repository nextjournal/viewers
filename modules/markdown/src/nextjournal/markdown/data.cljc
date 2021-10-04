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
  - attrs: attributes as passed by markdownit tokens (e.g {:attrs {:style \"some style info\"}})

  Mark:
  - mark: the mark type (:em, :strong, :link, etc.)
  - attrs: mark attributes e.g. href in links
  "
  #?(:cljs
     (:require [applied-science.js-interop :as j])))

;; clj common accessors
(def get-in* #?(:clj get-in :cljs j/get-in))

;; region node operations
(defn guard [pred val] (when (pred val) val))
(defn inc-last [path] (update path (dec (count path)) inc))
(defn hlevel [{:as _token hn :tag}] (when (string? hn) (some-> (re-matches #"h([\d])" hn) second #?(:clj read-string :cljs cljs.reader/read-string))))
(defn ->text [{:as _node :keys [text formula content]}] (or text formula (apply str (map ->text content))))

(defn node
  [type content attrs top-level]
  (cond-> {:type type :content content}
    (seq attrs) (assoc :attrs attrs)
    (seq top-level) (merge top-level)))
(defn mark
  ([type] (mark type nil))
  ([type attrs] (cond-> {:mark type} (seq attrs) (assoc :attrs attrs))))
(defn text-node
  ([text] (text-node text nil))
  ([text marks] (cond-> {:type :text :text text} (seq marks) (assoc :marks marks))))
(defn formula [text] {:type :formula :text text})
(defn sidenote-ref [ref] {:type :sidenote-ref :content [(text-node (inc ref))]})

(defn empty-text-node? [{text :text t :type}] (and (= :text t) (empty? text)))

(defn push-node [{:as doc ::keys [path]} node]
  (cond-> doc
    (not (empty-text-node? node)) ;; ⬅ mdit produces empty text tokens at mark boundaries, see edge cases below
    (-> #_doc
        (update ::path inc-last)
        (update-in (pop path) conj node))))

(defn open-node
  ([doc type] (open-node doc type {}))
  ([doc type attrs] (open-node doc type attrs {}))
  ([doc type attrs top-level]
   (-> doc
       (push-node (node type [] attrs top-level))
       (update ::path into [:content -1]))))

;; after closing a node, document ::path will point at it
(defn close-node [doc] (update doc ::path (comp pop pop)))
(defn update-current [{:as doc path ::path} fn & args] (apply update-in doc path fn args))
(defn open-mark
  ([doc type] (open-mark doc type {}))
  ([doc type attrs]
   (update doc ::marks conj (mark type attrs))))

(defn close-mark [doc] (update doc ::marks pop))

(comment                                                    ;; path after call
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
      ))
;; endregion

;; region TOC builder: (`add-to-toc` acts on toc after closing a header node)
(defn into-toc [toc {:as toc-item :keys [level]}]
  (loop [toc toc l level toc-path [:content]]
    (cond
      (= 1 l)
      (update-in toc toc-path (fnil conj []) toc-item)

      ;; fnil above is not enough to insert intermediate default empty :content collections for the final update-in (defaults to maps)
      (not (get-in toc toc-path))
      (recur (assoc-in toc toc-path []) l toc-path)

      :else
      (recur toc
             (dec l)
             (conj toc-path
                   (max 0 (dec (count (get-in toc toc-path)))) ;; select last child at level if it exists
                   :content)))))

(declare ->hiccup)
(defn add-to-toc [{:as doc :keys [toc] path ::path}]
  (let [{:as h :keys [heading-level]} (get-in doc path)]
    (cond-> doc
      (pos-int? heading-level)
      (update :toc into-toc {:level heading-level
                             :title (->text h)
                             :title-hiccup (->hiccup h)
                             :path path}))))

(comment
 (-> {}
     (into-toc {:level 3 :title "Foo"})
     (into-toc {:level 2 :title "Section 1"})
     (into-toc {:level 1 :title "Title"})
     (into-toc {:level 2 :title "Section 2"})
     (into-toc {:level 3 :title "Section 2.1"})
     (into-toc {:level 2 :title "Section 3"})
     ;;(into-toc 2 "Section 2")
     )


 (-> "# Start

par

### Three

## Two

par
- and a nested
- ### Heading not included

foo

## Two Again

par

# One Again

#### Four

end"
     nextjournal.markdown/parse
     :toc
     ))
;; endregion

;; region token handlers
(declare apply-tokens)
(defmulti apply-token (fn [_doc token] (:type token)))

;; blocks
(defmethod apply-token "heading_open" [doc token] (open-node doc :heading {} {:heading-level (hlevel token)}))
(defmethod apply-token "heading_close" [doc {doc-level :level}] (-> doc close-node (cond-> (zero? doc-level) add-to-toc)))
;; for building the TOC we just care about headings at document top level (not e.g. nested under lists) ⬆

(defmethod apply-token "paragraph_open" [doc _token] (open-node doc :paragraph))
(defmethod apply-token "paragraph_close" [doc _token] (close-node doc))

(defmethod apply-token "bullet_list_open" [doc {{:as attrs :keys [has-todos]} :attrs}] (open-node doc (if has-todos :todo-list :bullet-list) attrs))
(defmethod apply-token "bullet_list_close" [doc _token] (close-node doc))

(defmethod apply-token "ordered_list_open" [doc _token] (open-node doc :numbered-list))
(defmethod apply-token "ordered_list_close" [doc _token] (close-node doc))

(defmethod apply-token "list_item_open" [doc {{:as attrs :keys [todo]} :attrs}] (open-node doc (if todo :todo-item :list-item) attrs))
(defmethod apply-token "list_item_close" [doc _token] (close-node doc))

(defmethod apply-token "math_block" [doc {text :content}] (-> doc (open-node :block-formula) (push-node (formula text))))
(defmethod apply-token "math_block_end" [doc _token] (close-node doc))

(defmethod apply-token "hr" [doc _token] (push-node doc {:type :ruler}))

(defmethod apply-token "blockquote_open" [doc _token] (open-node doc :blockquote))
(defmethod apply-token "blockquote_close" [doc _token] (close-node doc))

(defmethod apply-token "tocOpen" [doc _token] (open-node doc :toc))
(defmethod apply-token "tocBody" [doc _token] doc ) ;; ignore body
(defmethod apply-token "tocClose" [doc _token] (-> doc close-node (update-current dissoc :content)))

(defmethod apply-token "code_block" [doc {:as _token c :content}]
  (-> doc
      (open-node :code)
      (push-node (text-node c))
      close-node))
(defmethod apply-token "fence" [doc {:as _token i :info c :content}]
  (-> doc
      (open-node :code {} {:info i})
      (push-node (text-node c))
      close-node))

;; footnotes

(defmethod apply-token "sidenote_ref" [doc token] (push-node doc (sidenote-ref (get-in* token [:meta :id]))))
(defmethod apply-token "sidenote_anchor" [doc token] doc)
(defmethod apply-token "sidenote_open" [doc token] (open-node doc :sidenote {:ref (get-in* token [:meta :id])}))
(defmethod apply-token "sidenote_close" [doc token] (close-node doc))
(defmethod apply-token "sidenote_block_open" [doc token] (open-node doc :sidenote {:ref (get-in* token [:meta :id])}))
(defmethod apply-token "sidenote_block_close" [doc token] (close-node doc))

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
    nextjournal.markdown/parse
    nextjournal.markdown.data/->hiccup
    ))

;; inlines
(defmethod apply-token "inline" [doc {:as _token ts :children}] (apply-tokens doc ts))

(defmethod apply-token "text" [{:as doc ms ::marks} {text :content}] (push-node doc (text-node text ms)))

(defmethod apply-token "math_inline" [doc {text :content}] (push-node doc (formula text)))
(defmethod apply-token "math_inline_double" [doc {text :content}] (push-node doc (formula text)))

(defmethod apply-token "softbreak" [doc _token] (push-node doc (text-node " ")))

(defmethod apply-token "image" [doc {:keys [attrs children]}] (-> doc (open-node :image attrs) (apply-tokens children) close-node))

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

;; html (ignored)
(defmethod apply-token "html_inline" [doc _] doc)


;; endregion
;; region data builder api
(defn pairs->kmap [pairs] (into {} (map (juxt (comp keyword first) second)) pairs))
(defn apply-tokens [doc tokens]
  (let [mapify-attrs-xf (map (fn [x] (#?(:clj update :cljs j/update!) x :attrs pairs->kmap)))]
    (reduce (mapify-attrs-xf apply-token) doc tokens)))

(def empty-doc {:type :doc
                :content []
                :toc {}
                ;; private
                ::path [:content -1]
                ::marks []})

(defn <-tokens
  "Takes a doc and a collection of markdown-it tokens, applies tokens to doc. Uses an emtpy doc in arity 1."
  ([tokens] (<-tokens empty-doc tokens))
  ([doc tokens] (-> doc (apply-tokens tokens) (dissoc ::path ::marks))))

(comment
  (-> "# Markdown Data

some _emphatic_ **strong** [link](https://foo.com)

---

> some ~~nice~~ quote
> for fun

## Formulas

[[TOC]]

$$\\Pi^2$$

- [ ]  and
- [x]  some $\\Phi_{\\alpha}$ latext
- [ ]  bullets

## Fences

```py id=\"aaa-bbb-ccc\"
1
print(\"this is some python\")
2
3
```

![Image Text](https://img.icons8.com/officel/16/000000/public.png)

Hline Section
-------------

### but also indented code

    import os
    os.listdir('/')

or monospace mark [`real`](/foo/bar) fun
"
      nextjournal.markdown/parse
      ;;seq
      ;;(->> (take 10))
      ;;(->> (take-last 4))
      )

  ;; Edge Cases
  ;; * overlapping marks produce empty text nodes
  (-> "*some text **with*** **mark overlap**"
      nextjournal.markdown/tokenize
      second
      :children
      seq
      )

  ;; * a strong with __ produces surrounding empty text nodes:
  (-> "__text__" nextjournal.markdown/tokenize second :children seq)
  (-> "__text__" nextjournal.markdown/tokenize <-tokens))
;; endregion

;; region hiccup renderer (maybe move to .hiccup ns)
(declare node->hiccup toc->hiccup)

(defn wrap-content [ctx hiccup {:as node :keys [type attrs content]}]
  (if-some [v (guard ifn? (get ctx type))]
    (v node)
    (into hiccup
          (keep (partial node->hiccup (assoc ctx ::parent type)))
          content)))

(defn toc-item->hiccup [{:keys [content title-hiccup]}]
  [:li.toc-item
   [:div
    title-hiccup
    (when (seq content) (into [:ul] (map toc-item->hiccup) content))]])

(defmulti  node->hiccup (fn [_ctx {:as _node :keys [type]}] type))

;; toc
(defmethod node->hiccup :toc [{:as ctx ::keys [toc]} _]
  (if-some [v (guard ifn? (get ctx :toc))] [v toc] (toc->hiccup toc)))

;; blocks
;; TODO: drop multimethods for a map { type => macro / fn }
(defn dataset [attrs]
  (into {}
        (map (juxt (comp (partial str "data-") name first) second))
        attrs))
(defmethod node->hiccup :doc [ctx node] (wrap-content ctx [:div] node))
(defmethod node->hiccup :heading [ctx node] (wrap-content ctx [(keyword (str "h" (:heading-level node)))] node))
(defmethod node->hiccup :paragraph [ctx node] (wrap-content ctx [:p] node))
(defmethod node->hiccup :block-formula [ctx node] (wrap-content ctx [:figure.formula] node))
(defmethod node->hiccup :bullet-list [ctx node] (wrap-content ctx [:ul] node))
(defmethod node->hiccup :todo-list [ctx node] (wrap-content ctx [:ul {:data-todo-list true}] node))
(defmethod node->hiccup :numbered-list [ctx node] (wrap-content ctx [:ol] node))
(defmethod node->hiccup :list-item [ctx node] (wrap-content ctx [:li] node))
(defmethod node->hiccup :todo-item [ctx {:as node :keys [attrs]}] (wrap-content ctx [:li (dataset attrs)] node))
(defmethod node->hiccup :blockquote [ctx node] (wrap-content ctx [:blockquote] node))
(defmethod node->hiccup :code [ctx node] (wrap-content ctx [:pre.viewer-code] node))
(defmethod node->hiccup :ruler [_ctx _node] [:hr])

;; inlines
(declare apply-marks apply-mark)
(defmethod node->hiccup :formula [ctx node] (wrap-content ctx [:span.formula] node))
(defmethod node->hiccup :sidenote-ref [ctx node] (wrap-content ctx [:sup.sidenote-ref] node))
(defmethod node->hiccup :text [_ctx {:keys [text marks]}] (cond-> text (seq marks) (apply-marks marks)))
(defmethod node->hiccup :image [{:as ctx ::keys [parent]} {:as node :keys [attrs]}]
  (if (= :paragraph parent) ;; TODO: add classes instead of inline styles
    [:img.inline attrs]
    [:figure.image [:img attrs] (wrap-content ctx [:figcaption] node)]))

;; sidenotes
(defmethod node->hiccup :sidenote [ctx {:as node :keys [attrs]}]
  (wrap-content ctx [:span.sidenote [:sup {:style {:margin-right "3px"}} (-> attrs :ref inc)]] node))

;; tables
(defmethod node->hiccup :table [ctx node] (wrap-content ctx [:table] node))
(defmethod node->hiccup :table-head [ctx node] (wrap-content ctx [:thead] node))
(defmethod node->hiccup :table-body [ctx node] (wrap-content ctx [:tbody] node))
(defmethod node->hiccup :table-row [ctx node] (wrap-content ctx [:tr] node))
(defmethod node->hiccup :table-header [ctx node] (wrap-content ctx [:th] node))
(defmethod node->hiccup :table-data [ctx node] (wrap-content ctx [:td] node))

;; marks
(defn apply-marks [ret m] (reduce apply-mark ret m))
(defmulti apply-mark (fn [_hiccup {m :mark}] m))
(defmethod apply-mark :em [hiccup _] [:em hiccup])
(defmethod apply-mark :monospace [hiccup _] [:code hiccup])
(defmethod apply-mark :strong [hiccup _] [:strong hiccup])
(defmethod apply-mark :strikethrough [hiccup _] [:s hiccup])
(defmethod apply-mark :link [hiccup {:keys [attrs]}] [:a {:href (:href attrs)} hiccup])

;; TODO: reverse args
(defn ->hiccup
  "Transforms MarkDown data into Hiccup

  an optional second `options` map allows for customizing type => render-fn to be used in combination with reagent."
  ([node] (->hiccup {} node))
  ([opts node] (node->hiccup (assoc opts ::toc (:toc node))
                             node)))

(defn toc->hiccup [{:as _toc :keys [content]}] (into [:ul.toc] (map toc-item->hiccup) content))

(comment
  (-> "# Hello

[[TOC]]

## Section One
A nice $\\phi$ formula [for _real_ **strong** fun](/path/to) \n soft

- [ ] one **ahoi** list
- two `nice` and ~~three~~
- [x] checked

> that said who?

## Section Two
---

### Images

![Some **nice** caption](https://www.example.com/images/dinosaur.jpg)

and here as inline ![alt](foo/bar) image

```clj
(some nice clojure)
```"
      nextjournal.markdown/parse
      ->hiccup
      ))
;; endregion

;; region zoom into sections
(defn section-at [{:as doc :keys [content]} [_ pos :as path]]
  ;; TODO: generalize over path (zoom-in at)
  ;; supports only top-level headings atm (as found in TOC)
  (let [{:as h section-level :heading-level} (get-in doc path)
        in-section? (fn [{l :heading-level}] (or (not l) (< section-level l)))]
    (when section-level
      {:type :doc
       :content (cons h
                      (->> content
                           (drop (inc pos))
                           (take-while in-section?)))})))

(comment
  (some-> "# Title

## Section 1

foo

- # What is this? (no!)
- maybe

### Section 1.2

## Section 2

some par

### Section 2.1

some other par

### Section 2.2

#### Section 2.2.1

two two one

#### Section 2.2.2

two two two

## Section 3

some final par"
          nextjournal.markdown/parse
          (section-at [:content 9])                         ;; ⬅ paths are stored in TOC sections
          ->hiccup))

;; endregion
(comment
  ;; boot browser repl
  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :browser)
  )
