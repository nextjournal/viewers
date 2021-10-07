(ns nextjournal.markdown.transform
  "transform markdown data as returned by `nextjournal.markdown/parse` into other formats, currently:
     * hiccup")

;; helpers
(defn guard [pred val] (when (pred val) val))
(defn ->text [{:as _node :keys [text content]}] (or text (apply str (map ->text content))))

(defn hydrate-toc
  "Scans doc contents and replaces toc node placeholder with the toc node accumulated during parse."
  [{:as doc :keys [toc]}]
  (update doc :content (partial into [] (map (fn [{:as node t :type}] (if (= :toc t) toc node))))))

(defn table-alignment [{:keys [style]}]
  (when (string? style)
    (let [[_ alignment] (re-matches #"^text-align:(.+)$" style)]
      (when alignment {:text-align alignment}))))

;; into-markup
(declare ->hiccup)
(defn into-markup
  "Takes a hiccup vector, a context and a node, puts node's `:content` into markup mapping through `->hiccup`."
  [mkup ctx {:as node :keys [content]}]
  (into mkup
    (keep (partial ->hiccup (assoc ctx ::parent node)))
    content))

(defn toc->hiccup [{:as ctx ::keys [parent]} {:as node heading :node :keys [content]}]
  (cond->> [:div
            (when heading (->hiccup ctx heading))
            (when (seq content)
              (into [:ul]
                (map (partial ->hiccup (assoc ctx ::parent node)))
                content))]
    (= :toc (:type parent))
    (conj [:li.toc-item])
    (not= :toc (:type parent))
    (conj [:div.toc])))

(def default-hiccup-renderers
  {:doc (partial into-markup [:div])
   :heading (fn [ctx {:as node :keys [heading-level]}] (into-markup [(keyword (str "h" heading-level))] ctx node))
   :paragraph (partial into-markup [:p])
   :text (fn [_ {:keys [text]}] text)
   :hashtag (fn [_ {:keys [text]}] [:a.tag {:href (str "/tags/" text)} (str "#" text)]) ;; TODO: make it configurable
   :blockquote (partial into-markup [:blockquote])
   :ruler (partial into-markup [:hr])

   ;; images
   :image (fn [{:as ctx ::keys [parent]} {:as node :keys [attrs]}]
            (if (= :paragraph (:type parent))
              [:img.inline attrs]
              [:figure.image [:img attrs] (into-markup [:figcaption] ctx node)]))

   ;; code
   :code (partial into-markup [:pre.viewer-code])

   ;; softbreaks
   ;; :softbreak (constantly [:br]) (treat as space)
   :softbreak (constantly " ")

   ;; formulas
   :formula (partial into-markup [:span.formula])
   :block-formula (partial into-markup [:figure.formula])

   ;; lists
   :bullet-list (partial into-markup [:ul])
   :list-item (partial into-markup [:li])
   :todo-list (partial into-markup [:ul.contains-task-list])
   :numbered-list (partial into-markup [:ol])
   :todo-item (fn [ctx {:as node :keys [attrs]}]
                (into-markup [:li [:input {:type "checkbox" :checked (:checked attrs)}]] ctx node))

   ;; tables
   :table (partial into-markup [:table])
   :table-head (partial into-markup [:thead])
   :table-body (partial into-markup [:tbody])
   :table-row (partial into-markup [:tr])
   :table-header (fn [ctx {:as node :keys [attrs]}] (into-markup [:th {:style (table-alignment attrs)}] ctx node))
   :table-data (fn [ctx {:as node :keys [attrs]}] (into-markup [:td {:style (table-alignment attrs)}] ctx node))

   ;; sidenodes
   :sidenote-ref (partial into-markup [:sup.sidenote-ref])
   :sidenote (fn [ctx {:as node :keys [attrs]}]
               (into-markup [:span.sidenote [:sup {:style {:margin-right "3px"}} (-> attrs :ref inc)]]
                            ctx
                            node))
   ;; TOC
   :toc toc->hiccup

   ;; marks
   :em (partial into-markup [:em])
   :strong (partial into-markup [:strong])
   :monospace (partial into-markup [:code])
   :strikethrough (partial into-markup [:s])
   :link (fn [ctx {:as node :keys [attrs]}] (into-markup [:a {:href (:href attrs)}] ctx node))

   ;; default convenience fn to wrap extra markup around the default one from within the overriding function
   :default (fn [ctx {:as node t :type}] (when-some [d (get default-hiccup-renderers t)] (d ctx node)))
   })

(defn ->hiccup
  ([node] (->hiccup default-hiccup-renderers node))
  ([ctx {:as node t :type}]
   (let [{:as node :keys [type]} (cond-> node (= :doc t) hydrate-toc)]
     (if-some [f (guard fn? (get ctx type))]
       (f ctx node)
       [:div.error.red
        (str "We don't know how to turn a node of type: '" type "' into hiccup.")]
       ))))

(comment
  (clojure.core/some-> fo
                       abr)

  (-> "# Hello

a nice paragraph with sidenotes[^my-note]

[[TOC]]

## Section One
A nice $\\phi$ formula [for _real_ **strong** fun](/path/to) soft
break

- [ ] one **ahoi** list
- two `nice` and ~~three~~
- [x] checked

> that said who?

---

## Section Two

### Tables

| Syntax |  JVM                     | JavaScript                      |
|--------|-------------------------:|:--------------------------------|
|   foo  |  Loca _lDate_ ahoiii     | goog.date.Date                  |
|   bar  |  java.time.LocalTime     | some [kinky](link/to/something) |
|   bag  |  java.time.LocalDateTime | $\\phi$                         |

### Images

![Some **nice** caption](https://www.example.com/images/dinosaur.jpg)

and here as inline ![alt](foo/bar) image

```clj
(some nice clojure)
```

[^my-note]: Here can discuss at length"
    nextjournal.markdown/parse
    ->hiccup
    )

  ;; override defaults
  (->> "## Title
par one

par two"
    nextjournal.markdown/parse
    (->hiccup (assoc default-hiccup-renderers
                     :heading (partial into-markup [:h1.at-all-levels])
                     ;; wrap something around the default
                     :paragraph (fn [{:as ctx d :default} node] [:div.p-container (d ctx node)]))))
  )
