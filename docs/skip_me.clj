;; This will be skipped
^{:nextjournal.devdocs/exclude? true}
(ns skip-me)
;; since we cannot evaluate cljs in Clerk
(require '[lambdaisland.deja-fu :as fu])

(assoc (fu/local-date-time) :hours 10 :minutes 20 :seconds 30)
