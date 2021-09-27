(ns nextjournal.devdocs
  "Embedded Markdown documentation

  See [[devdoc-collection]]."
  (:require [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [lambdaisland.shellutils :as shellutils]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.view :as clerk-view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.devcards :as dc]
            [shadow.resource :as shadow-resource]))

(defn- slugify [s]
  (-> s
      (str/lower-case)
      (str/replace #"[^a-z0-9~@$*]+" " ")
      (str/trim)
      (str/replace " " "-")))

(defn- split-markdown
  "Split markdown into code blocks and regular markdown blocks, returns a
  collection of `[:code strings...]` and `[:markdown string...]`."
  [lines]
  (loop [[line & lines] lines
         in-code? false
         result []
         current-block [:markdown]]
    (if line
      (if (re-find #"^ *```" line)
        (recur lines
               (not in-code?)
               (conj result current-block)
               [(if in-code? :markdown :code)])
        (recur lines
               in-code?
               result
               (conj current-block line)))
      (conj result current-block))))

(defn md->toc
  "Naively find header lines in the markdown, and return a nested ToC structure
  accordingly. Returns nested map of `:level`/`:title`/`:children`"
  [blocks]
  (let [headers (mapcat
                 (fn [[type & lines]]
                   (when (= :markdown type)
                     (filter #(.startsWith % "#") lines)))
                 blocks)
        update-last-child (fn [m f & args]
                            (let [m (if (empty? (:children m))
                                      (update m :children conj {:children []})
                                      m)]
                              (apply update-in m
                                     [:children (dec (count (:children m)))]
                                     f args)))
        update-level (fn update-level [m lvl f & args]
                       (if (= lvl 1)
                         (apply f m args)
                         (apply update-last-child m
                                update-level (dec lvl) f args)))]
    (reduce
     (fn [res [level title]]
       (update-level res level
                     update :children
                     conj {:level level
                           :title title
                           :children []}))
     {:children []}
     (for [header headers
           :let [level (count (re-find #"^#+" header))]]
       [level (str/trim (subs header level))]))))

(defmacro devdoc-collection
  "Create a Devdoc Collection out of a set of Markdown documents

  Takes a name for the collection, a map of options, and a sequence of paths to
  markdown files, which must be on the classpath, and are resolved relative to
  the classpath.

  Documents should start with a `h1`, this is taken as the title of the
  document, and also used to determine the URL slug of the document.

  By default code blocks are simply rendered as code-blocks. The `:cljs-eval?`
  option also inlines the code block code, so it becomes part of the build and
  gets evaluated when viewing the document. `:view-source?` controls whether
  to (also) show code blocks as code (defaults to true)."
  ([name
    {:keys [cljs-eval? view-source? clerk? resource? slug]
     :or {cljs-eval? false
          view-source? true
          clerk? false
          resource? true}}
    paths]
   ;; Not pretty but it means we can run and inline notebooks as part of a
   ;; `shadow-cljs release`. It might be cleaner to move to a separate build
   ;; step which just handles Clerk and writes EDN to disk, and pick that up
   ;; here.
   (let [coll-id (or slug (slugify name))]
     (if (and clerk? (not (System/getenv "DUCTILE_STATIC_CLERK")))
       `(do)
       `(swap! registry
               assoc
               ~coll-id
               {:id ~coll-id
                :title ~name
                :clerk? ~clerk?
                :cljs-eval? ~cljs-eval?
                :devdocs
                ~(vec (for [{:keys [path slug]} paths
                            :let [exists? (or (and resource? (io/resource path))
                                              (.exists (io/file path)))
                                  _ (when (not exists?)
                                      (println "WARN: Devdoc devdoc not found: " path))]
                            :when exists?]
                        (let [file (shellutils/canonicalize (if resource?
                                                              (io/resource path)
                                                              path))
                              file (shellutils/relativize (shellutils/canonicalize "")
                                                          file)
                              blocks (if clerk?
                                       (mapcat (fn [{:keys [type text result]}]
                                                 (case type
                                                   :markdown
                                                   [[:markdown text]]
                                                   :code
                                                   [[:code text]
                                                    [:data result]]))
                                               (clerk/eval-file path))
                                       (-> path
                                           (->> (shadow-resource/slurp-resource &env))
                                           (str/split #"\R")
                                           split-markdown))
                              toc (md->toc blocks)
                              title (or (get-in toc [:children 0 :title])
                                        (-> path
                                            shellutils/basename
                                            shellutils/strip-ext
                                            (str/replace #"[-_]" " ")
                                            str/capitalize))
                              devdoc-id (or slug (slugify title))]
                          `{:id ~devdoc-id
                            :toc ~toc
                            :title ~title
                            :path ~(str file)
                            :collection-id ~coll-id
                            :file-size ~(.length file)
                            :last-modified ~(let [ts (str/trim (:out (sh/sh "git" "log" "-1" "--format=%ct" (str file))))]
                                              (when (not= "" ts)
                                                (* (Long/parseLong ts) 1000)))
                            :view [nextjournal.viewer/inspect
                                   ~(into (v/view-as :clerk/notebook [])
                                          (mapcat
                                           (fn [[type & rest]]
                                             (case type
                                               :markdown
                                               [(v/view-as :markdown (str/join "\n" rest))]
                                               :code
                                               (cond-> []
                                                 view-source?
                                                 (conj (v/view-as :code (str/join "\n" rest)))
                                                 cljs-eval?
                                                 (conj (read-string (str/join "\n" rest))))
                                               :data
                                               (let [result (first rest)]
                                                 [`(try
                                                     (cljs.reader/read-string
                                                      {:default identity}
                                                      ~(clerk-view/->edn result))
                                                     (catch :default e#
                                                       "Parse error..."))]))))
                                          blocks)]})))})))))

(defmacro show-card
  "Show an existing devcard, simply pass its fully qualified name, unquoted.

  For use inside devdoc markdown files with `:cljs-eval? true`

  Make sure the care is loaded before it's referenced, by requiring the
  namespace where it's defined, if necessary.

  A second `opts` argument will be merged with the cards map. It can be used
  to hide the title and description by setting `::dc/title?` and/or
  `::dc/decription?` to `false`.
  "
  ([card-name]
   (with-meta
     `[nextjournal.devcards-ui/show-card (get-in @nextjournal.devcards/registry [~(namespace card-name) ~(name card-name)])]
     (merge {:nextjournal/viewer :reagent} (meta &form))))
  ([card-name opts]
   (with-meta
     `[nextjournal.devcards-ui/show-card (merge (get-in @dc/registry [~(namespace card-name) ~(name card-name)]) ~opts)]
     (merge {:nextjournal/viewer :reagent} (meta &form)))))

(defmacro only-on-ci [& body]
  (when (System/getenv "CI")
    `(do ~body)))
