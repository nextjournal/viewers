(ns nextjournal.devdocs
  "Embedded (pre-built) Clerk notebooks."
  (:require [babashka.fs :as fs]
            [clojure.java.shell :as sh]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as clerk.hashing]
            [nextjournal.clerk.view :as clerk.view]
            [nextjournal.clerk.viewer :as clerk.viewer]))

(defn doc-path->edn-path [path]
  (str "build/devdocs/" (str/replace (str path) fs/file-separator "|") ".edn"))

#_(doc-path->edn-path "docs/clerk/clerk.md")
#_(doc-path->edn-path "docs/clerk/clerk.clj")

(defn path->title [path]
  (-> path fs/file-name (str/split #"_")
      (->> (map str/capitalize)
           (str/join " "))))

;; FIXME: visibility is only assigned when blocks are evaluated
(defn assign-visibility [{:as doc :keys [visibility]}]
  (update doc
          :blocks
          (partial into []
                   (map (fn [{:as b :keys [type text]}]
                          (cond-> b
                            (= :code type)
                            (assoc :result {:nextjournal/viewer :hide-result
                                            ::clerk/visibility
                                            (or (try (clerk.hashing/->visibility (read-string text)) (catch Exception _ nil))
                                                visibility)})))))))

(defn doc-info->edn [{:keys [path eval?]}]
  (-> (clerk/parse-file (fs/file path))
      (cond->
        eval? clerk/eval-doc
        (not eval?) assign-visibility)
      (->> (clerk.view/doc->viewer {:inline-results? true}))
      clerk.view/->edn))

(defn guard [p? val] (when (p? val) val))
(defn file->doc-info [path]
  (-> (clerk/parse-file (fs/file path))
      (select-keys [:title :doc])
      (assoc :path (str path)
             :last-modified (when-some [ts (-> (sh/sh "git" "log" "-1" "--format=%ct" (str path)) :out str/trim not-empty)]
                              (* (Long/parseLong ts) 1000))
             :edn-doc
             (if-some [edn-path (guard fs/exists? (doc-path->edn-path path))]
               (do (println "Found cached EDN doc at" edn-path (str "(size: " (fs/size edn-path) ")"))
                   (slurp edn-path))
               (doc-info->edn {:path path :eval? false})))))

#_(file->doc-info "docs/clerk/clerk.clj")

(defn doc-path->path-in-registry [registry folder-path]
  (let [index-of-matching (fn [r] (first (keep-indexed #(when (str/starts-with? folder-path (:path %2)) %1) (:collections r))))]
    (loop [r registry nav-path [:collections]]
      (if-some [idx (index-of-matching r)]
        (recur (get-in r (conj nav-path idx)) (conj nav-path idx :collections))
        nav-path))))

(defn add-collection [registry [path coll]]
  (if (str/blank? path)
    (assoc registry :devdocs coll)
    (update-in registry
               (doc-path->path-in-registry registry path)
               (fnil conj []) {:title (path->title path)
                               :path path
                               :devdocs coll})))

#_(-> {:collections [{:title "x" :path "x"}]}
      (add-collection ["foo" [:a]])
      (add-collection ["foo/bar" [:b]])
      (add-collection ["foo/bar/dang" [:c]])
      (add-collection ["foo/caz" [:d]]))

(defn expand-paths [paths]
  (mapcat (partial fs/glob ".")
          (if (symbol? paths)
            (when-some [ps (some-> paths requiring-resolve deref)]
              (cond-> ps (fn? ps) (apply [])))
            paths)))

#_(expand-paths ["docs/**.{clj,md}"
                 "README.md"
                 "modules/devdocs/src/nextjournal/devdocs.clj"])

(defmacro build-registry
  "Populates a nested registry of parsed notebooks given a set of paths."
  [{:keys [paths]}]
  (->> paths
       expand-paths
       (map file->doc-info)
       (group-by (comp str fs/parent :path))
       (sort-by first)
       (reduce add-collection {:title "DevDocs"})))

#_(letfn [(strip-edn [coll] (-> coll
                                (update :devdocs (partial into [] (map #(dissoc % :edn-doc))))
                                (update :collections (partial into [] (map strip-edn)))))]
    (strip-edn
     (build-registry {:paths ["docs/**/*.{clj,md}"
                              "README.md"
                              "modules/devdocs/src/nextjournal/devdocs.clj"]})))

(defn build!
  "Expand paths and evals resulting notebooks with clerk. Persists EDN results to fs at conventional path (see `doc-path->cached-edn-path`)."
  [{:keys [paths ignore-cache?]}]
  (doseq [path (expand-paths paths)]
    (println "started building notebook" (str path))
    (let [edn-path (doc-path->edn-path path)]
      (if (and (fs/exists? edn-path) (not ignore-cache?))
        (println "Found cached EDN doc at" edn-path)
        (try
          (let [{edn-str :result :keys [time-ms]} (clerk/time-ms (doc-info->edn {:path path :eval? true}))]
            (println "finished building notebook" path "in" time-ms "ms, writing" (count edn-str) "chars EDN to" edn-path)
            (when-not (fs/exists? (fs/parent edn-path)) (fs/create-dirs (fs/parent edn-path)))
            (spit edn-path edn-str))
          (catch Exception e
            (println "failed building notebook" path "with" (ex-message e) "continuing...")
            (println "caused by " (ex-cause e))
            (stacktrace/print-stack-trace e) {}))))))

(comment
  (build! {:paths ["docs/**/*.{clj,md}" "README.md"]})
  (fs/delete-tree ".clerk/devdocs"))
