(ns nextjournal.devdocs
  "Embedded (pre-built) Clerk notebooks."
  (:require [babashka.fs :as fs]
            [clojure.edn]
            [clojure.java.shell :as sh]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]))


(def build-path "build/devdocs")
(defn doc-path->edn-path [path] (str (str/replace (str path) fs/file-separator "|") ".edn"))

#_(doc-path->edn-path "docs/clerk/clerk.md")
#_(doc-path->edn-path "docs/clerk/clerk.clj")
#_(doc-path->edn-path "manifest")

(defn path->title [path]
  (-> path fs/file-name (str/replace #"\.(clj(c?)|md)$" "") (str/split #"_")
      (->> (map str/capitalize)
           (str/join " "))))

#_(path->title "foo/bar")
#_(path->title "foo/bar.cljc")
#_(path->title "foo/bar_besque.md")
#_(path->title "foo/bar.md")

(defn hide-results [doc]
  ;; respect visibility also when falling back to just parse
  (update doc
          :blocks
          (partial into []
                   (map (fn [{:as b :keys [type text]}]
                          (cond-> b
                            (= :code type)
                            (assoc :visibility
                                   (merge (try (parser/->visibility (read-string text)) (catch Exception _ nil))
                                          {:result :hide}))))))))

#_ (nextjournal.clerk.eval/eval-file "docs/clerk/clerk.clj")

(defn doc-viewer-edn [{:keys [path]}]
  (-> (parser/parse-file {:doc? true} (fs/file path))
      hide-results
      (->> (view/doc->viewer {:inline-results? true}))
      viewer/->edn))

(defn guard [p? val] (when (p? val) val))
(defn assoc-when-missing [m k v] (cond-> m (not (contains? m k)) (assoc k v)))

(defn file->doc-info [path]
  (-> (parser/parse-file {:doc? true} (fs/file path))
      (select-keys [:title :doc])
      (assoc-when-missing :title (path->title path))
      (assoc :path (str path)
             :edn-cas-url (doc-path->edn-path path)
             :last-modified (when-some [ts (-> (sh/sh "git" "log" "-1" "--format=%ct" (str path)) :out str/trim not-empty)]
                              (* (Long/parseLong ts) 1000)))))

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

(defn expand-paths [paths]
  (->> (if (symbol? paths)
         (when-some [ps (some-> paths requiring-resolve deref)]
           (cond-> ps (fn? ps) (apply [])))
         paths)
       (mapcat (partial fs/glob "."))))

#_(expand-paths ["docs/**.{clj,md}"
                 "README.md"
                 "modules/devdocs/src/nextjournal/devdocs.clj"])

(defn build-registry
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

(defn write-edn-results [{:keys [out-path]} docs]
  (doseq [{:as _doc :keys [viewer file]} docs]
    (let [edn-path (str (fs/path out-path (doc-path->edn-path file)))]
      (when-not (fs/exists? (fs/parent edn-path)) (fs/create-dirs (fs/parent edn-path)))
      (spit edn-path (viewer/->edn viewer)))))

(defn build!
  "Expand paths and evals resulting notebooks with clerk. Persists EDN results to fs at conventional path (see `doc-path->cached-edn-path`)."
  [{:as opts :keys [paths out-path] :navbar/keys [theme] :or {out-path build-path}}]
  ;; build Clerk static app (in EDN format) one file per notebook
  (with-redefs [builder/write-static-app! write-edn-results]
    (builder/build-static-app! {:paths (expand-paths paths) :out-path out-path}))
  ;; Assemble a registry respecting fs hierarchy
  (println "🗂 Building devdocs registry...")
  (spit (fs/file out-path "registry.edn")
        (pr-str (cond-> (build-registry {:paths paths})
                  theme (assoc :navbar/theme theme))))
  (println "done."))

(comment
  (shadow.cljs.devtools.api/repl :browser)
  (fs/delete-tree "build/devdocs")
  (fs/list-dir "build/devdocs")

  ;; get manifest
  (clojure.edn/read-string (slurp (str build-path "/registry.edn")))
  (clerk/clear-cache!)

  ;; build devdocs for results to appear in notebooks
  (build! {:paths ["docs/**.{clj,md}"]
           ;; #_#_
           :out-path "public"
           :navbar/theme {:back "text-[12px] text-slate-300 hover:bg-white/10 font-normal px-5 py-1"
                          :icon "text-slate-400"}})

  ;; registry
  (build-registry {:paths ["docs/**.{clj,md}"]}))
