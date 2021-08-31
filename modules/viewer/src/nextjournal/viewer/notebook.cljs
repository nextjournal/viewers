(ns nextjournal.viewer.notebook
  "Entrypoint for Clerk notebook viewer."
  (:require [cljs.reader]
            [clojure.string :as str]
            [nextjournal.devcards :as dc]
            [nextjournal.log :as log]
            [nextjournal.viewer :as v]
            [nextjournal.viewer.table]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.dom :as rdom]
            [nextjournal.view.context :as context]
            [re-frame.context :as rf]
            [sci.core :as sci]))

(defn notebook [xs]
  (v/html
    (into [:div.flex.flex-col.items-center.viewer-notebook]
          (map #(v/html
                  (let [viewer (-> % v/meta :nextjournal/viewer)]
                    [:div {:class (case viewer
                                    :code "viewer viewer-code"
                                    (:markdown :latex) "viewer viewer-markdown"
                                    (:plotly :vega-lite) "viewer viewer-plot"
                                    "viewer viewer-data overflow-x-auto")}
                     (cond-> [v/inspect %] (:blob/id %) (vary-meta assoc :key (:blob/id %)))]))) xs)))

(defn var [x]
  (v/html [:span.inspected-value
           [:span.syntax-tag "#'" (str x)]]))

(defonce state (ratom/atom nil))
(defn root []
  [v/inspect @state])

(def ^:export read-string
  (partial cljs.reader/read-string {:default identity}))

(defn ^:export mount [el]
  (rdom/render [root] el))

(defn ^:export reset-state [new-state]
  (reset! state new-state))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))

#_(opts->query {:s 10 :num 42})


(defn describe [result]
  (cond-> {:nextjournal/type-key (v/value-type result) :blob/id (-> result meta :blob/id)}
    (counted? result)
    (assoc :count (count result))))

#_(describe (vec (range 100)))

(defn paginate [result {:as opts :keys [start n] :or {start 0}}]
  (log/info :paginate {:start start :n n :result result})
  (if (and (number? n)
           (pos? n)
           (not (or (map? result)
                    (set? result)))
           (counted? result))
    (with-meta (->> result (drop start) (take n) doall) (merge opts (describe result)))
    result))

#_(meta (paginate (vec (range 10)) {:n 20}))
#_(meta (paginate (vec (range 100)) {:n 20}))
#_(meta (paginate (zipmap (range 100) (range 100)) {:n 20}))
#_(paginate #{1 2 3} {:n 20})

(rf/reg-sub
 ::blobs
 (fn [db [blob-key id :as v]]
   (if id
     (get-in db v)
     (get db blob-key))))

(defn fetch! [!result {:blob/keys [id]} opts]
  (log/trace :fetch! opts)
  (-> (js/fetch (str "_blob/" id (when (seq opts)
                                   (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(reset! !result {:value (read-string %)}))
      (.catch #(reset! !result {:error %}))))

(defn in-process-fetch! [!result {:blob/keys [id]} opts]
  (log/trace :in-process-fetch! opts :id id)
  (-> (js/Promise. (fn [resolve _reject]
                     (resolve @(rf/subscribe [::blobs id]))))
      (.then #(paginate % opts))
      (.then #(reset! !result {:value (doto % (log/info :in-process-fetch!/value))}))
      (.catch #(reset! !result {:error %}))))

(defn get-fetch-opts [{:keys [nextjournal/type-key count]}]
  (cond
    (and (number? count)
         (pos? count)
         (contains? #{:list :vector} type-key)
         (not (@v/!viewers type-key))) {:n v/increase-items}))

#_(get-fetch-opts {})
#_(get-fetch-opts {:type-key :vector :count 1000})

(defn blob [blob]
  (r/with-let [!result (r/atom {:loading? true})
               fetch! (partial (:blob/fetch! blob fetch!) !result blob)
               _ (fetch! (get-fetch-opts blob))]
    (let [{:keys [value error loading?]} @!result]
      (cond value (v/view-as :reagent [context/provide {:fetch! fetch!}
                                       [v/inspect value]])
            error (v/html [:span.red "error" (str error)])
            loading? (v/html "loading…")))))

(v/register-viewers! {:clerk/notebook notebook
                      :clerk/var var
                      :clerk/blob blob})



(dc/defcard blob-in-process-fetch
  "Dev affordance that performs fetch in-process."
  (into [:div]
        (map (fn [[blob-id v]] [:div [v/inspect (v/view-as :clerk/blob (assoc (describe (with-meta v {:blob/id blob-id})) :blob/fetch! in-process-fetch!))]]))
        @(rf/subscribe [::blobs]))
  {:blobs (hash-map (random-uuid) (vec (drop 500 (range 1000)))
                    (random-uuid) (range 1000)
                    (random-uuid) (zipmap (range 1000) (range 1000)))})


(def sci-viewer-namespace
  {'html nextjournal.viewer/html
   'view-as nextjournal.viewer/view-as
   'inspect nextjournal.viewer/inspect
   'register-viewer! nextjournal.viewer/register-viewer!
   'register-viewers! nextjournal.viewer/register-viewers!
   'with-viewer nextjournal.viewer/with-viewer
   'with-viewers nextjournal.viewer/with-viewers})


(defonce ctx
  (sci/init {:async? true
             :disable-arity-checks true
             :classes {'js goog/global
                       :allow :all}
             :bindings {'atom ratom/atom}
             :namespaces {'nextjournal.viewer sci-viewer-namespace
                          'v sci-viewer-namespace}}))


(defn eval-form [f]
  (sci/eval-form ctx f))

(set! v/*eval-form* eval-form)

(dc/defcard eval-viewer
  "Viewers that are lists are evaluated using sci."
  [v/inspect (v/with-viewer "Hans" '(fn [x] (v/with-viewer [:h3 "Ohai, " x "! 👋"] :hiccup)))])

(dc/defcard test
  "foo"
  []
  [:h1 "hi"])

(dc/defcard notebook
  "Shows how to display a notebook document"
  [v/inspect (v/view-as :clerk/notebook
                        [(v/view-as :markdown "# Hello Markdown\n## Paragraphs\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum velit nulla, sodales eu lorem ut, tincidunt consectetur diam. Donec in scelerisque risus. Suspendisse potenti. Nunc non hendrerit odio, at malesuada erat. Aenean rutrum quam sed velit mollis imperdiet. Sed lacinia quam eget tempor tempus. Mauris et leo ac odio condimentum facilisis eu sed nibh. Morbi sed est sit amet risus blandit ullam corper. Pellentesque nisi metus, feugiat sed velit ut, dignissim finibus urna.\n## Lists\n\n* List Item 1\n* List Item 2\n* List Item 3\n\n1. List Item 1\n2. List Item 2\n3. List Item 3\n## Blockquotes\n> Hello, is it me you’re looking for?\n>\n>—Lionel Richie")
                         [1 2 3 4]
                         (v/view-as :code "(shuffle (range 10))")
                         {:hello [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
                         (v/view-as :markdown "# And some more\n And some more [markdown](https://daringfireball.net/projects/markdown/).")
                         (v/view-as :code "(shuffle (range 10))")
                         (v/view-as :markdown "## Some math \n This is a formula.")
                         (v/view-as :latex
                                    "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
                         (v/view-as :plotly
                                    {:data [{:y (shuffle (range 10)) :name "The Federation" }
                                            {:y (shuffle (range 10)) :name "The Empire"}]})])])
