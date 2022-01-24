# A Devcards Notebook
```
^{:nextjournal.clerk/visibility :hide}
(ns compound
  (:require [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk :as clerk]))

(clerk/set-viewers! [{:pred #(and (map? %) (contains? % :nextjournal.devcards/id))
                      :transform-fn (fn [{:nextjournal/keys [width] :nextjournal.devcards/keys [id]}]
                                      (-> {:nextjournal.devcards/registry-path [(namespace id) (name id)]}
                                          viewer/wrap-value
                                          (assoc :nextjournal/width (or width :wide))))
                      :fetch-fn (fn [_ x] x)
                      :render-fn '(fn [{:nextjournal.devcards/keys [registry-path]}]
                                    (v/html [nextjournal.devcards-ui/show-card (assoc (get-in @nextjournal.devcards/registry registry-path)
                                                                                      :nextjournal.devcards/title? false
                                                                                      :nextjournal.devcards/description? false)]))}])
```
This notebook extends Clerk default browser environment to make the
devcards available.

```
{:nextjournal.devcards/id 'nextjournal.clerk.sci-viewer/inspect-paginated-more}
```

You can also customize the width.

```
{:nextjournal.devcards/id 'nextjournal.clerk.sci-viewer/inspect-paginated-more :nextjournal/width :normal}
```
