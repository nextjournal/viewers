# A Simple Devdoc

This is just a simple Devdoc, it contains code snippets, but they are not evaluated.

```clojure
(require '[lambdaisland.deja-fu :as fu])

(assoc (fu/local-date-time) :hours 10 :minutes 20 :seconds 30)
;; => #time/date-time "2021-06-30T10:20:30.529"
```
