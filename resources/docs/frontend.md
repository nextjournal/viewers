# A Frontend Notebook

This notebook contains code snippets which are inlined as ClojureScript, and
presented via the Nextjournal viewers.

# 🃏 Show devcards

```cljs
(show-card nextjournal.viewer.table/table-2 {::dc/title? false ::dc/class "w-full viewer"})
```

In this case we are also showing the source, but that is optional.

```cljs
[1 2 3 4]
```

You can choose which viewer will be used.

```cljs
^{:nextjournal/viewer :hiccup}
[:b "A bold stroke"]
```
