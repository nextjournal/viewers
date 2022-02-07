(ns nextjournal.devcards)

(defmacro when-enabled [& body]
  (when-not (= "true" (System/getenv "NEXTJOURNAL_DEVCARDS_ELIDE"))
    `(do ~@body)))

(defn register-devcard! [vname opts]
  (let [ns-str (str *ns*)
        name-str (name vname)]
    `(when-enabled
         (~'nextjournal.devcards/register-devcard*
          (assoc ~opts :ns ~ns-str :name ~name-str))
       ~(str ns-str "/" name-str))))

(defn parse-optional-preds
  "Return a list of bindings corresponding to `preds`.
   - when an argument doesn't match a predicate, `nil` is included in its place,
     then we try the next predicate."
  [preds args]
  (loop [args args
         preds preds
         out []]
    (cond (or (empty? preds)
              (empty? args)) (into out args)

          ((first preds) (first args)) (recur (rest args)
                                              (rest preds)
                                              (conj out (first args)))
          :else (recur args (rest preds) (conj out nil)))))

(defn cljs?
  "Returns true if this macro is called in a ClojureScript context."
  [env]
  (some? (:ns env)))

(defmacro defcard
  {:arglists '([name ?doc ?argv body ?data])}
  [& args]
  (let [[name
         doc
         argv
         body
         data] (parse-optional-preds [ident? string? (every-pred vector? #(every? symbol? %))] args)
        main (if argv `(fn ~argv ~body) body)]
    (when (cljs? &env)
      (register-devcard! name
                         {:data `(fn [] ~data)
                          :doc doc
                          :compile-key (hash data)
                          :main `(fn [] ~main)}))))

(defmacro defcard-nj
  {:arglists '([name ?doc main ?initial-data ?options])
   :depcrecated true}
  [& exprs]
  (let [[name doc main initial-data options] (parse-optional-preds [ident? string?] exprs)]
    (when (cljs? &env)
      (register-devcard! name {:data `(fn [] (merge {::state ~initial-data} ~options))
                               :doc doc
                               :main `(fn [] ~main)}))))
