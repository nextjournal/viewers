(ns nextjournal.util.macros)

(defmacro for!
  "Like [[for]], but eager.

  Supports additional key `:into` to coerce into a target collection.

  ```
  (for! [v [:x :y :z]]
    v)
  ;;=> (:x :y :z)
  ```
  "
  {:style/indent 1}
  [binding & body]
  (let [pairs (partition 2 binding)
        coll (some #(when (= :into (first %))
                      (second %))
                   pairs)
        binding (into [] cat (remove #(= :into (first %)) pairs))]
    (if coll
      `(into ~coll (for ~binding ~@body))
      `(doall
        (for ~binding
          ~@body)))))
