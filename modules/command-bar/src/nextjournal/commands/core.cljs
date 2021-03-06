;; forked from https://github.com/piranha/keybind
;; ISC License: https://github.com/piranha/keybind/blob/master/LICENSE.txt
(ns nextjournal.commands.core
  (:require [applied-science.js-interop :as j]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.commands.state :as state]
            [nextjournal.commands.keys :as keys]
            [kitchen-async.promise :as p]
            [nextjournal.commands.fuzzy :as fuzzy]
            [nextjournal.log :as log]
            [re-frame.context :as re-frame]
            [reagent.core :as reagent]))

(def separator {:id :separator
                :action identity})

(defn get-command
  ([id]
   (get-command (:commands (state/get-registry)) id))
  ([commands id]
   (if (keyword? id)
     (commands id)
     id)))

(defn context-for-command
  "returns context for command, if it can be applied.
   A command's `when` function can return a map, which is merged into context."
  ([command]
   (context-for-command (state/current-context) command))
  ([context command]
   (let [command (get-command command)
         context (merge context (:context command))]
     (if-let [when (:when command)]
       (when-let [ctx (when context)]
         (cond-> context
                 (map? ctx)
                 (merge ctx)))
       context))))

(defn resolve-with-context [context f]
  (if (fn? f) (f context) f))

(defn apply-context
  "Applies context to a command
  (some attributes of commands, like title and search index,
   are derived from context)"
  ([command] (apply-context (state/current-context) command))
  ([context command]
   (let [{:as command :keys [title]} (get-command command)
         command-context (context-for-command context command)
         title (resolve-with-context (or command-context context) title)]
     (assoc command
       :active? (some? command-context)
       :disabled? (not command-context)
       :title title
       :context command-context))))

(defn eval-command
  "Evaluates a command if possible given the current context"
  ([command] (eval-command command (or (:context command) (state/current-context))))
  ([command context]
   (let [{:as command :keys [context]} (apply-context context (get-command command))
         {:keys [action prevent-default? blur-command-bar?]
          :or {prevent-default? true
               blur-command-bar? true}} command]
     (when-not (:disabled? command)
       (let [result (action context)]
         (when (and prevent-default?
                    (instance? js/Event (:event context))
                    (not= :pass result))
           (.preventDefault (:event context)))
         (when blur-command-bar?
           ;; avoiding circular reference between core <> view-state
           (j/call-in js/window [.-nextjournal .-commands .-command-bar-state .-blur-command-bar!] (:!view-state context)))
         result)))))

(defn dispatch [e state]
  (let [path (-> e keys/e->chord keys/chord->path)
        ids (:ids (get-in state path))]
    (log/trace :path path :ids ids)
    (when (seq ids)
      (let [context (state/current-context
                      {:event e
                       :event/trigger :keys
                       :event/sequence sequence})]
        (doseq [id ids
                :let [result (eval-command id context)]
                :while (not= :stop result)])))))

