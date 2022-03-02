^{:nextjournal.clerk/visibility :hide-ns}
(ns notebooks.clerk)

;; # A Clerk Notebook

;; This is a Clerk notebook, it is written as Clojure plus comments like this
;; one.
;;
;; Comments can contain markdown. When rendering this in devdocs the code is
;; evaluated once, and the results are cached.

(seq (.split (System/getProperty "java.class.path") ":"))
