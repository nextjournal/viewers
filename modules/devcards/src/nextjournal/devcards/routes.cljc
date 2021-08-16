(ns nextjournal.devcards.routes
  (:require [clojure.string :as str]))

(def routes
  {"/nextjournal/devcards" :devcards/root
   "/nextjournal/devcards/:ns" :devcards/by-namespace
   "/nextjournal/devcards/:ns/:name" :devcards/by-name})

(def routes-reverse (zipmap (vals routes) (keys routes)))

(defn replace-in-string
  "returns s with replacements - for each k-v in params, replaces (str k) with v"
  [s replacements]
  (reduce-kv (fn [s k v]
               (str/replace s (str k) v)) s replacements))

(defn url-for
  ([name]
   (url-for name {}))
  ([name params]
   (-> (routes-reverse name)
       (replace-in-string params))))


