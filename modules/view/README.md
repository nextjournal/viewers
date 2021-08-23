# nextjournal.view

See also `nextjournal/devcards/README.md` and the devcards and source in
`nextjournal.view.expo`.

This package provides a single macro, `defview`, which defines "views". These
are really just Reagent components, but with some extra affordances.

- First class component-level state. Instead of closing over an atom the
  component itself contains a state atom that can be accessed.
- Provide `::initial-state` declaratively when defining the component
- Allow overriding `::initial-state` when using the component, via a prop
- Freerange "frame"-aware, provides access to subscribe/dispatch functions
  that operate on the active frame.

Note: there is also another `defview` used in this project, provided by
[Triple](https://github.com/mhuebert/triple), and intended for use with React
Native. This is a different macro with different implementation and behavior,
but serving a similar purpose.

## Basic syntax

Looks much like defining a Reagent "form-1" component with a regular `defn`

```clojure
(require '[nextjournal.view :as v])

(v/defview my-component [_]
  [:h1 "Hello world"])
  
(reagent-dom/render [my-component] element)
```

Like `defn` you can optionally pass in a docstring and an attribute-map (here
referred to as "methods", because it allows specifying react/reagent methods,
but also other things, we'll get to that).

```clojure
(v/defview my-component 
  "A truly super-duper component" ;; docstring
  {}                              ;; methods/attributes map
  [this]                          ;; argv
  [:h1 "Hello world"])            ;; body
```

The `argv` and `body` are combined to form the view/component render function.

## Working with `this`

When a `defview` is rendered, its render function gets called. The first
argument it gets passed is always `this`, i.e. the view instance. This object
implements `ILookup`, meaning you can destructure it like a map.

Some keys get special treatment, like `::v/state` or `::re-frame/dispatch`. All
other keys are simply looked up in the properties map passed to the component, so syntactically properties lookup looks the same as it does in Reagent.

```clojure
(v/defview takes-prop
  [{:keys [some-prop]}]
  [:p "The prop is: " some-prop])
  
[takes-prop {:some-prop "Hello"}]
```

Note that even though we are destructuring `this` as if it's a map, it is not
actually a map, and trying to perform other map operations on it like `assoc`,
`dissoc`, etc. will fail. If you want the actual properties map you can look up
`::v/props`.

```clojure
(v/defview explicit-props
  [{props ::v/props}]
  [:p "I got these props: " (pr-str (keys props))])
  
[explicit-props {:some-prop "Hello"}]
;; I got these props: (:some-prop)
```

## Props and children

You can still pass children to the component as you would in Reagent, and they
will be passed as successive positional arguments to the render function. The
main difference is that with `defview` the first argument is *always* `this`
(the view instance), even when no properties map is passed.

```clojure
(defn reagent-children-no-props [child other-child]
  [:<>
   [:p "Hello children"]
   child
   other-child])

(v/defview children-no-props [this child other-child]
  [:<>
   [:p "Hello children"]
   child
   other-child])
```

You can access the full argument vector used to render the component (including
the component function itself in the first position), with `::v/argv`.

## Component-local state

The standard way of doing component-local state in Reagent is by closing over a
Reagent-atom. `defview` in contrast has a per-view-instance state atom built-in.
This allows it to provide a unified interface for injecting initial state from
the outside. Great for devcards!

```clojure
(v/defview basic-state
  {::v/initial-state 0}
  [{state ::v/state}]
  [:p
   [:button.form-button.mr-4 {:on-click #(swap! state inc)} "inc"]
   "Count: " (str @state)])
   
[:<>
 [:section
  [:p "Default initial state (0):"]
  [basic-state*]]
 [:section
  [:p "Injected initial state (42):"]
  [basic-state* {::v/initial-state 42}]]]
```

## Working with Freerange

In Re-frame everything is global. There is a single app-db, a single registry of
subscriptions and event handlers, a single event loop, and so forth. Our fork of
Re-frame called Freerange addresses this by adding the concept of a "frame". A
frame contains a single instance of an app-db, a registry, an event loop.

This has big benefits for testing (easier to test with specific localized state
and handlers), and for devcards.

At render time the active frame is made available via a React context.
Components that `subscribe` or `dispatch` should use frame-aware versions of
these functions to make sure they aren't coupled to the default global frame.
`defview` provides easy access to these functions.

```clojure
;; Make sure to use this namespace, not re-frame.core
(require '[re-frame.context :as re-frame])

(re-frame/reg-event-db ::inc (fn [db] (update db ::count (fnil inc 0))))
(re-frame/reg-sub ::count (fn [db _] (::count db 0)))

(v/defview using-frame [{::re-frame/keys [subscribe dispatch]}]
  [:p
   [:button.form-button.mr-4 {:on-click #(dispatch [::inc])} "inc"]
   "Count: " (str @(subscribe [::count]))])
```

Now from devcards you can specify the initial re-frame db:

```clojure
(require '[nextjournal.devcards :as dc])

(dc/defcard frame-based-counter
  ;; Empty argv is needed here, or defcard thinks `[using-frame]` is the argv
  []
  [using-frame]
  {::count 5})
```

You can also access `::re-frame/dispatch-sync`, and `::re-frame/frame` (access the raw fram directly from the React context).

