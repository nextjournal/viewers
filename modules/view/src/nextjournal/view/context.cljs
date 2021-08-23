(ns nextjournal.view.context
  "The following provide and consume Reagent components expose React contexts without fuss.

  * Contexts can be keywords or React context instances. In the case of keywords, React context instances are created behind the scenes.
  * Context values are left alone, they remain as JS or Clojure values (no coercion).
  * Ratoms inside consume work as you'd expect.
  * You can provide multiple contexts at the same time, but you can only c/consume one context at a time.

  example use:

  ``` clojure
  ;; Optional, provide a fallback when no c/provide is available in the render tree.
  (c/set-default :app-theme {:color \"red\"})

  [c/provide {:app-theme {:color \"blue\"}}
   ;; consume one context at a time
   [c/consume :app-theme
    (fn [{:keys [color] :as _theme}]
      [:div {:style {:color color}} \"Colorful Text\"])]]
  ```

  From https://gist.github.com/mhuebert/d400701f7eddbc4fffa811c70178a8c1"
  (:require ["react" :as react]
            [reagent.core :as reagent]
            [nextjournal.log :as log]))

(defonce contexts (atom {}))

(defn set-default
  "Set the default value for a given context, identified by keyword.

  This should only be called at the top level, before rendering, since it will
  replace any existing context with the same key."
  [context-key default-value]
  (swap! contexts
         (fn [cs]
           (when (get cs context-key)
             (log/warn :replacing-context {:context-key context-key
                                           :default-value default-value}))
           (assoc cs context-key (react/createContext default-value)))))

(defn get-context [context-or-key]
  (if (keyword? context-or-key)
    (or (get @contexts context-or-key)
        (do
          (set-default context-or-key nil)
          (get @contexts context-or-key)))
    context-or-key))

(defn provide
  "Adds React contexts to the component tree.
  `bindings` should be a map of {<keyword-or-Context>, <value-to-be-bound>}."
  [bindings & body]
  (loop [bindings (seq bindings)
         out (->> body
                  (reduce (fn [a el] (doto a (.push (reagent/as-element el)))) #js [])
                  (.concat #js [react/Fragment #js {}])
                  (.apply react/createElement nil))]
    (if (empty? bindings)
      out
      (recur (rest bindings)
             (let [[context-or-key v] (first bindings)
                   ^js context (get-context context-or-key)]
               (react/createElement (.-Provider context)
                                    #js {:value v}
                                    out))))))

(defn consume
  "Reads a React context value within component tree.
  `context` should be a keyword or React Context instance."
  [context f]
  ;; This is a weird hack, but apparently there's no really obvious better way
  ;; to address this. The issue is that `f` is typically defined inline, it's
  ;; probably closing over some values, so it's going to be a different function
  ;; on every invocation/render. If we use it directly as a reagent component
  ;; like [f value] then reagent/react will treat it as a different component
  ;; type on every render, and won't reuse any DOM elements beneath it in the
  ;; tree. This is most apparent with things like mapbox where it really matters
  ;; that we don't draw a new map on every render. But if we instead call the
  ;; passed in function directly instead of treating it like a component, then
  ;; we lose reactivity, i.e. changing reagent atoms which are (de)referenced in
  ;; the function won't cause the component to re-render. The work around is to
  ;; create a "static" wrapper component (`wrapper`) with `with-let` so to React
  ;; it looks like it's always the same component, and we have a component to
  ;; stick in Hiccup and get reactivity. Then in there we call the most recent
  ;; `f` to do the actual rendering. Doesn't look great but it does all it needs
  ;; to do.
  (reagent/with-let [box (atom f)
                     wrapper (fn [& args]
                               (apply @box args))]
    (react/createElement (.-Consumer (get-context context))
                         #js {}
                         (fn [value]
                           (reset! box f)
                           (reagent/as-element [wrapper value])))))
