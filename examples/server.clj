(ns server
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.config :as clerk-config]
            [nextjournal.clerk.webserver :as clerk-webserver]
            [nextjournal.clerk.view :as clerk-view]))


(swap! clerk-config/!resource->url assoc "/js/viewer.js" "http://localhost:7779/js/viewer.js")

(def docs
  #{"docs/reference.md" "docs/simple.md" "docs/frontend.md" "docs/clerk.clj"})

(defn doc->html [path]
  (let [paths [path]
        path->doc (into {} (map (juxt identity clerk/file->viewer)) paths)
        path->url (into {} (map (juxt identity #(clerk/strip-index %))) paths)
        static-app-opts {:path->doc path->doc :paths (vec (keys path->doc)) :path->url path->url :current-path path}]
    (clerk-view/->static-app static-app-opts)))

#_(doc->html "resources/docs/clerk.clj")

(defn handler [{:as req :keys [uri]}]
  (when-let [path (docs (subs uri 1))]
    (binding [*ns* (find-ns 'user)]
      {:body (doc->html relative-path)
       :status 200})))
