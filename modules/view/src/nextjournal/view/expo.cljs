(ns nextjournal.view.expo
  (:require [nextjournal.view :as v]
            [nextjournal.devcards :as dc]
            [ductile.ui.components :as components]
            [re-frame.core :as rf-core]
            [re-frame.context :as re-frame]
            [re-frame.frame :as rf-frame])
  (:require-macros [nextjournal.view.expo :refer [view-source]]))

(dc/defcard intro
  "This namespace provides an exposition of the `defview` and `defcard` macros,
  the forms they take and the things they can do.

  These are part of vendored Nextjournal modules, `nextjournal.view` and
  `nextjournal.devcards`.

  These devcards are best looked at side by side with the view implementations.
  They will make most sense after perusing `nextjournal/view/README.md` and
  `nextjournal/devcards/README.md`" nil)

(v/defview my-first-view
  [_]
  [:h1 "Hello world"])

(dc/defcard basic-syntax
  "Basic view with a docstring, argument vector, and Hiccup body.

  ```clojure
  (v/defview name \"?docstring\" {?methods} [argv] body)
  ```"
  []
  [:<>
   (view-source my-first-view)
   [:hr.p-2]
   [my-first-view]])

(v/defview takes-prop*
  [{:keys [some-prop]}]
  [:p "The prop is: " some-prop])

(dc/defcard takes-prop
  "When rendering, the first argument passed into the `view` is the view
  instance (`this`), which can be destructured to look up properties. So
  accessing values from a properties map works transparently the way it does in
  Reagent, but there's some indirection via the view instance, which allows us
  to treat certain keys special."
  [:<>
   (view-source takes-prop*)
   [:pre [:code (pr-str [takes-prop* {:some-prop "Hello"}])]]
   [:hr.p-2]
   [takes-prop* {:some-prop "Hello"}]])

(v/defview explicit-props*
  [{props ::v/props}]
  [:p "I got these props: " (pr-str (keys props))])

(dc/defcard explicit-props
  "The first argument passed to the view can be destructured as if it's a
   properties map, but it is not a regular map. If you want the actual properties
   map, look up `::v/props`"
  [:<>
   (view-source explicit-props*)
   [:pre [:code (pr-str [explicit-props* {:some-prop "Hello"}])]]
   [:hr.p-2]
   [explicit-props* {:some-prop "Hello"}]])

(v/defview basic-state*
  {::v/initial-state 0}
  [{state ::v/state}]
  [:p
   [:button.form-button.mr-4 {:on-click #(swap! state inc)} "inc"]
   "Count: " (str @state)])

(dc/defcard basic-state
  "Every view instance gets its own state atom, which can be lookup op on the
  instance (first arg to the view) with `::v/state`. This allows `defview` to
  provide a unified intere-frameace for setting the default initial state, and to
  override the initial state (e.g. from a `defcard`)"
  []
  [:<>
   (view-source basic-state*)
   [:hr.p-2]
   [:section
    [:p "Default initial state (0):"]
    [basic-state*]]
   [:section
    [:p "Injected initial state (42):"]
    [basic-state* {::v/initial-state 42}]]])

(defn reagent-children-no-props
  "Reagent form-1 component which simply takes two positional child arguments."
  [child other-child]
  [:<>
   [:p "Hello children"]
   child
   other-child])

(v/defview children-no-props
  "Notice the difference with the Reagent form-1 version, with `defview` there
  is always a initial `this` argument, even when there is no properties map
  passed in."
  [this child other-child]
  [:<>
   [:p "Hello children"]
   child
   other-child])

(dc/defcard passing-children-but-no-properties
  "This card illustrates a subtle distinction between reagent form-1 components
  and defview. Consult the source for details."
  [:<>
   (view-source reagent-children-no-props)
   [reagent-children-no-props [:p "Lewie"] [:p "Dewie"]]
   [:hr.p-2]
   (view-source children-no-props)
   [children-no-props [:p "Lewie"] [:p "Dewie"]]])

(defn register-into-frame [frame]
  (rf-frame/reg-event-db frame ::inc (fn [db] (update db ::count (fnil inc 0))))
  (rf-frame/reg-sub frame ::count [(fn [db _] (::count db 0))]))

;; Only try to register events when it is possible to do so.
(when rf-core/default-frame
  (register-into-frame rf-core/default-frame))

(v/defview using-frame [{::re-frame/keys [subscribe dispatch]}]
  [:p
   [:button.form-button.mr-4 {:on-click #(dispatch [::inc])} "inc"]
   "Count: " (str @(subscribe [::count]))])

(dc/defcard frame-based-counter
  "This version of the counter component uses the re-frame app-db instead of
  component-local state. It accesses `subscribe` and `dispatch` by destructing
  the view instance (`this`), so that the component honors the current frame.
  This way we can now set a specific initial db value inside the defcard."
  ;; Empty argv is needed here, or defcard thinks `[using-frame]` is the argv
  []
  [:<>
   (view-source using-frame)
   [:hr.p-2]
   [using-frame]]
  {::count 5})

(v/defview meta-argv [{argv ::v/argv}]
  [:<>
   [:p "The argument vector that rendered this component:"]
   [:pre
    [:code.language-clojure
     (pr-str argv)]]])

(dc/defcard accessing-argv
  "The `::v/argv` key gives access to the full argument vector used to render the
   component."
  []
  [:<>
   (view-source meta-argv)
   [:pre [:code (pr-str [meta-argv {:random "prop"} :child :other-child])]]
   [:hr.p-2]
   [meta-argv {:random "prop"} :child :other-child]])

(dc/defcard stateful-card-plain
  "Defcard with its own state, it gets initialized via the options
  map (::dc/state), and the state atom gets passed as an argument."
  [state]
  [:<>
   (view-source stateful-card-plain)
   [:hr.p-2]
   [:p
    [:button.form-button.mr-4 {:on-click #(swap! state update ::count inc)} "inc"]
    "Count: " (::count @state)]]
  {::dc/state {::count 0}})

(dc/defcard-nj stateful-card-nj
  "The defcard-nj version does not have first class support for an argument
  vector for injecting the state, instead you use a one-arg function. The
  initial state is passed immediately afterwards, and can still be optionally
  followed by a freerange initial-db"
  (fn [state]
    [:<>
     (view-source stateful-card-nj)
     [:hr.p-2]
     [:p
      [:button.form-button.mr-4 {:on-click #(swap! state update ::count inc)} "inc"]
      "Count: " (::count @state)]])
  {::count 0}
  {:re-frame-db :not-currently-used})