(defn id->title [id]
  (-> (name id)
      (str/split #"-")
      (vec)
      (update 0 str/capitalize)
      (->> (str/join " "))))

(declare normalize-command)

(defn format-subcommand-result
  [context command result]
  (when result
    (let [result (if (map? result) result {:commands result})]
      (-> result
          (update :commands
                  (partial mapv #(-> (normalize-command %)
                                     ;; adds context to command without checking :requires etc.
                                     ;; (as we pass-through subcommands without any filtering)
                                     ;; the reason we always attach :context to each command is because
                                     ;; our :requires key supports returning additional context for a
                                     ;; particular command, so we can't use global context for eval'ing a command

                                     (update :context (fn [c] (or c context))))))
          (update :selected #(when (seq (:commands result)) (or % 0)))
          ;; subcommands can return a dynamic title, or inherit from parent command
          (update :title #(or % (:title command)))))))

(defn promise? [x] (j/contains? x :then))

(defn resolve-subcommands [context command]
  (let [{:as command :keys [subcommands]} (get-command command)]
    (cond (keyword? subcommands) (resolve-subcommands context (merge command (get-command subcommands)))
          (fn? subcommands) (when-let [context (context-for-command context command)]
                              (let [result (subcommands context)]
                                (cond (keyword? result) (resolve-subcommands context (merge command (get-command result)))
                                      (some-> result promise?) (p/->> result (format-subcommand-result context command))
                                      :else (format-subcommand-result context command result))))
          :else
          (format-subcommand-result context command subcommands))))

(defn resolve-commands
  ;; returns flat list of commands suitable for sorting.
  ;; traverses subcommands, passes down parent context to subcommands (when applicable).
  [registry context commands]
  (let [expand-subcommands (fn [{:as command :keys [context] :subcommands/keys [include-in-search? async?]}]
                             (if (and include-in-search? (not async?))
                               (->> (:commands (resolve-subcommands context command))
                                    (resolve-commands registry context)
                                    (cons command))
                               [command]))]
    (into [] (comp (map (partial get-command (:commands registry)))
                   (filter (every-pred some? (complement :private?)))
                   (map #(apply-context context %))
                   (remove :disabled?)
                   (mapcat expand-subcommands))
          commands)))

(declare resolve-action)

(defn requirements-satisfied? [context requires]
  (if (empty? requires) true ((apply every-pred requires) context)))

(defn resolve-action
  "Set the action function of the command (handles special cases for :dispatch and :subcommands)"
  [{:as command :keys [action dispatch subcommands]}]
  (assert (= 1 (count (keep identity [action dispatch subcommands])))
          (str "Command must have exactly one of #{:action, :dispatch, :subcommands} " (:id command)))
  (assert (or (nil? dispatch) (vector? dispatch) (fn? dispatch)))

  (cond action command

        dispatch
        (-> command
            (dissoc :dispatch)
            (assoc :action
                   (fn [context]
                     (re-frame/dispatch (resolve-with-context context dispatch)))))

        subcommands
        (-> command
            (assoc :blur-command-bar? false
                   :action
                   (fn [context]
                     (j/call-in js/window [.-nextjournal .-commands .-command-bar-state .-add-to-stack!] context command))))
        :else
        command))

(defn normalize-command [command]
  (if (keyword? command)
    (if (= :separator command)
      separator
      (get-command command))
    (let [{:keys [category id title keys when requires normalized?]} command]
      (if normalized?
        command
        (-> command
            (merge
              (some-> keys (keys/parse-keys))
              {:normalized? true
               :title (or title (some-> id id->title))
               :category (or category (some-> id namespace keyword))
               :when (fn [ctx]
                       (and
                         (requirements-satisfied? ctx requires)
                         (if when (when ctx) true)))})
            resolve-action)))))

(defn add-to-category [registry category-id id]
  (update-in registry [:categories category-id :commands] (fnil (comp vec distinct conj) []) id))

(defn remove-from-category [registry category-id id]
  (update-in registry [:categories category-id :commands] (fnil (partial remove #{id}) [])))

(defn register* [registry id command]
  (let [command (normalize-command (assoc command :id id))
        registry (-> registry
                     (assoc-in [:commands id] command)
                     (add-to-category (:category command) id))]
    (if (:bind-keys? command true)
      (reduce (fn [registry sequence]
                (update-in registry (conj sequence :ids) (fnil (comp distinct conj) []) id))
              registry
              (:keys/paths command))
      registry)))

(defn register
  "pure function for adding command to context"
  ([db commands]
   (update db ::state/registry #(reduce-kv register* % commands)))
  ([db id command]
   (update db ::state/registry register* id command)))

(defn deregister* [registry id]
  (let [command (get-in registry [:commands id])
        registry (-> registry
                     (update :commands dissoc id)
                     (remove-from-category (:category command) id))]
    (if (:bind-keys? command true)
      (reduce (fn [registry sequence]
                (update-in registry (conj sequence :ids) (fnil (partial remove #{id}) [])))
              registry
              (:keys/paths command))
      registry)))

(defn deregister
  ([db id]
   (if (coll? id)
     (update db ::state/registry #(reduce deregister* % id))
     (update db ::state/registry deregister* id))))

(defn normalize-categories [categories]
  (->> categories
       (mapv
         (fn [cat]
           {:pre [(or (keyword? cat) (map? cat))]}
           (let [[id category] (if (keyword? cat) [cat] (first cat))]
             (-> category
                 (assoc :id id)
                 (update :title (fn [x] (or x (-> id name str/capitalize))))))))))

;; Main external API

(re-frame/reg-event-db
  ::register
  (fn [db [_ commands]]
    (register db commands)))

(re-frame/reg-event-db
  ::deregister
  (fn [db [_ commands]]
    (deregister db commands)))

(defn register!
  "Binds a sequence of button presses, specified by `keys`, to `action` when
  pressed. Keys must be unique per `keys`, and can be used to remove keybinding
  with `unbind!`.

  `keys` format is emacs-like strings a-la \"ctrl-c k\", \"meta-shift-k\", etc."
  ([commands]
   (re-frame/dispatch [::register commands]))
  ([id command]
   (re-frame/dispatch [::register {id command}])))

(defn deregister! [commands]
  (re-frame/dispatch [::deregister commands]))

;; TODO
;; I think I found a bug where viewing the Collaborators modal
;; has a side-effect of clearing out the :article-entity subscription

(defn bind-event!
  "Binds an existing re-frame event as a command. Accepts same options as `register!`."
  [id command]
  (register! id (assoc command :dispatch [id])))

(defn listen!
  "Begin responding to `keydown` events on `element` (default: window)

  Accepts options which are mainly used only in dev / devcards situations.
  :element - only take action when a provided dom element is focused
  :get-registry - function to return the current registry, called for every event"
  [{:keys [pred]
    :or {pred (constantly true)}}]
  ;; expects the "correct" frame to be bound
  (let [f (re-frame/bind-fn #(when (pred)
                               (dispatch % (state/get-registry))))]
    (js/addEventListener "keydown" f false)
    #(js/removeEventListener "keydown" f)))

(defn listener
  "A component which listens for keyboard events, for the registry in the current app-db."
  [& body]
  (reagent/with-let [unlisten (listen! {:get-registry (re-frame/bind-fn state/get-registry)})]
    (into [:<>] body)
    (finally
     (unlisten))))

(defn mount [commands]
  (binding [re-frame.registry/*current-frame* re-frame.core/default-frame]
    (let [register! (re-frame/bind-fn register!)
          deregister! (re-frame/bind-fn deregister!)]
      (reagent/with-let [_ (register! commands)]
        (finally (deregister! (keys commands)))))))