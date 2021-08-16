(ns nextjournal.ui.components.promises
  (:require [nextjournal.view :as v]
            [kitchen-async.promise :as p]
            [reagent.core :as r]))

(v/defview view [{:keys [promise
                         on-value on-error on-loading]}]
  (r/with-let [promise promise
               result (r/atom {:loading? true})
               _ (p/try (p/->> promise (hash-map :value) (reset! result))
                        (p/catch js/Error e (reset! result {:error e})))]
    (let [{:keys [value error loading?]} @result]
      (cond value [on-value value]
            error [on-error error]
            loading? [on-loading]))))
