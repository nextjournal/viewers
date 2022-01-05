(ns nextjournal.devdocs
  "Embedded Markdown documentation and pre-built Clerk notebooks.

  See [[devdoc-collection]]."
  (:require [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.walk :as walk]
            [lambdaisland.shellutils :as shellutils]
            [nextjournal.markdown :as markdown]
            [nextjournal.markdown.transform :as markdown.transform]
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

(defn render-clerk
  "Takes the path to a Clerk notebook, returns a `:toc` based on the headers in
  the markdown comments, and a `:view` (Hiccup)."
  [file]
  (let [evaled (clerk/eval-file file)]
    {:toc (->> evaled
               (filter (comp #{:markdown} :type))
               (map :text)
               str/join
               markdown/parse
               :toc)
     :view `[nextjournal.clerk.sci-viewer/inspect
             (try
               (nextjournal.clerk.sci-viewer/read-string
                ~(->> evaled
                      (clerk-view/doc->viewer {:inline-results? true})
                      clerk-view/->edn))
               (catch :default e#
                 (js/console.error :clerk.sci-viewer/read-error e#)
                 "Parse error..."))]}))

(defn read-cljs-form [&env s]
  (binding [*ns* (create-ns (:name (:ns &env)))
            reader/*alias-map* (apply merge
                                      ((juxt :requires :require-macros)
                                       (:ns &env)))]
    (reader/read-string s)))

(defn render-markdown
  "Takes a markdown string, returns a `:toc` based on the headers in the markdown,
  and a `:view` (Hiccup). The options `:cljs-eval?` and `:view-source?`
  determine how code blocks are rendered. If `:view-source?` is true the code
  blocks themselves are rendered, if `:cljs-eval?` is true then their code is
  inlined into the ClojureScript build. The result is rendered inline
  via [[nextjournal.viewer/inspect]]. Both can be true."
  [&env markdown {:keys [cljs-eval? view-source?]}]
  (let [{:as doc :keys [toc]} (markdown/parse markdown)]
    {:toc toc
     :view [:div.flex.flex-col.items-center.viewer-notebook
            [:div.viewer.viewer-markdown.w-full.max-w-prose.px-8.overflow-x-auto
             (markdown.transform/->hiccup
              (assoc markdown.transform/default-hiccup-renderers :code
                     (fn [ctx node]
                       [:<>
                        (when view-source?
                          [:div.viewer-code.not-prose
                           `[nextjournal.viewer/inspect
                             (nextjournal.viewer/view-as :code ~(markdown.transform/->text node))]])
                        (when cljs-eval?
                          `[nextjournal.viewer/inspect
                            ~(read-cljs-form &env (markdown.transform/->text node))])]))
              doc)]]}))

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
          resource? true}
     :as opts}
    paths]
   ;; Not pretty but it means we can run and inline notebooks as part of a
   ;; `shadow-cljs release`. It might be cleaner to move to a separate build
   ;; step which just handles Clerk and writes EDN to disk, and pick that up
   ;; here.
   (let [collection-id (or slug (slugify name))]
     `(swap! registry
             assoc
             ~collection-id
             {:id ~collection-id
              :title ~name
              :clerk? ~clerk?
              :cljs-eval? ~cljs-eval?
              :devdocs
              ~(vec (for [{:keys [path slug] :as path-opts} paths
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
                            {:keys [toc view]} (if clerk?
                                                 (render-clerk path)
                                                 (render-markdown
                                                  &env
                                                  (shadow-resource/slurp-resource &env path)
                                                  (merge opts path-opts)))
                            title (or (get-in toc [:content 0 :title])
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
                          :collection-id ~collection-id
                          :file-size ~(.length file)
                          :last-modified ~(let [ts (str/trim (:out (sh/sh "git" "log" "-1" "--format=%ct" (str file))))]
                                            (when (not= "" ts)
                                              (* (Long/parseLong ts) 1000)))
                          :view ~view})))}))))

(defmacro show-card
  "Show an existing devcard, simply pass its fully qualified name, unquoted.

  For use inside devdoc markdown files with `:cljs-eval? true`

  Make sure the card is loaded before it's referenced, by requiring the
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

(defmacro only-on-ci
  "Only include the wrapped form in the build output if CI=true."
  [& body]
  (when (System/getenv "CI")
    `(do ~body)))
