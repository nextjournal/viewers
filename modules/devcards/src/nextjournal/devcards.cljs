(ns nextjournal.devcards
  (:require-macros [nextjournal.devcards :as dc])
  (:require [clojure.string]
            [nextjournal.view :as v]
            [re-frame.context :as rf]
            [re-frame.frame :as rf.frame]
            [reagent.core :as r]))

(defonce registry (r/atom {}))

(defn register-devcard* [{:as opts :keys [ns name]}]
  (swap! registry assoc-in [ns name] opts))

(defn error-boundary [_elem]
  (let [error (r/atom nil)]
    (r/create-class
     {:UNSAFE_component-will-receive-props
      (fn [_this _]
        (when @error
          (reset! error nil)))
      :component-did-catch
      (fn [_this e ^js info]
        (reset! error [e info])
        (let [trace (some-> info
                            (.-componentStack)
                            (clojure.string/split "\n")
                            (->> (map clojure.string/trim)
                                 (remove empty?)
                                 (clojure.string/join "\n")))]
          (set! (.-componentStack info) trace)))
      :reagent-render
      (fn [elem]
        (if-let [[e info] @error]
          [:div.sans-serif
           [:div.b.f6.red "Error"]
           [:pre.text-xs.red.mt2.b
            {:style {:overflow-x "auto"}}
            (.-message e)]
           (when-let [stack (.-componentStack ^js info)]
             [:pre.text-xs.red.mt2
              {:style {:overflow-x "auto"}}
              stack])]
          (do
            (reset! error nil)
            elem)))})))

(v/defview testview [{::v/keys [state]}]
  [:h1 {:on-click #(swap! state update :clicks (fnil inc 0))}
   "Hello " (:name @state) "! (" (:clicks @state 0) " clicks)"])

(dc/defcard testview-card
  "evaluating this form should change the inital state if changed."
  [state]
  [testview {::v/initial-state state}]
  {::dc/state {:name "World"}
   :some-value-in-db 1})


(rf/reg-sub
 ::counter
 (fn [db]
   (::counter db 0)))

(rf/reg-event-db
 ::inc
 (fn [db]
   (update db ::counter inc)))

(v/defview testc [{::rf/keys [dispatch subscribe]}]
  [:h1 {:on-click #(dispatch [::inc])} "Counter = " @(subscribe [::counter])])

(dc/defcard counter-db-1
  "cards can use completely isolated dbs and use subscriptions and event handlers."
  []
  [testc]
  {::counter 10})

(dc/defcard counter-db-2
  "notice how clicking here doesn't affect the card above."
  []
  [testc]
  {::counter 999})

(dc/when-enabled
 (v/defview frame-display
   {::v/initial-state
    (fn []
      (:frame-id (rf/current-frame)))}
   [{::v/keys [state]}]
   [:div @state "/" (:frame-id (rf/current-frame))]))

(dc/defcard multiframe
 "Demonstrates using multiple isolated frames in a component"
  []
  (into [:div]
        (map
         #(let [frame (rf.frame/make-frame
                       {:registry (:registry (rf/current-frame))
                        :app-db (r/atom {})})]
            [rf/provide-frame frame
             (rf/bind-frame frame
                            [frame-display])])
         (range 2))))
