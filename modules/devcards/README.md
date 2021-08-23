# nextjournal.devcards

See also `nextjournal/view/README.md` and the devcards and source in
`nextjournal.view.expo`.

A reimplementation of Bruce Hauman's devcards.

- UI which integrates with the command palette
- Integration with Freerange (allow overriding the re-frame db)
- Works well with `nextjournal.view/defview`
- Defcard-local state (dedicated ratom) for stateful devcards
- Nextjournal inspector for devcard-local state or for freerange DB

``` clojure
(require '[nextjournal.devcards :as dc])
```

We provide two macros, `dc/defcard` and `dc/defcard-nj`. They are largely
equivalent, but with some small differences in syntax.

## Syntax

The basic syntax:

``` clojure
(dc/defcard some-name
  [:h1 "hello"])
```

The complete syntax:

```clojure
(dc/defcard some-name
  "The docstring. Can be omitted but shouldn't be. Will be rendered as markdown"
  []              ;; argv, only needed for stateful cards, in which 
                  ;; case it receives a single state atom as an
                  ;; argument. Can be omitted*
  [:h1 "hello"]   ;; body
  {})             ;; Initial re-frame db, or set state with ::dc/state
  
  
(dc/defcard-nj other-card
  "The docstring. Can be omitted but shouldn't be. Will be rendered as markdown"
  [:h1 "hello"]   ;; body, can also be a one-arg function
  {}              ;; Initial defcard state
  {})             ;; Initial re-frame db
```

In the basic case you simply pass a hiccup form to show in the devcard, and
probably a docstring to go with it so people know what they are looking at.

## DB and defcard state

Devcards provide support for state on two different levels. Each `defcard` can
have its own state similar to how each `defview` has its own state, it gets a
dedicated ratom that you can initialize and access.

There is also first-class support for re-frame (freerange) app-db state. If you
are using Freerange-based components (i.e. `defview` which pulls
`subscribe`/`dispatch` out of the view instance), then you can pass a freerange
initial DB value after the hiccup form. This DB will also be shown underneath
the card in a Nextjournal inspector.

How the state / database are initialized, and how the state is accessed, differ
slightly between `defcard` and `defcard-nj`.

With `defcard` you pass the app-db initial state immediately after the hiccup
form. If you want to provide an initial state for `defcard`'s own ratom then you
use `::dc/state` inside that same map.

``` clojure
(dc/defcard stateful-card [state]
  [:pre "State:" (pr-str @state)]
  {:this-goes-in "the re-frame app-db"
   ::dc/state {:this-is "defcard state"}})
   
;;=> State: {:this-is "defcard state"}
```

Notice also how in this case I added an argument vector (`[state]`) to access
the defcard state, which I can then `swap!`/`reset`/`deref`.

With `defcard-nj` the local state and app-db state are provided as separate
maps.

``` clojure
(dc/defcard-nj stateful-card
  [:p "my card"]
  {:this-is "defcard state"}
  {:this-goes-in "the re-frame app-db"})
```

In this case the syntax does not allow for an argument vector, if you want to
gain access to the local state you need to pass a one-arg function instead of
hiccup.

``` clojure
(dc/defcard-nj stateful-card
  (fn [state]
    [:pre "State:" (pr-str @state)])
  {:this-is "defcard state"}
  {:this-goes-in "the re-frame app-db"})
```

There is one important caveat, in that `dc/defcard` sometimes mistakes hiccup
for an argument vector. Consider this:

``` clojure
(v/defview my-component [this]
  [:h1 "hello"])
  
(dc/defcard show-it-off
  [my-component])
```

`defcard` will treat the first vector it is given that consists exclusively out
of symbols as an argument vector, so it will think that this defcard has an argv
of `[my-component]` and a hiccup body of `nil`.

This problem does not occur if there's a non-symbol in there.

``` clojure
(v/defview my-component [this msg]
  [:h1 msg])
  
(dc/defcard show-it-off
  [my-component "hello"])
```

It can also be prevented by explicitly adding an argv.

```clojure
(dc/defcard show-it-off []
  [my-component])
```

  
