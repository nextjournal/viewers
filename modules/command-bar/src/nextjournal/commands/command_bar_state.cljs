(ns nextjournal.commands.command-bar-state
  (:require [applied-science.js-interop :as j]
            [clojure.set :as set]
            [clojure.string :as str]
            [kitchen-async.promise :as p]
            [nextjournal.commands.core :as commands]
            [nextjournal.commands.state :as commands.state]
            [nextjournal.commands.fuzzy :as fuzzy]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; util

(defn some-str [s] (when-not (str/blank? s) s))

(defn find-first [pred coll]
  (reduce (fn [_ x] (when (pred x) (reduced x))) nil coll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; categories / shortlists

(defn more-command [{:as category :keys [id title]} commands shortlist]
  {:id (keyword (name id) "more")
   :title "More"
   :slug title
   :panel-title title
   :category title
   :subcommands (fn [_]
                  {:commands commands
                   :selected (->> commands
                                  (take-while (comp (into #{} (map :id) shortlist) :id))
                                  count)})})

(defn resolve-category-commands [registry context {:as category :keys [title id]}]
  (assoc category :commands (->> (commands/resolve-commands registry context (or (:commands category)
                                                                                 (get-in registry [:categories id :commands])))
                                 (mapv #(assoc % :category title)))))

(defn resolve-category-shortlist [{:as category :keys [commands shortlist]}]
  (let [shortlist (if shortlist
                    (into []
                          (keep (fn [cmd]
                                  (if (keyword? cmd)
                                    (find-first #(= (:id %) cmd) commands)
                                    cmd)))
                          shortlist)
                    (vec (take (if (= 6 (count commands)) 6 5) commands)))
        more? (seq (set/difference (into #{} (map :id) commands)
                                   (into #{} (map :id) shortlist)))]
    (assoc category :shortlist (cond-> shortlist
                                 more?
                                 (conj (more-command category commands shortlist))))))

;; state access

(defn subcommand-state
  ([view-state] (-> view-state :stack last))
  ([view-state stack-key]
   (find-first #(= (:stack-key %) stack-key) (:stack view-state))))

(defn subcommands-loading? [view-state]
  (some-> view-state subcommand-state :loading?))

(defn update-subcommand-state [view-state stack-key f & args]
  (update view-state
          :stack
          (partial mapv (fn [cmd]
                          (if (= stack-key (:stack-key cmd))
                            (apply f cmd args)
                            cmd)))))

(defn update-current-subcommand-state [view-state f & args]
  (cond-> view-state
          (seq (:stack view-state))
          (update-in [:stack (dec (count (:stack view-state)))]
                     (fn [cmd] (apply f cmd args)))))

(def get-selected (comp :selected subcommand-state))

(defn set-selected! [view-state i]
  (update-current-subcommand-state view-state assoc :selected i))

(def get-query (comp some-str :subcommands/query subcommand-state))

(defn set-query! [!view-state value]
  (swap! !view-state update-current-subcommand-state assoc :subcommands/query value)
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; view-state

(declare evaluate-subcommands!)
(defn initial-state
  "Given initial props, sets up view-state"
  [props]
  (doto (reagent/atom nil)
    (add-watch ::compute-subcommands
               ;; recompute subcommands whenever the last command in the stack
               ;; has changed, or its query has changed (for commands that
               ;; opt in with :subcommands/uses-query?)
               (fn [_ !view-state old new]
                 (let [prev (subcommand-state old)
                       next (subcommand-state new)
                       signature (if (:subcommands/uses-query? next)
                                   (juxt :stack-key :subcommands/query)
                                   :stack-key)]
                   (when (and next
                              (not= (signature prev)
                                    (signature next)))
                     (swap! !view-state
                            update-current-subcommand-state
                            evaluate-subcommands!
                            !view-state)))))
    (reset! (-> props
                (update :categories commands/normalize-categories)
                (update :stack #(or % []))
                (update :registry #(or % (commands.state/get-registry)))))))

(defn resting-state
  "Disables command-bar while keeping initial-state"
  [state]
  (-> state
      (select-keys [:categories
                    :shortcuts
                    :registry
                    :element])
      (assoc :stack [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; search/filter

(defn apply-search [query commands]
  (if-let [query (some-> query
                         str/lower-case)]
    (->> commands
         (keep (fn [cmd]
                 (let [prefix (some-> (:category cmd) some-str (str " "))
                       text (str/lower-case (str prefix (:title cmd)))
                       {:as result :fuzzy/keys [chars score]} (fuzzy/match {:query query :text text})]
                   (when (pos? score)
                     (let [[prefix-chars chars] (split-with #(< % (count prefix)) chars)
                           chars (into [] (map #(- % (count prefix))) chars)]
                       (merge cmd (assoc result :fuzzy/chars chars :fuzzy/category-chars prefix-chars))))))))
    commands))

(def search-categories
  {:invisible-stack? true
   :subcommands/uses-query? true
   :subcommands (fn [{:as context :keys [!view-state]}]
                  (let [{:as view-state :keys [categories registry]} @!view-state
                        query (get-query view-state)]
                    (->> categories
                         (into []
                               (comp (filter #(commands/requirements-satisfied? context (:requires %)))
                                     (mapcat #(-> (resolve-category-commands registry context %)
                                                  (update :commands (partial apply-search query))
                                                  resolve-category-shortlist
                                                  ((if query :commands :shortlist)))))))))})

(defn candidates [view-state]
  (let [{:keys [commands subcommands/uses-query?]} (some-> view-state subcommand-state)]
    (cond->> commands
             (not uses-query?)
             (apply-search (get-query view-state))
             true
             (map-indexed #(assoc %2 :result-index %1)))))

(defn evaluate-subcommands!
  [{:as command :keys [stack-key]} !view-state]
  (let [{:keys [context]} @!view-state
        command (assoc command :context context)
        context (cond-> context
                        (:subcommands/uses-query? command)
                        (assoc :subcommands/query (:subcommands/query command)))
        result* (commands/resolve-subcommands context command)]
    (if (commands/promise? result*)
      (let [request-key (random-uuid)]
        (p/then result*
                (fn [result]
                  (when (= (:request-key (subcommand-state @!view-state stack-key)) request-key) ;; ignore result for stale requests
                    (swap! !view-state update-subcommand-state stack-key merge result {:loading? false}))))
        (assoc command :loading? true :request-key request-key))
      (merge command result*))))

(defn ^:export add-to-stack! [context commands]
  (let [{:keys [!view-state]} context
        ;; adds context to view-state when opening the command bar, doesn't
        ;; overwrite context if already present
        _ (swap! !view-state update :context #(or % context))
        commands (->> (if (map? commands) [commands] commands)
                      (keep identity)
                      (mapv #(-> %
                                 (assoc :stack-key (random-uuid))
                                 (evaluate-subcommands! !view-state))))]
    (swap! !view-state
           (fn [st]
             (-> st
                 (assoc :context context)
                 (update :stack (fn [stack]
                                  (if
                                   ;; clear existing stack if adding to stack directly from keyboard-invocation
                                   (= :keys (:event/trigger context))
                                    commands
                                    (into stack commands)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; control opening/closing of the command palette.

(defn- command-input [view-state]
  (some-> (:element view-state)
          (j/call :querySelector "input")))

(defn focus-bar-input! [view-state]
  (js/requestAnimationFrame
   #(some-> (command-input view-state)
            (j/call :focus))))

(defn active-stack? [view-state]
  (boolean (seq (:stack view-state))))

(defn activate-bar! [!view-state & [opts]]
  (when (or opts (not (active-stack? @!view-state)))
    (let [context (commands.state/current-context opts)]
      (add-to-stack! context search-categories)))
  !view-state)

(defn refocus! [view-state]
  (if-let [view @(re-frame/subscribe [:db/get :com.nextjournal.editor.doc/view])]
    (re-frame/dispatch [:perform-fx {:com.nextjournal.editor.doc/focus view}])
    (some-> (command-input view-state) (j/call :blur))))

(defn ^:export blur-command-bar! [!view-state]
  (swap! !view-state resting-state))

(defn toggle-bar! [!view-state]
  (if (active-stack? @!view-state)
    (blur-command-bar! !view-state)
    (activate-bar! !view-state)))
