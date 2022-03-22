(ns clerk.no-title
  (:require nextjournal.devdocs))

;; This notebook's title in the navbar is inferred by its namespace according to

(nextjournal.devdocs/ns->title (str *ns*))
