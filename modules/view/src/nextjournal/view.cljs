(ns nextjournal.view
  (:require [applied-science.js-interop :as j]
            [re-frame.context :as re-frame]
            [re-frame.frame :as frame]
            [reagent.core :as r]
            [reagent.impl.component :as c]
            [reagent.ratom :as ratom])
  (:require-macros [nextjournal.view :as v]))

(defn memoized-frame-fn
  ;; only create these partially-applied functions once per component.
  ;; same outcome as `with-let`
  [component ^string memo-key ctx-function]
  (j/get component memo-key
         (let [f (partial ctx-function (j/get component :context))]
           (j/assoc! component memo-key f)
           f)))

(defn get-key
  "Reads a key from `component`"
  [component k not-found]
  (case k ::props (r/props component)
          ::state (r/state-atom component)
          ::argv (r/argv component)

          ::re-frame/frame (j/get component :context)
          ::re-frame/subscribe (memoized-frame-fn component "rf_subscribe" frame/subscribe)
          ::re-frame/dispatch (memoized-frame-fn component "rf_dispatch" frame/dispatch)
          ::re-frame/dispatch-sync (memoized-frame-fn component "rf_dispatch_sync" frame/dispatch-sync)

          ;; add other keys here

          ;; fall back to reading keys from props
          (get (r/props component) k not-found)))

(defn wrap-render [f]
  ;; the render-function is always be passed `this` as the first argument,
  ;; followed by child elements, NOT including a props map - props can be read
  ;; from `this`
  (fn [c]
    (let [argv (c/get-argv c)
          props? (some? (r/props c))
          n (count argv)
          first-child (if props? 2 1)
          extra-children (if props? (- n 2) (- n 1))]
      (case extra-children
        0 (.call f c c)
        1 (.call f c c (nth argv first-child))
        2 (.call f c c (nth argv first-child) (nth argv (+ 1 first-child)))
        3 (.call f c c (nth argv first-child) (nth argv (+ 1 first-child)) (nth argv (+ 2 first-child)))
        4 (.call f c c (nth argv first-child) (nth argv (+ 1 first-child)) (nth argv (+ 2 first-child)) (nth argv (+ 3 first-child)))
        (.apply f c (.slice (into-array argv) 1))))))

(defn constructor-fn [{:keys [constructor ::initial-state]}]
  (j/fn [^js this ^:js {:as props :syms [argv]}]

    (specify! this
      ILookup
      (-lookup
        ([o k] (get-key o k nil))
        ([o k not-found] (get-key o k not-found))))

    ;; initial-state can be provided statically in methods-map, or passed in as a prop (eg. for devcards)
    (when-some [initial-state (or (some-> argv (c/extract-props) ::initial-state)
                                  initial-state)]
      (let [state (if (fn? initial-state) (initial-state this) initial-state)
            state-atom (cond-> state
                         (not (instance? ratom/RAtom state)) r/atom)]
        (set! (.-cljsState this) state-atom)))

    (when constructor (constructor this props))
    this))

(defn wrap-methods [m]
  (-> m
      (assoc :constructor (constructor-fn m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Silent updates
;;
;; (useful where you want to update a view's state-atom
;;  without triggering an update)

(defonce ^:dynamic *notify-watches?*
  ;; patch Reagent to enable disabling of reactive updates
  ;; TODO - verify that this works in advanced.
  ;;        can't use ratom/notify-w, b/c it is private.
  (do (when (exists? js/window)
        (j/update-in! js/window [.-reagent .-ratom .-notify-w]
                      (fn [notify-w]
                        (fn [this old new]
                          (when (true? *notify-watches?*)
                            (notify-w this old new))))))
      true))

(defn swap-silently!
  "Swap a reactive atom, without causing dependent components to re-render."
  [& args]
  (v/silently (apply swap! args)))

(defn reset-silently!
  [ratom value]
  (v/silently (reset! ratom value)))

