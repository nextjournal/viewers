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

(declare dirs->config)
(defn dir->config [directory]
  (let [subdirs (filter fs/directory? (fs/list-dir directory))]
    (cond-> {:path (str directory)
             :title (path->title directory)
             :devdocs (mapv file->doc-info (fs/glob directory "*.{clj,md}"))}
      (seq subdirs)
      (assoc :collections (dirs->config subdirs)))))
(defn dirs->config [paths] (mapv dir->config paths))
#_(dir->config "docs")
#_(dirs->config ["docs"])

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

(defn config->paths [cfg]
  (->> cfg
       (mapcat (fn [{:keys [devdocs collections]}]
                 (cond-> (map :path devdocs)
                   collections (concat (config->paths collections)))))))
#_(config->paths (dirs->config ["docs"]))

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
#_(hydrate-docs (dirs->config ["docs"]))

(defmacro build-registry
  "Populates a nested registry of compiled notebooks structured along the targed filesystem fragments."
  [{:keys [paths]}] (-> paths dirs->config hydrate-docs))

(defn build! [paths]
  ;; `dirs->config` actually also filters notebooks (currently path/**/*.{clj,md})
  (doseq [path (config->paths (dirs->config paths))]
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
  (build! ["docs"]))
