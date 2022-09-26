;; # A Clerk Notebook
^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns notebooks.clerk)

;; This is a Clerk notebook, it is written as Clojure plus comments like this
;; one.
;;
;; Comments can contain markdown. When rendering this in devdocs the code is
;; evaluated once, and the results are cached.

(seq (.split (System/getProperty "java.class.path") ":"))
