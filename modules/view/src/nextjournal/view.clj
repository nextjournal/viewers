(ns nextjournal.view)

(defn parse-args [args]
  (let [docstring (if (string? (first args)) (first args) nil)
        args (cond-> args docstring (rest))
        methods (if (map? (first args)) (first args) nil)
        args (cond-> args methods (rest))
        [arglist & body] args]
    [docstring methods arglist body]))

(defn destructured-namespaces
  "Returns names of namespaces of qualified idents in binding"
  [binding]
  (into #{} (comp (filter qualified-ident?)
                  (map namespace))
        (tree-seq coll? seq binding)))

;; the `defview` macro was designed with the opinion that one should
;; avoid putting state in "ratoms" which exist only in closures, and
;; instead prefer to either use the "state atom" of a component, or
;; the global re-frame db. In this way, state is _either_ local to a
;; particular component, or global, and not hidden in a closure.
;;
;; a benefit of this approach is the ability to override local state
;; in a general way -- we can support passing in ::initial-state
;; as a prop. (when local state is defined in closures, every component
;; must implement its own way of supplying defaults to these ratoms)

(defmacro defview
  "Define a reagent view with optional args and methods map.

  The render function will always be passed `this` as the first argument.
  ILookup is implemented for a handful of special keys (see view/get-key),
  other keys fall back to lookups on the props map.

  ::view/state - state atom
  ::view/props - props map (first child, if a map)
  ::view/argv - argv"
  [name & args]
  (let [[docstring methods arglist body] (parse-args args)
        uses-context? ((destructured-namespaces (first arglist)) "re-frame.context")]

    (when uses-context?
      (assert (not (:context-type methods)) "Cannot specify a context-type when destructuring re-frame.context"))

    `(def ~name ~@(when docstring [docstring])
       (~'reagent.core/create-class
         (merge {:display-name ~(str *ns* "/" name)
                 :render (~'nextjournal.view/wrap-render (fn ~name ~arglist ~@body))}
                ~(when uses-context?
                   {:context-type 're-frame.context/frame-context})
                (~'nextjournal.view/wrap-methods ~methods))))))

(defmacro silently
  "Evaluate `body` without triggering Reagent reactive updates"
  [& body]
  `(binding [~'nextjournal.view/*notify-watches?* false]
     ~@body))
