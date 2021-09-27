(ns nextjournal.commands.state
  (:require [re-frame.context :as re-frame]
            [reagent.core :as reagent]))

(re-frame/reg-sub
  :db/get-in
  (fn [db [_ path not-found]]
    (get-in db path not-found)))

(re-frame/reg-sub
  :db/get
  (fn [db [_ key]]
    (get db key)))

(re-frame/reg-sub :desktop? (fn [_db] (not (<= (.-innerWidth js/window) 768))))

(re-frame/reg-event-db
 :commands/set-context
 (fn [db [_ k value]]
   (js/console.log :set-context db value)
   (let [!mutable-context (::!context db)]
     (if (some? value)
       (swap! !mutable-context assoc k value)
       (swap! !mutable-context dissoc k)))
   db))

(re-frame/reg-event-db
 :commands/unset-context
 (fn [db [_ k value :as event]]
   (let [!mutable-context (::!context db)]
     (case (count event)
       2 (swap! !mutable-context dissoc k)
       3 (when (identical? value (@!mutable-context k))
           (swap! !mutable-context dissoc k))))
   db))

;; the registry stores keybindings, commands, categories, and
;; dynamic context functions.

(defn get-registry []
  @(re-frame/subscribe [:db/get ::registry]))

(defn empty-db!
  "Returns the required entries for re-frame's app-db, used by the commands system"
  []
  {::registry (get-registry)                                ;; include commands registered before init
   ::!context (reagent/atom {})})

;; Context provides the inputs to commands, and is the basis for
;; determining if a command can fire or not. Context is a map.

;; set/unset-context is used to maintain 'mutable' context, eg
;; often controlled by views mounting and unmounting.

(defn get-context-atom []
  @(re-frame/subscribe [:db/get ::!context]))

(defn set-context!
  [k value]
  (re-frame/dispatch [:commands/set-context k value]))

(defn unset-context!
  ([k]
   (re-frame/dispatch [:commands/unset-context k]))
  ([k value]
   (re-frame/dispatch [:commands/unset-context k value])))

(re-frame/reg-event-db ::register-context-fn
                       (fn [db [_ k context-fn]]
                         (assoc-in db [::registry ::context-fns k] context-fn)))

;; we can also register 'dynamic' context functions. Each fn
;; is called and its result merged with mutable-context whenever
;; we read the `current-context`.
(defn register-context-fn!
  "Adds context-fn for dynamic context that will be merged into context map at read-time"
  [k f]
  (re-frame/dispatch [::register-context-fn k f]))

(defn dynamic-context [context-fns mutable-context]
  (->> (vals context-fns)
       (reduce (fn [m f] (merge m (f mutable-context))) mutable-context)))

(re-frame/reg-sub :commands/reactive-context
  :<- [:db/get-in [::registry ::context-fns]]
  :<- [:db/get ::!context]
  (fn [[context-fns !context] _]
    (dynamic-context context-fns @!context)))

(defn current-context
  "Returns current context"
  ([] (current-context nil))
  ([initial-context]
   (current-context @(re-frame/subscribe [:db/get-in [::registry ::context-fns]])
                    @(get-context-atom)
                    initial-context))
  ([context-fns mutable-context initial-context]
   (dynamic-context context-fns (merge mutable-context initial-context))))
