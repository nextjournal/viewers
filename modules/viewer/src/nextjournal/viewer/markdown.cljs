(ns nextjournal.viewer.markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.devcards :as dc]))

;; FIXME: inspect should be passed down to viewers via ctx or something
(defn inspect* [& args]
  (let [i (resolve 'nextjournal.viewer/inspect)]
    (apply i args)))

(defn eval-form* [f]
  (let [ef (resolve 'nextjournal.viewer.notebook/eval-form)]
    (ef f)))

(defn sidenote-click-handler [^js e]
  (when (.. e -target -classList (contains "sidenote-ref"))
    (.. e -target -classList (toggle "expanded"))))

(defn code-viewer [node]
  [:div.viewer-code
   [inspect* {:nextjournal/viewer :code
              :nextjournal/value (md.transform/->text node)}]])

(def default-renderers
  (assoc md.transform/default-hiccup-renderers
         :doc (partial md.transform/into-markup [:div.viewer-markdown {:on-click sidenote-click-handler}])
         :code (fn [_ctx node] (code-viewer node))
         :todo-item (fn [ctx {:as node :keys [attrs]}]
                      (md.transform/into-markup [:li [:input {:type "checkbox" :default-checked (:checked attrs)}]] ctx node))
                                                                               ;; ⬆ defaultChecked only makes senst in react/reagent
         :formula (fn [_ctx node] [inspect* {:nextjournal/viewer :latex
                                             :nextjournal/value (md.transform/->text node)}])))

(defn viewer [value]
  ;; TODO: allow to pass "modes" with different sets of overrides
  (when value
    {:nextjournal/value (md/->hiccup default-renderers value)
     :nextjournal/viewer :hiccup}))

(dc/defcard default-markdown
  [inspect* (viewer "# Markdown Default Rendering
## Sidenotes

One of the most distinctive features of Tufte’s style is his extensive use of sidenotes.
Sidenotes[^1] are like footnotes, except they don’t force the reader to jump their eye to
the bottom of the page, but instead display off to the side in the margin.[^longnote]

[^1]: Here’s a sidenote.

[^longnote]: And here is another one with more text that wraps over multiple lines.

Sidenotes are a great example of the web not being like print. On sufficiently large viewports,
Tufte CSS uses the margin for sidenotes, margin notes, and small figures. On smaller viewports,
elements that would go in the margin are hidden until the user toggles them into view.

The goal is to present related but not necessary information such as asides or citations as
close as possible to the text that references them. At the same time, this secondary information
should stay out of the way of the eye, not interfering with the progression of ideas in the
main text.

## Tags

You might want to #hashtag this document for #good #fun123.

## Todos

- [x] Checked
- [x] Unchecked
  - [ ] Nested unchecked

## Code

```clj
(like what)
```

### Block Formulas

$$\\int_{\\omega} \\phi d\\omega$$

### Formulas inside tables

| Syntax |  JVM                     | JavaScript                                    |
|:-------|:------------------------:|----------------------------------------------:|
| foo    |  Local_Date_             | goog.date.Date                                |
| bar    |  java.time.LocalDateTime | $\\bigoplus_{\\alpha < \\omega}\\phi_\\alpha$ |
")])

(dc/defcard dark-mode
  "Provide custom styles to e.g. support dark mode."
  [markdown]
  [inspect* {:nextjournal/viewer :hiccup
             :nextjournal/value (->> @markdown
                                     (md/->hiccup
                                      (assoc default-renderers
                                             :doc (partial md.transform/into-markup [:div.viewer-markdown.dark:bg-gray-900.dark:text-white.rounded.shadow-sm.p-4]))))}]
  {::dc/state "### Dark Mode Support
Here is some code that provides a custom wrapper with styles to e.g. set the text color
and background if dark mode is enabled in your system."})

(dc/defcard custom-code-eval
  [markdown]
  [inspect* {:nextjournal/viewer :hiccup
             :nextjournal/value (->> @markdown
                                     (md/->hiccup
                                      (assoc default-renderers
                                             :code (fn [_ {:as node :keys [language]}]
                                                     [:div
                                                      (code-viewer node)
                                                      (when (= "cljs" language)
                                                        [:div.viewer-result.mt-3
                                                         [inspect* (eval-form* (cljs.reader/read-string (md.transform/->text node)))]])]))))}]
  {::dc/state "# Custom `.cljs` Code Eval

Overrides the default `:code` renderer to add an extra sci pass for fenced code blocks with a `cljs` language info

```cljs
{:foo (reduce + 0 (range 10)) }
```

Can show what markdown parser actually do

```cljs
(nextjournal.markdown/parse \"# 👋🏻 Hello markdown parsing
- [x] with
- [ ] some
- [ ] fun
\")

```
same for hiccup transform
```cljs
(nextjournal.markdown/->hiccup \"# 👋🏻 Hello markdown parsing
- [x] with
- [ ] some
- [ ] fun
\")

```

code in other languages than clojurescript is just inert:

```python
import sys
sys.version
```

"})

(dc/defcard table-of-contents
  [markdown]
  [inspect* (viewer @markdown) ]
  {::dc/state "# Built-in Table of Contents

[[TOC]]

## Section 1

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

### Section 1.1

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

## Section 2

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

### Section 2.1

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

#### Section 2.1.1

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

### Section 2.2

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

## Section 3

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.
"})

(dc/defcard reference
  [markdown]
  [inspect* (viewer @markdown) ]
  {::dc/state "# Referenz

## Absätze

Normaler Text wird als Absatz interpretiert. Ein doppelter Zeilenabstand beginnt
einen neuen Absatz.

## Formatierung

* `**fett**` wird zu **fett**
* `_kursiv_` wird zu _kursiv_
* `~~durchgestrichen~~` wird zu ~~durchgestrichen~~
* `[Linktext](https://nextjournal.com/)` wird zu [Linktext](https://nextjournal.com/)

## Überschriften

Überschriften beginnen mit `#`. Mehrere aufeinanderfolgende `#`
definieren das Level der Überschrift.

    # Überschrift 1
    ## Überschrift 2
    ### Überschrift 3
    #### Überschrift 4

## Listen

### Aufzählungen

Normale Aufzählungen beginnen mit einem `*` und können verschachtelt sein.

    * Kühlschrank
        * Butter
        * Eier
        * Milch
    * Vorratsschrank
        * Brot
        * Backpapier
        * Alufolie

wird zu

* Kühlschrank
    * Butter
    * Eier
    * Milch
* Vorratsschrank
    * Brot
    * Backpapier
    * Alufolie

### Nummerierte Aufzählungen

Nummerierte Aufzählungen beginnen mit `1.` und können ebenfalls verschachtelt sein.
Verschachtelte Aufzählungen beginnen wieder mit `1.` (anstelle z.B. `1.1.`) und nehmen automatisch die
übergeordneten Indizes mit.

    1. Grünpflanzen
        1. Charophyten
        2. Chlorophyten
    2. Landpflanzen
        1. Lebermoose
        2. Laubmoose
        3. Hornmoose

wird zu

1. Grünpflanzen
   1. Charophyten
   2. Chlorophyten
2. Landpflanzen
   1. Lebermoose
   2. Laubmoose
   3. Hornmoose

### Todo Listen

Todo Listen beginnen mit `* [ ]` oder `* [x]` wobei das `x` markiert ob
das Todo erledigt ist. Todo Listen können ebenfalls verschachtelt sein.

    * [ ] Lebensmittel
        * [x] Butter
        * [ ] Eier
        * [ ] Milch
    * [x] Werkstatt
        * [x] Schrauben Torx M6
        * [x] Torx Bitsatz

wird zu

* [ ] Lebensmittel
    * [x] Butter
    * [ ] Eier
    * [ ] Milch
* [x] Werkstatt
    * [x] Schrauben Torx M6
    * [x] Torx Bitsatz

## Tabellen

Doppelpunkte können verwendet werden um den Text in Spalten links,
rechts oder zentriert auszurichten.


    | Spalte 1     | Spalte 2            | Spalte 3 |
    | ------------ |:-------------------:| --------:|
    | Spalte 1 ist | links ausgerichtet  |   1600 € |
    | Spalte 2 ist | zentriert           |     12 € |
    | Spalte 3 ist | rechts ausgerichtet |      1 € |

wird zu

| Spalte 1     | Spalte 2            | Spalte 3 |
| ------------ |:-------------------:| --------:|
| Spalte 1 ist | links ausgerichtet  |   1600 € |
| Spalte 2 ist | zentriert           |     12 € |
| Spalte 3 ist | rechts ausgerichtet |      1 € |

## Bilder

    ![ARS Altmann Waggon](https://www.ars-altmann.de/wp-content/uploads/2017/12/Schiene2.jpg)

wird zu

![ARS Altmann Waggon](https://www.ars-altmann.de/wp-content/uploads/2017/12/Schiene2.jpg)

## Zitate

    > “The purpose of computation is insight, not numbers.”
    >
    > ― Richard Hamming

wird zu

> “The purpose of computation is insight, not numbers.”
>
> ― Richard Hamming

## Trennlinien

Verschiedene Sektionen können durch Trennlinien verdeutlicht werden.
`---` produziert eine Linie über die volle Breite des Dokuments.

    #### Sektion 1

    Hier ist ein Absatz zur Sektion 1.

    ---

    #### Sektion 2

    Hier ist ein Absatz zur Sektion 2.

wird zu

#### Sektion 1

Hier ist ein Absatz zur Sektion 1.

---

#### Sektion 2

Hier ist ein Absatz zur Sektion 2.

## Inhaltsverzeichnis

Ein Inhaltsverzeichnis über alle Überschriften kann an jeder beliebigen
Stelle mit `[[toc]]` eingefügt werden.

    [[toc]]

wird zu

[[toc]]
"})
