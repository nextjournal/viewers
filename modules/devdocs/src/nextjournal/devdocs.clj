(ns nextjournal.devdocs
  "Embedded Markdown documentation and pre-built Clerk notebooks.

  See [[collection]]."
  (:require [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.walk :as walk]
            [nextjournal.markdown :as markdown]
            [nextjournal.markdown.transform :as markdown.transform]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk-view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.devcards :as dc]
            [shadow.resource :as shadow-resource]))

(defn slugify [s]
  (some-> s
          (str/lower-case)
          (str/replace #"[^a-z0-9~@$*]+" " ")
          (str/trim)
          (str/replace "_" "-")
          (str/replace " " "-")))

(defn path->slug [path]
  (-> path
      io/file
      .getName
      (str/replace #"\.(clj(.?)|md)$" "")))

#_(path->slug "notebooks/data_mappers.clj")
#_(path->slug "notebooks/data_mappers.cljc")
#_(path->slug "notebooks/data_mappers.md")

(defn file->doc
  "Takes the path to a Clerk notebook, returns a `:toc` based on the headers in
  the markdown comments, and a `:view` (Hiccup)."
  [file {:keys [eval?]
         :or {eval? true}}]
  (let [{:as doc :keys [blocks]} (if eval?
                                   (clerk/eval-file file)
                                   (clerk/parse-file file))]
    (-> doc
        (dissoc :blocks)
        (assoc :edn-doc (clerk-view/->edn (clerk-view/doc->viewer {:inline-results? true} doc))))))


#_(file->doc "docs/clerk.clj" {})
#_(file->doc "docs/frontend.md" {:eval? false})

(defmacro defcollection
  "Create a Devdoc Collection out of a set of Markdown documents

  Takes a name for the collection, a map of options, and a sequence of paths to
  markdown files, which must be on the classpath, and are resolved relative to
  the classpath.

  Documents should start with a `h1`, this is taken as the title of the
  document, and also used to determine the URL slug of the document."
  [name opts paths]
  ;; Not pretty but it means we can run and inline notebooks as part of a
  ;; `shadow-cljs release`. It might be cleaner to move to a separate build
  ;; step which just handles Clerk and writes EDN to disk, and pick that up
  ;; here.
  (let [collection-id (slugify name)]
    `(swap! registry
            assoc
            ~collection-id
            {:id ~collection-id
             :title ~name
             :devdocs
             ~(vec (for [{:keys [path slug] :as path-opts} paths
                         :let [file (io/file path)
                               exists? (.exists file)
                               _ (when (not exists?)
                                   (println "WARN: Devdoc devdoc not found: " path))]
                         :when exists?]
                     (let [{:keys [toc title edn-doc]} (file->doc path opts)
                           devdoc-id (or slug (slugify (or title (path->slug path))))
                           title (or title (-> devdoc-id (str/replace #"[-_]" " ") str/capitalize))]
                       `{:id ~devdoc-id
                         :toc ~toc
                         :title ~title
                         :path ~path
                         :collection-id ~collection-id
                         :file-size ~(.length file)
                         :last-modified ~(let [ts (str/trim (:out (sh/sh "git" "log" "-1" "--format=%ct" path)))]
                                           (when (not= "" ts)
                                             (* (Long/parseLong ts) 1000)))
                         :edn-doc ~edn-doc})))})))

(defmacro only-on-ci
  "Only include the wrapped form in the build output if CI=true."
  [& body]
  (when (System/getenv "CI")
    `(do ~body)))
