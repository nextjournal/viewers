(ns nextjournal.devdocs
  "Embedded (pre-built) Clerk notebooks."
  (:require [babashka.fs :as fs]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as clerk.hashing]
            [nextjournal.clerk.view :as clerk.view]
            [nextjournal.clerk.viewer :as clerk.viewer]))

(defn doc-path->edn-path [path] (str ".clerk/devdocs/" (str path ".edn")))

(defn path->title [path]
  (-> path fs/file-name (str/split #"_")
      (->> (map str/capitalize)
           (str/join " "))))

(defn file->doc-info [path]
  (-> (clerk/parse-file (fs/file path))
      (select-keys [:title :doc])
      (assoc :path (str path))))

(defn path-info->collection [{:as opts :keys [path pattern]}]
  (let [subcollections (into [] (comp (filter fs/directory?)
                                      (map #(path-info->collection (assoc opts :path %)))
                                      (remove (comp empty? :devdocs)))
                             (fs/list-dir path))]
    (cond-> {:path (str path)
             :title (path->title path)
             :devdocs (into []
                            (comp (filter (comp (if pattern
                                                  #(re-find (cond-> pattern (string? pattern) re-pattern) (str %))
                                                  (constantly true))))
                                  (map file->doc-info))
                            (fs/glob path "*.{clj,cljc,md}"))}
      (seq subcollections)
      (assoc :collections subcollections))))

#_(path-info->collection {:path "docs"})
#_(path-info->collection {:path "dev/ductile/insights"})
#_(path-info->collection {:path "src/re_db" :pattern #"notebook"})

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
  (-> (clerk/parse-file path)
      (cond->
        eval? clerk/eval-doc
        (not eval?) assign-visibility)
      (->> (clerk.view/doc->viewer {:inline-results? true}))
      clerk.view/->edn))
#_(doc-info->edn {:path "docs/clerk/clerk.md" :eval? false})

(defn collections->paths [colls]
  (->> colls
       (mapcat (fn [{:keys [devdocs collections]}]
                 (cond-> (map :path devdocs)
                   collections (concat (collections->paths collections)))))))
#_(collections->paths [(path-info->collection {:path "docs"})])

(defn guard [p? val] (when (p? val) val))
(defn +viewer-edn [{:as opts :keys [path]}]
  (assoc opts
         :edn-doc
         (if-some [edn-path (guard fs/exists? (doc-path->edn-path path))]
           (do (println "Found cached EDN doc at" edn-path)
               (slurp edn-path))
           (doc-info->edn (assoc opts :eval? false)))))

(defn hydrate-docs [registry]
  (mapv #(-> %
             (update :devdocs (partial mapv +viewer-edn))
             (update :collections hydrate-docs))
        registry))

(defmacro build-registry
  "Populates a nested registry (a vector of collections) of compiled notebooks structured along the target filesystem fragments.

  `paths` is a collection of maps with `:path` and an optional `:pattern` keys to refine the notebook collection."
  [{:keys [paths]}]
  (->> paths (mapv path-info->collection) hydrate-docs))

(defn build!
  "Takes same options as `build-registry`, evals resulting notebooks with clerk and persists EDN results to fs at conventional path."
  [{:keys [paths]}]
  (doseq [path (->> paths (map path-info->collection) collections->paths)]
    (println "started building notebook" path)
    (let [{edn-str :result :keys [time-ms edn-path]}
          (try
            (assoc (clerk/time-ms (doc-info->edn {:path path :eval? true}))
                   :edn-path (doc-path->edn-path path))
            (catch Exception e
              (println "failed building notebook" path "with" (ex-message e) "continuing...")
              (stacktrace/print-stack-trace e) {}))]
      (when edn-path
        (println "finished building notebook" path "in" time-ms "ms, writing" (count edn-str) "chars EDN to" edn-path)
        (fs/delete-if-exists edn-path)
        (when-not (fs/exists? (fs/parent edn-path)) (fs/create-dirs (fs/parent edn-path)))
        (spit edn-path edn-str)))))

(comment
  (fs/delete-tree ".clerk/devdocs")
  (fs/glob ".clerk/devdocs" "**/*.edn")
  (fs/delete-tree ".clerk/devdocs")
  (collections->paths (map path-info->collection [{:path "docs" :pattern "clerk|devcards"}]))
  (build! {:paths [{:path "docs" :pattern "clerk|devcards"}]}))
