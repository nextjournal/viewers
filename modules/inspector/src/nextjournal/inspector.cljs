(ns nextjournal.inspector
  (:require [clojure.string :as str]
            [nextjournal.view :as v]
            [nextjournal.commands.core :as commands]
            [nextjournal.viewer :as viewer]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.tooltip :as tooltip]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [re-frame.core :as rf.core]
            [re-frame.context :as rf]
            [re-frame.frame :as rf.frame]
            [goog.dom.classlist :as classlist]))

(rf/reg-event-db
 :view/toggle-inspector
 (fn [{:keys [::visible?] :as db} _]
   (if visible?
     (classlist/remove js/document.body "inspector-visible")
     (classlist/add js/document.body "inspector-visible"))
   (assoc db ::visible? (not visible?))))

(v/defview toggle [{::rf/keys [dispatch subscribe]}]
  (let [visible? @(subscribe [:db/get ::visible?])]
    [tooltip/view
     {:attachment :center-right
      :tip [:div.whitespace-nowrap
            "Toggle Inspector"
            [:span.extra.ml-1 (:keys/printable (commands/get-command :view/toggle-inspector))]]}
     [:div.fixed
      {:style {:right 12 :bottom 83 :width 36 :height 36 :z-index 1005}}
      [:div.bg-white.hover-bg-light-silver.rounded-full.flex.items-center.justify-center.cursor-pointer
       {:style {:border "1px solid #DADADA" :width "100%" :height "100%"
                :box-shadow "0 2px 2px rgba(0,0,0,.1)"}
        :on-click #(dispatch [:view/toggle-inspector])}
       [icon/view (if visible? "ViewOff" "View") {:size 24}]]]]))

(def inspector-doc-md
"# 🕵️‍♀️ Inspector
 ```clojurescript
 (nextjournal.inspector/dbr)
 ```")

(defn db
  "displays a reactive value of the app-db, pass it:
   * no arguments to see the whole db
   * a vector to see the value at a given path
   * a function to extract a subset"
  ([] (db identity))
  ([f-or-path] (let [app-db @(:app-db rf.core/default-frame)]
                 (if (vector? f-or-path)
                   (get-in app-db f-or-path)
                   (f-or-path app-db)))))

(comment
  (tap> (db))
  (tap> (db :article))
  (tap> (db [:article :nodes]))
  (tap> (db #(-> % :com.nextjournal.editor.doc/state .-doc .-textContent))))

(defn dbr
  "displays a reactive value of the app-db, pass it:
   * no arguments to see the whole db
   * a vector to see the value at a given path
   * a function to extract a subset
   "
  ([] (dbr identity))
  ([f-or-path]
   (ratom/make-reaction #(db f-or-path))))

(comment
  (tap> (dbr))
  (tap> (dbr :com.nextjournal.editor.util/transit-xhrio-route-cached))
  (tap> (dbr :article))
  (tap> (dbr [:article :nodes]))
  (tap> (dbr #(-> % :com.nextjournal.editor.doc/state .-doc .toJSON)))
  (tap> (dbr #(-> % :com.nextjournal.editor.doc/state .-doc .-textContent))))

(defn selection [^js sel]
  (let [$from (.-$from sel)]
    {:$from $from
     :$to (.-$to sel)
     :nodeBefore (.-nodeBefore $from)
     :nodeAfter (.-nodeAfter $from)}))

(comment
  (tap> (dbr #(-> % :com.nextjournal.editor.doc/state .-selection selection)))
  (tap> (dbr #(let [{:keys [nodeBefore nodeAfter]} (-> % :com.nextjournal.editor.doc/state .-selection selection)
                    guard (fn [x f] (when (f x) x))
                    node-str (fn [^js x]
                               (when x (or (-> (.-textContent x)
                                               (guard (complement str/blank?)))
                                           (.. x -type -name))))]
                (str (node-str nodeBefore) "|"  (node-str nodeAfter))))))

(rf/reg-fx
 ::add-tap
 (fn [_ frame]
   (let [{:keys [dispatch]} (rf/context-fns frame)]
     (add-tap #(dispatch [::tap> %])))))

(rf/reg-event-db
 ::tap>
 (fn [db [_ v]]
   (update db ::taps conj {:value v :key (random-uuid)})))

(comment
 ;; before modularization - when bringing back the inspector article view, we should
 ;; refactor so it doesn't need to touch this (reusable) namespace
 ::v/initial-state
 (fn []
   (let [{:keys [article]} (markdown/parse+ inspector-doc-md {:com.nextjournal.editor.markdown/default-runtime
                                                              {:id (str (random-uuid))
                                                               :kind "runtime"
                                                               :type :editor-worker
                                                               :language "clojurescript"}})
         state (doc/state (:doc article))]
     (rf/dispatch [:perform-fx {::add-tap {}}])
     (rf/dispatch [:assoc-in-db {:path [:com.nextjournal.editor.doc/state]} state])
     (rf/dispatch [:assoc-in-db {:path [:article]} article])
     state)))

(v/defview inspector-doc
  {::v/initial-state
   (fn []
     (rf/dispatch [:perform-fx {::add-tap {}}])
     nil)}
  [{::rf/keys [dispatch subscribe] :keys [fixed?]}]
  (r/with-let [popped-out? (r/atom false)]
    [:div.inspector-panel.bg-gray-100.shadow-lg
     (when (or fixed? @popped-out?)
       {:class "fixed top-0 right-0 bottom-0"
        :style {:z-index 1005}})
     (when-not fixed?
       [:div.black-50.hover:underline.sans-serif.absolute.top-0.right-0.cursor-pointer
        {:style {:font-size 12 :margin "5px 5px 0 0"}
         :on-click #(swap! popped-out? not)}
        "Pop " (if @popped-out? "in" "out")])
     [:div.inspector.flex.flex-column
      [:div.inspector-taps.bg-gray-100
       {:style {:padding-left 60 :margin-left -30}}
       [:h2.flex.items-center
        "🚰 Taps"
        (when (seq @(subscribe [:db/get ::taps]))
          [:span.black-50.hover:underline.sans-serif.cursor-pointer.font-normal.ml-2
           {:style {:font-size 12}
            :on-click #(dispatch [:assoc-in-db {:path [::taps]} (list)])}
           "Clear"])]
       (if-let [taps (seq @(subscribe [:db/get ::taps]))]
         [:<>
          (for [{:keys [value key]} taps]
            ^{:key key}
            [:div {:style {:padding-right 30}}
             [viewer/inspect value]])]
         [:div.mt-2.text-sm
          [:code "tap>"]
          [:span " any value to show it here."]])]]]))


(comment
  (tap> (range 10))
  (tap> ^{:nextjournal/viewer :hiccup} [:h3 "Hello Hiccup 👋"]))

(v/defview view [{::v/keys [props]}]
  [inspector-doc props])

(commands/bind-event!
 :view/toggle-inspector
 {:keys "Shift-Mod-I"
  :requires #{:env/dev?}})
