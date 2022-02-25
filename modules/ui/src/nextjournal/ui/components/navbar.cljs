(ns nextjournal.ui.components.navbar
  (:require [nextjournal.devcards :as dc]
            [clojure.string :as str]
            [reagent.core :as r]
            ["emoji-regex" :as emoji-regex]))

(def emoji-re (emoji-regex))

(defn stop-event! [event]
  (.preventDefault event)
  (.stopPropagation event))

(defn scroll-to-anchor! [anchor]
  (.. (js/document.getElementById (subs anchor 1)) scrollIntoView))

(defn toc-items [!state items & [options]]
  (into
    [:div]
    (map
      (fn [{:keys [path title items]}]
        [:<>
         [:a.flex.px-3.py-1.hover:bg-slate-200
          (merge {:href path
                  :on-click (fn [event]
                              (stop-event! event)
                              (scroll-to-anchor! path))}
                 options)
          [:div {:class "text-[14px]"} title]]
         (when (seq items)
           [:div.ml-3
            [toc-items !state items]])])
      items)))

(defn navbar-items [!state items update-at]
  (into
    [:div]
    (map-indexed
      (fn [i {:keys [path title expanded? loading? items toc]}]
        (let [label (or title (str/capitalize (last (str/split path #"/"))))
              emoji (when (zero? (.search label emoji-re))
                      (first (.match label emoji-re)))]
          [:<>
           (if (seq items)
             [:div.flex.px-3.py-1.hover:bg-slate-200.cursor-pointer
              {:on-click (fn [event]
                           (stop-event! event)
                           (swap! !state assoc-in (vec (conj update-at i :expanded?)) (not expanded?)))}
              [:div.flex.items-center.justify-center.flex-shrink-0
               {:class "w-[20px] h-[20px] mr-[4px]"}
               [:svg.text-slate-500.transform.transition
                {:viewBox "0 0 100 100"
                 :class ["w-[10px]" "h-[10px]" (if expanded? "rotate-180" "rotate-90")]}
                [:polygon {:points "5.9,88.2 50,11.8 94.1,88.2 " :fill "currentColor"}]]]
              [:div
               {:class "text-[14px]"}
               label]]
             [:a.flex.px-3.py-1.hover:bg-slate-200
              {:href path
               :on-click (fn [event]
                           (stop-event! event)
                           (when toc
                             (swap! !state assoc-in (vec (conj update-at i :loading?)) true)
                             (js/setTimeout
                               (fn []
                                 (swap! !state #(-> (assoc-in % (vec (conj update-at i :loading?)) false)
                                                    (assoc :toc toc))))
                               500)))}
              [:div.flex.items-center.justify-center.flex-shrink-0
               {:class "w-[20px] h-[20px] mr-[4px]"}
               (if loading?
                 [:svg.animate-spin.h-3.w-3.text-slate-500
                  {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"}
                  [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
                  [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
                 (if emoji
                   [:div emoji]
                   [:svg.text-slate-500.h-4.w-4
                    {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                    [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]))]
              [:div
               {:class "text-[14px]"}
               (if emoji
                 (subs label (count emoji))
                 label)]])
           (when (and (seq items) expanded?)
             [:div.ml-3
              [navbar-items !state items (vec (conj update-at i :items))]])]))
      items)))

(defn navbar [!state]
  (let [{:keys [toc]} @!state]
    [:div.relative.overflow-x-hidden.h-full
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transition.transform.py-3
      {:class (if toc "-translate-x-full" "translate-x-0")}
      [:div.uppercase.tracking-wider.text-slate-500.font-medium.px-3.mb-1
       {:class ["text-[12px]"]}
       "Project"]
      [navbar-items !state (:items @!state) [:items]]]
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transition.transform.pt-2.pb-3
      {:class (if toc "translate-x-0" "translate-x-full")}
      [:div.font-normal.px-3.py-1.cursor-pointer.text-slate-500.hover:bg-slate-200
       {:class ["text-[12px]"]
        :on-click #(swap! !state dissoc :toc)}
       "← Back to project"]
      [toc-items !state toc {:class "font-medium"}]]]))

(dc/when-enabled
  (def toc-pendulum
    [{:path "#top"
      :title "Exercise 1.44: The double pendulum ⚖️"
      :items [{:path "#Lagrangian" :title "Lagrangian"}
              {:path "#Simulation" :title "Simulation"}
              {:path "#Measurements-Data-Transformation" :title "Measurements, Data Transformation"}
              {:path "#Data-Visualization-Utilities" :title "Data Visualization Utilities"}
              {:path "#Generalized-coordinates-velocities" :title "Generalized coordinates, velocities"}
              {:path "#Double-Double-Pendulum" :title "Double Double Pendulum!"}]}])
  (def navbar-long
    {:items
     [{:path "notebooks/boom.clj" :title "💥 Boom"}
      {:path "notebooks/conditional_read.cljc" :title "Conditional Read"}
      {:path "notebooks/controlling_width.clj" :title "↔️ Controlling Width"}
      {:path "notebooks/deep.clj" :title "🕳 Deep"}
      {:path "notebooks/dice.clj" :title "🎲 Nanu Würfel"}
      {:path "notebooks/double_pendulum.clj" :title "Exercise 1.44: The double pendulum ⚖️" :toc toc-pendulum}
      {:path "notebooks/elements.clj" :title "Elements of Clerk"}
      {:path "notebooks/hello.clj" :title "👋 Hello, Clerk"}
      {:path "notebooks/how_clerk_works.clj" :title "🕵️‍♀️ How Clerk works"}
      {:path "notebooks/interactivity.clj" :title "🤹‍♀️ Interactivity"}
      {:path "notebooks/markdown.md" :title "Markdown Ingestion"}
      {:path "notebooks/onwards.md" :title "🏔 Onwards"}
      {:path "notebooks/pagination.clj" :title "Pagination"}
      {:path "notebooks/paren_soup.clj" :title "🍜 Paren Soup"}
      {:path "notebooks/readme.clj" :title "README"}
      {:path "notebooks/recursive.clj"}
      {:path "notebooks/rule_30.clj" :title "🕹 Rule 30"}
      {:path "notebooks/sicmutils.clj" :title "Exercise 1.44: The double pendulum"}
      {:path "notebooks/sorting.clj" :title "📶 Sorting"}
      {:path "notebooks/tablecloth.clj" :title "Tablecloth Sample"}
      {:path "notebooks/tap.clj" :title "🚰 Tap Inspector"}
      {:path "notebooks/test123.clj"}
      {:path "notebooks/viewer_api.clj" :title "👁 Clerk Viewer API"}
      {:path "notebooks/viewer_api_meta.clj" :title "Metadata-based Viewer API"}
      {:path "notebooks/viewer_d3_require.clj" :title "Custom Viewers with d3-require"}
      {:path "notebooks/viewer_normalization.clj"}
      {:path "notebooks/viewers" :items [{:path "notebooks/viewers/html.clj" :title "🧙‍♀️ HTML & Hiccup"}
                                         {:path "notebooks/viewers/image.clj" :title "🏞 Image Viewer"}
                                         {:path "notebooks/viewers/image_layouts.clj" :title "Image Layouts"}
                                         {:path "notebooks/viewers/markdown.clj" :title "✍️ Markdown"}
                                         {:path "notebooks/viewers/plotly.clj" :title "📊 Plotly"}
                                         {:path "notebooks/viewers/table.clj" :title "🔢 Table"}
                                         {:path "notebooks/viewers/tex.clj" :title "🧮 TeX"}
                                         {:path "notebooks/viewers/vega.clj" :title "🗺 Vega Lite"}]}
      {:path "notebooks/visibility.clj" :title "🙈 Controlling Visibility"}]})
  (def navbar-nested
    {:items [{:path "#" :title "Insights" :items [{:path "#" :title "EDI Analysis"}
                                                  {:path "#" :title "Can we get model info from BMW EDI?"}
                                                  {:path "#" :title "Can we get model info from BMW EDI? (vol.2)"}
                                                  {:path "#" :title "Edifact Analysis"}
                                                  {:path "#" :title "EDI History" :items [{:path "#" :title "EDI History: Unrecognized WMIs"}
                                                                                          {:path "#" :title "EDI History: Overview"}
                                                                                          {:path "#" :title "What is the overlap between EDI history & PLFZDP?"}
                                                                                          {:path "#" :title "EDI History: Model names"}]}]}
             {:path "#" :title "Notebooks" :items [{:path "#" :title "🌍 Localization"}
                                                   {:path "#" :title "🎪 Demo"}
                                                   {:path "#" :title "🚚 Devcards"}
                                                   {:path "#" :title "Import Compounds"}
                                                   {:path "#" :title "Data Mappers"}
                                                   {:path "#" :title "Compound Simulation"}
                                                   {:path "#" :title "AS/400 Archeology"}
                                                   {:path "#" :title "Table Exploration Template"}
                                                   {:path "#" :title "Reify Simulation Data"}
                                                   {:path "#" :title "Krefeld EDI"}
                                                   {:path "#" :title "Krefeld seed"}
                                                   {:path "#" :title "Dispatchable Vehicles"}
                                                   {:path "#" :title "Columns"}
                                                   {:path "#" :title "Krefeld Volume Analysis"}
                                                   {:path "#" :title "🏭 Manufacturers"}
                                                   {:path "#" :title "🃏 Holodeck"}
                                                   {:path "#" :title "VIN Scanner Logs"}
                                                   {:path "#" :title "👷️ Workflow Contro"}]}
             {:path "#" :title "Roadmap" :items [{:path "#" :title "Ladungsbildung"}
                                                 {:path "#" :title "Referenz"}
                                                 {:path "#" :title "Wörterbuch"}
                                                 {:path "#" :title "Abrechnung"}
                                                 {:path "#" :title "EDI"}
                                                 {:path "#" :title "Compound"}
                                                 {:path "#" :title "Truck Booking"}
                                                 {:path "#" :title "ARS" :items [{:path "#" :title "Anforderungen der IT"}
                                                                                 {:path "#" :title "Anforderungen Disposition"}
                                                                                 {:path "#" :title "Anforderungen Abrechnung"}
                                                                                 {:path "#" :title "Anforderungen Compound"}]}]}
             {:path "#" :title "Re-DB" :items [{:path "#" :title "Re-DB: a cljs forms library"}]}]}))

(dc/defcard navbars
  (r/with-let [!state-long (r/atom navbar-long)
               !state-toc (r/atom (assoc navbar-long :toc toc-pendulum))
               !state-nested (r/atom (-> navbar-nested (assoc-in [:items 0 :expanded?] true)))]
    [:div
     [:div.flex
      [:div.mr-5
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "Long Example"]]
       [:div.bg-slate-100.border
        {:class "h-[600px]"}
        [navbar !state-long]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Emojis are automatically parsed and replace the document icon."]]
      [:div.mr-5
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "TOC Example"]]
       [:div.bg-slate-100.border
        {:class "h-[600px]"}
        [navbar !state-toc]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Sidebar items have a loading state while the TOC is fetched. Slide-in animation indicates that you are now inside the notebook."]]
      [:div.mr-5
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "Nested Example"]]
       [:div.bg-slate-100.border
        {:class "h-[600px]"}
        [navbar !state-nested]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Expanded state can be set initially too so that we can use heuristics for more DWIM."]]]
     [:div.mt-12
      [:div.flex
       [:div.mr-5
        {:class "w-[300px]"}
        [:div.text-slate-400.mb-1
         {:class "text-[11px]"}
         [:strong "Mobile Example"]]
        [:div.border.bg-white.relative.overflow-hidden
         {:class "h-[580px]"}
         [:div.absolute.border-r.bg-slate-100.left-0.top-0.h-full.shadow-xl
          {:class "w-[250px]"}
          [navbar !state-nested]
          [:button.w-8.h-8.absolute.top-1.right-1.flex.justify-center.items-center.hover:bg-slate-200.rounded.z-2
           [:svg.h-4.w-4.text-slate-400
            {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M6 18L18 6M6 6l12 12"}]]]]]]]]]))