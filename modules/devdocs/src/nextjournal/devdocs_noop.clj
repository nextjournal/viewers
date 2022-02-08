(ns nextjournal.devdocs-noop)

(defmacro defcollection
  [name opts paths]
  nil)

(defmacro only-on-ci
  "Only include the wrapped form in the build output if CI=true."
  [& body]
  (when (System/getenv "CI")
    `(do ~body)))
