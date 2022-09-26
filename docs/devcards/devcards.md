# 📇 A Devcards Notebook
```clojure
(ns notebooks.devcards
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk :as clerk]))

^{::clerk/visibility {:result :hide}}
(clerk/add-viewers! [{:pred #(and (map? %) (contains? % :nextjournal.devcards/id))
                      :transform-fn (fn [{:as wrapped-value {:nextjournal/keys [width] :nextjournal.devcards/keys [id]} :nextjournal/value}]
                                      (-> wrapped-value 
                                          (assoc :nextjournal/value {:nextjournal.devcards/registry-path [(namespace id) (name id)]})
                                          (assoc :nextjournal/width (or width :wide))
                                          viewer/mark-presented))
                      :render-fn '(fn [{:nextjournal.devcards/keys [registry-path]}]
                                    (v/html [nextjournal.devcards-ui/show-card (assoc (get-in @nextjournal.devcards/registry registry-path)
                                                                                      :nextjournal.devcards/title? false
                                                                                      :nextjournal.devcards/description? false)]))}])
```
This notebook relies on an [extension](https://github.com/nextjournal/viewers/blob/main/examples/nextjournal/clerk_sci_env.cljs) of Clerk's default browser environment to make the devcards available.

By default, devcards will be displayed with a wide layout

```clojure
{:nextjournal.devcards/id 'nextjournal.ui.components/disclose-button}
```

but you can also customize their width.

```clojure
{:nextjournal.devcards/id 'nextjournal.ui.components/circle-spinner
 :nextjournal/width :normal}
```

```clojure
{:nextjournal.devcards/id 'nextjournal.commands.command-bar/command-bar-grid :nextjournal/width :full}
```
