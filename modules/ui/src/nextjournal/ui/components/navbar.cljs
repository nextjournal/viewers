(ns nextjournal.ui.components.navbar
  (:require [nextjournal.devcards :as dc]
            [clojure.string :as str]
            [reagent.core :as r]
            ["emoji-regex" :as emoji-regex]))

(def emoji-re (emoji-regex))

(defn navbar-items [!state items update-at]
  (into
    [:div]
    (map-indexed
      (fn [i {:keys [path title expanded? items]}]
        (let [label (or title (str/capitalize (last (str/split path #"/"))))
              emoji (when (zero? (.search label emoji-re))
                      (first (.match label emoji-re)))]
          (js/console.log (when emoji (.-length emoji) (count emoji)))
          [:<>
           (if (seq items)
             [:div.flex.px-3.py-1.hover:bg-slate-200.cursor-pointer
              {:on-click (fn [event]
                           (.preventDefault event)
                           (.stopPropagation event)
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
             [:a.flex.px-3.py-1.hover:bg-slate-200 {:href path}
              [:div.flex.items-center.justify-center.flex-shrink-0
               {:class "w-[20px] h-[20px] mr-[4px]"}
               (if emoji
                 [:div emoji]
                 [:svg.text-slate-500.h-4.w-4
                  {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                  [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]])]
              [:div
               {:class "text-[14px]"}
               (if emoji
                 (subs label (count emoji))
                 label)]])
           (when (and (seq items) expanded?)
             [:div.ml-2
              [navbar-items !state items (vec (conj update-at i :items))]])]))
      items)))

(defn navbar [!state]
  [:div.overflow-y-auto.overflow-x-hidden.h-full.py-2
   [:h4.m-0.uppercase.tracking-wider.text-slate-500.font-normal.px-3
    {:class ["text-[12px]"]}
    "Project"]
   [navbar-items !state @!state []]])

(dc/when-enabled
  (def navbar-long
    [{:path "notebooks/boom.clj" :title "💥 Boom"}
     {:path "notebooks/conditional_read.cljc" :title "Conditional Read"}
     {:path "notebooks/controlling_width.clj" :title "↔️ Controlling Width"}
     {:path "notebooks/deep.clj" :title "🕳 Deep"}
     {:path "notebooks/dice.clj" :title "🎲 Nanu Würfel"}
     {:path "notebooks/double_pendulum.clj" :title "Exercise 1.44: The double pendulum ⚖️"}
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
     {:path "notebooks/visibility.clj" :title "🙈 Controlling Visibility"}])
  (def navbar-nested
    [{:path "#" :title "Insights" :items [{:path "#" :title "EDI Analysis"}
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
     {:path "#" :title "Re-DB" :items [{:path "#" :title "Re-DB: a cljs forms library"}]}]))

(dc/defcard navbars
  (r/with-let [!state-long (r/atom navbar-long)
               !state-nested (r/atom navbar-nested)]
    [:div.flex
     [:div.mr-5
      [:div.text-slate-400 {:class "text-[11px]"} "Long Example"]
      [:div.bg-slate-100.border
       {:class "w-[250px] h-[600px]"}
       [navbar !state-long]]]
     [:div.mr-5
      [:div.text-slate-400 {:class "text-[11px]"} "Nested Example"]
      [:div.bg-slate-100.border
       {:class "w-[250px] h-[600px]"}
       [navbar !state-nested]]]]))