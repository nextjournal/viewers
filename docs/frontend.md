# A Devcards Notebook
```
^{:nextjournal.clerk/visibility :hide}
(ns compound
  (:require [nextjournal.devcards :as devcards]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk :as clerk]))

(clerk/set-viewers! [{:pred #(and (map? %) (contains? % ::devcards/id))
                      :transform-fn (fn [{:nextjournal/keys [width] :nextjournal.devcards/keys [id]}]
                                      (-> {::devcards/registry-path [(namespace id) (name id)]}
                                          viewer/wrap-value
                                          (assoc :nextjournal/width (or width :wide))))
                      :fetch-fn (fn [_ x] x)
                      :render-fn (fn [{:nextjournal.devcards/keys [registry-path]}]
                                   (v/html [nextjournal.devcards-ui/show-card (assoc (get-in @nextjournal.devcards/registry registry-path)
                                                                                     :nextjournal.devcards/title? false
                                                                                     :nextjournal.devcards/description? false)]))}])
```
This notebook extends Clerk default browser environment to make the
devcards available.

```
(clerk/with-viewer (fn [_] @nextjournal.devcards/registry) :foo)
```

```

{:nextjournal.devcards/id 'nextjournal.clerk.sci-viewer/inspect-paginated-more}
```

You can also customize the width.

```
{::devcards/id 'nextjournal.clerk.sci-viewer/inspect-paginated-more :nextjournal/width :normal}
```
