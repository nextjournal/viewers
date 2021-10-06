(ns nextjournal.viewer.markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.devcards :as dc]))

;; FIXME: inspect should be passed down to viewers via ctx or something
(defn inspect* [& args]
  (let [i (resolve 'nextjournal.viewer/inspect)]
    (apply i args)))

(defn sidenote-click-handler [^js e]
  (when (.. e -target -classList (contains "sidenote-ref"))
    (.. e -target -classList (toggle "expanded"))))

(def default-renderers
  (assoc md.transform/default-hiccup-renderers
         :doc (partial md.transform/into-markup [:div.viewer-markdown {:on-click sidenote-click-handler}])
         :code (fn [_ctx node] [:div.viewer-code
                                   [inspect* {:nextjournal/viewer :code
                                              :nextjournal/value (md.parser/->text node)}]])
         :formula (fn [_ctx node] [inspect* {:nextjournal/viewer :latex
                                             :nextjournal/value (md.parser/->text node)}])))

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
|--------|-------------------------:|:----------------------------------------------|
| foo    |  Local_Date_             | goog.date.Date                                |
| bar    |  java.time.LocalDateTime | $\\bigoplus_{\\alpha < \\omega}\\phi_\\alpha$ |
")])

(dc/defcard dark-mode
  "Provide custom styles to e.g. support dark mode."
  [markdown]
  [inspect* {:nextjournal/viewer :hiccup
             :nextjournal/value (->> @markdown
                                     (md/->hiccup
                                      (assoc md.transform/default-hiccup-renderers
                                             :doc (partial md.transform/into-markup [:div.viewer-markdown.dark:bg-gray-900.dark:text-white.rounded.shadow-sm.p-4]))))}]
  {::dc/state "### Dark Mode Support
Here is some code that provides a custom wrapper with styles to e.g. set the text color
and background if dark mode is enabled in your system."})

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

### Section 2.2

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

## Section 3

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.
"})
