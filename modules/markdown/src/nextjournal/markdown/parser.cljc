(ns nextjournal.markdown.parser
  "Deals with transforming a collection of markdown-it tokens obtained from parsing markdown text into an AST composed
  of nested clojure structures.

  See `parse` function.

  A \"Node\" has the following keys:
  - type: a Node's type as keyword (:heading, :paragraph, :text, :code etc.)
  - info: (optional) fenced code info
  - content: (optional) a collection of Nodes representing nested content
  - text: (optional) content of text nodes, a collection of Nodes
  - heading-level: (on `:heading` nodes)
  - attrs: attributes as passed by markdownit tokens (e.g {:attrs {:style \"some style info\"}})
  "
  (:require [clojure.string :as str]
            [nextjournal.markdown.transform :as md.transform]
            #?@(:cljs [[applied-science.js-interop :as j]
                       [cljs.reader :as reader]])))

;; clj common accessors
(def get-in* #?(:clj get-in :cljs j/get-in))
(def update* #?(:clj update :cljs j/update!))
(defn re-idx-seq
  "Takes a regex and a string, returns a seq of pairs of indices delimiting each match."
  [re text]
  #?(:clj (let [m (re-matcher re text)]
            (take-while some? (repeatedly #(when (.find m) [(.start m) (.end m)]))))
     :cljs (let [rex (js/RegExp. (.-source re) "g")]
             (take-while some? (repeatedly #(when-some [m (.exec rex text)] [(.-index m) (.-lastIndex rex)]))))))

;; region node operations
;; helpers
(defn inc-last [path] (update path (dec (count path)) inc))
(defn hlevel [{:as _token hn :tag}] (when (string? hn) (some-> (re-matches #"h([\d])" hn) second #?(:clj read-string :cljs reader/read-string))))
(defn parse-fence-info
  "Ingests nextjournal, GFM, Pandoc and RMarkdown fenced code block info, returns a map

   Nextjournal
   ```python id=2e3541da-0735-4b7f-a12f-4fb1bfcb6138
     ...
   ```

   Pandoc
   ```{#pandoc-id .languge .extra-class key=Val}
     ...
   ```

   Rmd
   ```{r cars, echo=FALSE}
     ...
   ```

   See also:
   - https://github.github.com/gfm/#info-string
   - https://pandoc.org/MANUAL.html#fenced-code-blocks
   - https://rstudio.com/wp-content/uploads/2016/03/rmarkdown-cheatsheet-2.0.pdf"
  [info-str]
  (try
    (when (string? info-str)
      (let [tokens (-> info-str
                       str/trim
                       (str/replace #"[\{\}\,]" "")         ;; remove Pandoc/Rmarkdown brackets and commas
                       (str/replace "." "")                 ;; remove dots
                       (str/split #" "))]                   ;; split by spaces
        (reduce
         (fn [{:as info-map :keys [language]} token]
           (let [[_ k v] (re-matches #"^([^=]+)=([^=]+)$" token)]
             (cond
               (str/starts-with? token "#") (assoc info-map :id (str/replace token #"^#" "")) ;; pandoc #id
               (and k v) (assoc info-map (keyword k) v)
               (not language) (assoc info-map :language token) ;; language is the first simple token which is not a pandoc's id
               :else (assoc info-map (keyword token) true))))
         {}
         tokens)))
    (catch #?(:clj Throwable :cljs :default) _ {})))

(comment
  (parse-fence-info "python runtime-id=5f77e475-6178-47a3-8437-45c9c34d57ff")
  (parse-fence-info "{#some-id .lang foo=nex}")
  (parse-fence-info "#id clojure")
  (parse-fence-info "clojure #id")
  (parse-fence-info "clojure")
  (parse-fence-info "{r cars, echo=FALSE}"))

;; leaf nodes
(defn text-node [text] {:type :text :text text})
(defn tag-node [text] {:type :hashtag :text text})
(defn formula [text] {:type :formula :text text})
(defn block-formula [text] {:type :block-formula :text text})
(defn sidenote-ref [ref] {:type :sidenote-ref :content [(text-node (str (inc ref)))]})

;; node constructors
(defn node
  [type content attrs top-level]
  (cond-> {:type type :content content}
    (seq attrs) (assoc :attrs attrs)
    (seq top-level) (merge top-level)))

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
(defn into-toc [toc {:as toc-item :keys [heading-level]}]
  (loop [toc toc l heading-level toc-path [:children]]
    ;; `toc-path` is `[:children i₁ :children i₂ ... :children]`
    (let [type-path (assoc toc-path (dec (count toc-path)) :type)]
      (cond
        ;; insert intermediate default empty :content collections for the final update-in (which defaults to maps otherwise)
        (not (get-in toc toc-path))
        (recur (assoc-in toc toc-path []) l toc-path)

        ;; fill in toc types for non-contiguous jumps like h1 -> h3
        (not (get-in toc type-path))
        (recur (assoc-in toc type-path :toc) l toc-path)

        (= 1 l)
        (update-in toc toc-path (fnil conj []) toc-item)

        :else
        (recur toc
               (dec l)
               (conj toc-path
                     (max 0 (dec (count (get-in toc toc-path)))) ;; select last child at level if it exists
                     :children))))))

(defn add-to-toc [{:as doc :keys [toc] path ::path}]
  (let [{:as h :keys [heading-level]} (get-in doc path)]
    (cond-> doc
      (pos-int? heading-level)
      (update :toc into-toc (assoc h
                                   :type :toc
                                   :title (md.transform/->text h)
                                   :path path)))))

(defn set-title-when-missing [{:as doc :keys [title] ::keys [path]}]
  (cond-> doc (nil? title) (assoc :title (md.transform/->text (get-in doc path)))))

(comment
 (-> {:type :toc}
     ;;(into-toc {:heading-level 3 :title "Foo"})
     ;;(into-toc {:heading-level 2 :title "Section 1"})
     (into-toc {:heading-level 1 :title "Title" :type :toc})
     (into-toc {:heading-level 4 :title "Section 2" :type :toc})
     ;;(into-toc {:heading-level 4 :title "Section 2.1"})
     ;;(into-toc {:heading-level 2 :title "Section 3"})
     )


 (-> "# Top _Title_

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

[[TOC]]

#### Four

end"
     nextjournal.markdown/parse
     :toc
     ))
;; endregion

;; region token handlers
(declare apply-tokens)
(defmulti apply-token (fn [_doc token] (:type token)))
(defmethod apply-token :default [doc token]
  (prn :apply-token/unknown-type {:token token})
  doc)

;; blocks
(defmethod apply-token "heading_open" [doc token] (open-node doc :heading {} {:heading-level (hlevel token)}))
(defmethod apply-token "heading_close" [doc {doc-level :level}] (-> doc close-node (cond-> (zero? doc-level) (-> add-to-toc set-title-when-missing))))
;; for building the TOC we just care about headings at document top level (not e.g. nested under lists) ⬆

(defmethod apply-token "paragraph_open" [doc _token] (open-node doc :paragraph))
(defmethod apply-token "paragraph_close" [doc _token] (close-node doc))

(defmethod apply-token "bullet_list_open" [doc {{:as attrs :keys [has-todos]} :attrs}] (open-node doc (if has-todos :todo-list :bullet-list) attrs))
(defmethod apply-token "bullet_list_close" [doc _token] (close-node doc))

(defmethod apply-token "ordered_list_open" [doc {:keys [attrs]}] (open-node doc :numbered-list attrs))
(defmethod apply-token "ordered_list_close" [doc _token] (close-node doc))

(defmethod apply-token "list_item_open" [doc {{:as attrs :keys [todo]} :attrs}] (open-node doc (if todo :todo-item :list-item) attrs))
(defmethod apply-token "list_item_close" [doc _token] (close-node doc))

(defmethod apply-token "math_block" [doc {text :content}] (push-node doc (block-formula text)))
(defmethod apply-token "math_block_end" [doc _token] doc)

(defmethod apply-token "hr" [doc _token] (push-node doc {:type :ruler}))

(defmethod apply-token "blockquote_open" [doc _token] (open-node doc :blockquote))
(defmethod apply-token "blockquote_close" [doc _token] (close-node doc))

(defmethod apply-token "tocOpen" [doc _token] (open-node doc :toc))
(defmethod apply-token "tocBody" [doc _token] doc) ;; ignore body
(defmethod apply-token "tocClose" [doc _token] (-> doc close-node (update-current dissoc :content)))

(defmethod apply-token "code_block" [doc {:as _token c :content}]
  (-> doc
      (open-node :code)
      (push-node (text-node c))
      close-node))
(defmethod apply-token "fence" [doc {:as _token i :info c :content}]
  (-> doc
      (open-node :code {} (assoc (parse-fence-info i) :info i))
      (push-node (text-node c))
      close-node))

;; footnotes
(defmethod apply-token "sidenote_ref" [doc token] (push-node doc (sidenote-ref (get-in* token [:meta :id]))))
(defmethod apply-token "sidenote_anchor" [doc token] doc)
(defmethod apply-token "sidenote_open" [doc token] (-> doc (assoc :sidenotes? true) (open-node :sidenote {:ref (get-in* token [:meta :id])})))
(defmethod apply-token "sidenote_close" [doc token] (close-node doc))
(defmethod apply-token "sidenote_block_open" [doc token] (-> doc (assoc :sidenotes? true) (open-node :sidenote {:ref (get-in* token [:meta :id])})))
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
|--------|:------------------------:|--------------------------------:|
|   foo  |  Loca _lDate_ ahoiii     | goog.date.Date                  |
|   bar  |  java.time.LocalTime     | some [kinky](link/to/something) |
|   bag  |  java.time.LocalDateTime | $\\phi$                         |
"
    nextjournal.markdown/parse
    nextjournal.markdown.transform/->hiccup
    ))

;; text
(comment
  (split-by-tags "some #Nice_tag123 and what #not for")
  (split-by-tags "some nicetag and what not for")
  (split-by-tags "#some nicetag and what not #for")
  (-> "# Hello
some par with #tag and #another one"
      nextjournal.markdown/tokenize
      parse))

(defn split-by-tags [text]
  (when (and (string? text) (seq text))
    (let [idx-seq (re-idx-seq #"(^|\B)#[\w-]+" text)]
      (when (seq idx-seq)
        (let [{:keys [nodes remaining-text]}
              (reduce (fn [{:as acc :keys [remaining-text]} [start end]]
                        (-> acc
                            (update :remaining-text subs 0 start)
                            (cond->
                              (<= end (dec (count remaining-text)))
                              (update :nodes conj (text-node (subs remaining-text end))))
                            (update :nodes conj (tag-node (subs remaining-text (inc start) end))))) ;; ⬅ remove "#"
                      {:remaining-text text :nodes []}
                      (reverse idx-seq))]
          (cond->> (reverse nodes)
            (seq remaining-text)
            (cons (text-node remaining-text))))))))

(defmethod apply-token "text" [doc {text :content}]
  (if-some [nodes (split-by-tags text)]
    (reduce push-node doc nodes)
    (push-node doc (text-node text))))

;; inlines
(defmethod apply-token "inline" [doc {:as _token ts :children}] (apply-tokens doc ts))
(defmethod apply-token "math_inline" [doc {text :content}] (push-node doc (formula text)))
(defmethod apply-token "math_inline_double" [doc {text :content}] (push-node doc (formula text)))
(defmethod apply-token "softbreak" [doc _token] (push-node doc {:type :softbreak}))

;; images
(defmethod apply-token "image" [doc {:keys [attrs children]}] (-> doc (open-node :image attrs) (apply-tokens children) close-node))

;; marks
(defmethod apply-token "em_open" [doc _token] (open-node doc :em))
(defmethod apply-token "em_close" [doc _token] (close-node doc))
(defmethod apply-token "strong_open" [doc _token] (open-node doc :strong))
(defmethod apply-token "strong_close" [doc _token] (close-node doc))
(defmethod apply-token "s_open" [doc _token] (open-node doc :strikethrough))
(defmethod apply-token "s_close" [doc _token] (close-node doc))
(defmethod apply-token "link_open" [doc token] (open-node doc :link (:attrs token)))
(defmethod apply-token "link_close" [doc _token] (close-node doc))
(defmethod apply-token "code_inline" [doc {text :content}] (-> doc (open-node :monospace) (push-node (text-node text)) close-node))

;; html (ignored)
(defmethod apply-token "html_inline" [doc _] doc)
(defmethod apply-token "html_block" [doc _] doc)
;; endregion

;; region data builder api
(defn pairs->kmap [pairs] (into {} (map (juxt (comp keyword first) second)) pairs))
(defn apply-tokens [doc tokens]
  (let [mapify-attrs-xf (map (fn [x] (update* x :attrs pairs->kmap)))]
    (reduce (mapify-attrs-xf apply-token) doc tokens)))

(def empty-doc {:type :doc
                :content []
                :toc {:type :toc}
                ;; private
                ::path [:content -1]})

(defn parse
  "Takes a doc and a collection of markdown-it tokens, applies tokens to doc. Uses an emtpy doc in arity 1."
  ([tokens] (parse empty-doc tokens))
  ([doc tokens] (-> doc
                    (apply-tokens tokens)
                    (dissoc ::path))))

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

## Sidenotes

here [^mynote] to somewhere

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

or monospace mark [`real`](/foo/bar) fun.

[^mynote]: Here you _can_ `explain` at lenght
"
     nextjournal.markdown/tokenize
     parse
     ;;seq
     ;;(->> (take 10))
     ;;(->> (take-last 4))
     ))
;; endregion

;; region zoom-in at section
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
    nextjournal.markdown.transform/->hiccup))
;; endregion
