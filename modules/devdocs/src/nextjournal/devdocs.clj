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
  (-> path fs/file-name (str/replace #"\.(clj(c?)|md)$" "") (str/split #"_")
      (->> (map str/capitalize)
           (str/join " "))))

#_(path->title "foo/bar")
#_(path->title "foo/bar.cljc")
#_(path->title "foo/bar_besque.md")
#_(path->title "foo/bar.md")

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
      clerk.viewer/->edn))

(defn guard [p? val] (when (p? val) val))
(defn assoc-when-missing [m k v] (cond-> m (not (contains? m k)) (assoc k v)))

(defn file->doc-info [path]
  (-> (clerk/parse-file (fs/file path))
      (select-keys [:title :doc])
      (assoc-when-missing :title (path->title path))
      (assoc :path (str path)
             :last-modified (when-some [ts (-> (sh/sh "git" "log" "-1" "--format=%ct" (str path)) :out str/trim not-empty)]
                              (* (Long/parseLong ts) 1000))
             :edn-doc
             (if-some [edn-path (guard fs/exists? (doc-path->edn-path path))]
               (do (println "Found cached EDN doc at" edn-path (str "(size: " (fs/size edn-path) ")"))
                   (slurp edn-path))
               (doc-info->edn {:path path :eval? false})))))

#_(file->doc-info "docs/clerk/clerk.clj")
#_(file->doc-info "docs/clerk/missing_title.clj")

(defn doc-path->path-in-registry [registry folder-path]
  (let [index-of-matching (fn [r] (first (keep-indexed #(when (str/starts-with? folder-path (:path %2)) %1) (:items r))))]
    (loop [r registry nav-path [:items]]
      (if-some [idx (index-of-matching r)]
        (recur (get-in r (conj nav-path idx)) (conj nav-path idx :items))
        nav-path))))

(defn add-collection [registry [path coll]]
  (if (str/blank? path)
    (assoc registry :items coll)
    (update-in registry
               (doc-path->path-in-registry registry path)
               (fnil conj []) {:title (path->title path)
                               :path path
                               :items coll})))

(comment
  (-> {:items [{:title "x" :path "x"}]}
      (add-collection ["foo" [{:title "xxx" :path "foo/x.clj"} {:title "xx" :path "foo/xx.clj"}]])
      (add-collection ["foo/bar" [{:title "yyy" :path "foo/bar/y.clj"}]])
      (add-collection ["foo/bar/dang" [{:path "foo/bar/dang/z.clj" :title "z"}]])
      (add-collection ["foo/caz" [{:title "w" :path "foo/caz/w.clj"}]])))

(defn excluded? [path]
  (when (str/ends-with? (str path) ".clj")
    (-> path fs/file slurp
        clojure.edn/read-string meta
        :nextjournal.devdocs/exclude?)))

(defn expand-paths [paths]
  (->> (if (symbol? paths)
         (when-some [ps (some-> paths requiring-resolve deref)]
           (cond-> ps (fn? ps) (apply [])))
         paths)
       (mapcat (partial fs/glob "."))
       (remove excluded?)))

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
       (reduce add-collection {})))

#_(letfn [(strip-edn [coll] (-> coll
                                (update :devdocs (partial into [] (map #(dissoc % :edn-doc))))
                                (update :collections (partial into [] (map strip-edn)))))]
    (strip-edn
     (build-registry {:paths ["docs/**/*.{clj,md}"
                              "README.md"
                              "modules/devdocs/src/nextjournal/devdocs.clj"]})))

(defn write-edn-results [_opts docs]
  (doseq [{:as _doc :keys [viewer file]} docs]
    (let [edn-path (doc-path->edn-path file)]
      (when-not (fs/exists? (fs/parent edn-path)) (fs/create-dirs (fs/parent edn-path)))
      (spit edn-path (clerk.viewer/->edn viewer)))))

(defn build!
  "Expand paths and evals resulting notebooks with clerk. Persists EDN results to fs at conventional path (see `doc-path->cached-edn-path`)."
  [{:keys [paths ignore-cache? throw-exceptions?] :or {throw-exceptions? true}}]
  (with-redefs [clerk/write-static-app! write-edn-results]
    (clerk/build-static-app! {:paths (expand-paths paths)})))

(comment
  (shadow.cljs.devtools.api/repl :browser)
  (fs/delete-tree "build/devdocs")

  ;; build devdocs for results to appear in notebooks
  (do
   (clerk/clear-cache!)
   (fs/delete-tree "build/devdocs")
   (build! {:paths ["docs/**.{clj,md}"]})))
