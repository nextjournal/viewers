(ns nextjournal.ui.components.navbar
  (:require [nextjournal.devcards :as dc]
            [nextjournal.viewer :as v]
            [nextjournal.ui.components.icon :as icon]
            [nextjournal.ui.components.localstorage :as ls]
            [clojure.string :as str]
            [reagent.core :as r]
            ["emoji-regex" :as emoji-regex]))

(def emoji-re (emoji-regex))

(defn stop-event! [event]
  (.preventDefault event)
  (.stopPropagation event))

(defn scroll-to-anchor! [anchor]
  (.. (js/document.getElementById (subs anchor 1)) scrollIntoView))

(defn theme-class [theme key]
  (-> {:project "py-3"
       :toc "pt-2 pb-3"
       :heading "text-[12px] uppercase tracking-wider text-slate-500 dark:text-slate-400 font-medium px-3 mb-1"
       :back "text-[12px] text-slate-500 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700 font-normal px-3 py-1"
       :expandable "text-[14px] hover:bg-slate-200 dark:hover:bg-slate-700 dark:text-white px-3 py-1"
       :triangle "text-slate-500 dark:text-slate-400"
       :item "text-[14px] hover:bg-slate-200 dark:hover:bg-slate-700 dark:text-white px-3 py-1"
       :icon "text-slate-500 dark:text-slate-400"
       :slide-over "font-sans bg-white border-r"
       :slide-over-unpinned "shadow-xl"
       :pin-toggle "text-[11px] text-slate-500 text-right absolute right-4 top-3 cursor-pointer hover:underline z-10"}
      (merge theme)
      (get key)))

(defn toc-items [!state items & [options]]
  (let [{:keys [theme]} @!state]
    (into
      [:div]
      (map
        (fn [{:keys [path title items]}]
          [:<>
           [:a.flex
            {:href path
             :class (theme-class theme :item)
             :on-click (fn [event]
                         (stop-event! event)
                         (scroll-to-anchor! path))}
            [:div (merge {} options) title]]
           (when (seq items)
             [:div.ml-3
              [toc-items !state items]])])
        items))))

(defn navbar-items [!state items update-at]
  (let [{:keys [theme]} @!state]
    (into
      [:div]
      (map-indexed
        (fn [i {:keys [path title expanded? loading? items toc]}]
          (let [label (or title (str/capitalize (last (str/split path #"/"))))
                emoji (when (zero? (.search label emoji-re))
                        (first (.match label emoji-re)))]
            [:<>
             (if (seq items)
               [:div.flex.cursor-pointer
                {:class (theme-class theme :expandable)
                 :on-click (fn [event]
                             (stop-event! event)
                             (swap! !state assoc-in (vec (conj update-at i :expanded?)) (not expanded?)))}
                [:div.flex.items-center.justify-center.flex-shrink-0
                 {:class "w-[20px] h-[20px] mr-[4px]"}
                 [:svg.transform.transition
                  {:viewBox "0 0 100 100"
                   :class (str (theme-class theme :triangle) " "
                               "w-[10px] h-[10px] "
                               (if expanded? "rotate-180" "rotate-90"))}
                  [:polygon {:points "5.9,88.2 50,11.8 94.1,88.2 " :fill "currentColor"}]]]
                [:div label]]
               [:a.flex
                {:href path
                 :class (theme-class theme :item)
                 :on-click (fn []
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
                   [:svg.animate-spin.h-3.w-3.text-slate-500.dark:text-slate-400
                    {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"}
                    [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
                    [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
                   (if emoji
                     [:div emoji]
                     [:svg.h-4.w-4
                      {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"
                       :class (theme-class theme :icon)}
                      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]))]
                [:div
                 (if emoji
                   (subs label (count emoji))
                   label)]])
             (when (and (seq items) expanded?)
               [:div.ml-3
                [navbar-items !state items (vec (conj update-at i :items))]])]))
        items))))

(defn navbar [!state]
  (let [{:keys [items theme toc]} @!state]
    [:div.relative.overflow-x-hidden.h-full
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transition.transform
      {:class (str (theme-class theme :project) " "
                   (if toc "-translate-x-full" "translate-x-0"))}
      [:div.px-3.mb-1
       {:class (theme-class theme :heading)}
       "Project"]
      [navbar-items !state (:items @!state) [:items]]]
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transition.transform
      {:class (str (theme-class theme :toc) " " (if toc "translate-x-0" "translate-x-full"))}
      (if (and (seq items) (seq toc))
        [:div.px-3.py-1.cursor-pointer
         {:class (theme-class theme :back)
          :on-click #(swap! !state dissoc :toc)}
         "← Back to project"]
        [:div.px-3.mb-1
         {:class (theme-class theme :heading)}
         "TOC"])
      [toc-items !state toc {:class "font-medium"}]]]))

(defn pin-button [!state content & [opts]]
  [:div
   (merge {:on-click #(swap! !state update :pinned? not)} opts)
   content])

(defn slide-over-content [!state content]
  (let [{:keys [pinned? mobile? width theme]} @!state]
    [:div.flex-shrink-0.sidebar-content
     (assoc {:style {:width width}}
       :class (str (theme-class theme :slide-over)
                   (if mobile?
                     " fixed left-0 top-0 bottom-0 z-10 "
                     " relative ")
                   (when-not pinned?
                     (theme-class theme :slide-over-unpinned))))
     [pin-button !state
      (if pinned?
        (if mobile? "Hide" "Unpin")
        (if mobile? "Show" "Pin"))
      {:class (theme-class theme :pin-toggle)}]
     content]))

(defn pinnable-slide-over [!state content]
  (r/with-let [{:keys [local-storage-key pinned?]} @!state
               resize #(if (< js/innerWidth 640)
                         (swap! !state assoc :pinned? false :mobile? true)
                         (swap! !state assoc :pinned? pinned? :mobile? false))
               ref-fn #(when %
                         (when local-storage-key
                           (add-watch !state ::persist
                                      (fn [_ _ old {:keys [pinned?]}]
                                        (when (not= (:pinned? old) pinned?)
                                          (ls/set-item! local-storage-key pinned?)))))
                         (js/addEventListener "resize" resize)
                         (resize))]
    (let [{:keys [pinned? mobile?]} @!state]
      [:div.flex.h-screen
       {:ref ref-fn}
       (if pinned?
         [slide-over-content !state content]
         [:div.fixed.left-0.top-0.flex.items-center.z-10.cursor-pointer
          [:div.fixed.top-0.left-0.bottom-0.z-20.p-4.collapsed-sidebar
           {:class (if mobile? (str "mobile-sidebar " (if pinned? "flex" "hidden")) "flex")}
           [:div.-ml-4.-mt-4.flex
            [slide-over-content !state content]]]])])))

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
             {:path "#" :title "Re-DB" :items [{:path "#" :title "Re-DB: a cljs forms library"}]}]})
  (defn mobile-example [!state]
    (let [{:keys [navbar-visible?]} @!state]
      [:div.border.bg-white.relative.overflow-x-hidden
       {:class "h-[580px]"}
       [:div.absolute.top-0.left-0.w-full.text-xs.bg-white.border-b.h-8.flex.items-center.justify-between
        [:div.px-6.h-8.flex.items-center.text-xs.font-bold
         "Clerk"]
        [:button.h-8.px-6.hover:bg-slate-100.cursor-pointer.flex.items-center.text-xs.text-slate-400
         {:on-click #(swap! !state assoc :navbar-visible? true)}
         "Navigate to…"]]
       [:div.top-8.left-0.absolute.w-full.bottom-0.overflow-y-auto.px-6.py-4.prose
        [v/inspect (v/view-as :markdown
"### Exercise 1.44: The double pendulum

This namespace explores [Exercise 1.44](https://tgvaughan.github.io/sicm/chapter001.html#Exe_1-44) from Sussman
and Wisdom's [Structure and Interpretation of Classical Mechanics](https://tgvaughan.github.io/sicm/), using
the [SICMUtils](https://github.com/sicmutils/sicmutils) Clojure library and the Clerk rendering environment.

#### Lagrangian
Start with a coordinate transformation from `theta1`, `theta2` to rectangular
coordinates. We'll generate our Lagrangian by composing this with an rectangular
Lagrangian with the familiar form of `T - V`.")]]
       [:div.absolute.top-0.left-0.w-full.h-full.bg-gray-500.bg-opacity-75.transition-opacity.pointer-events-none
        {:class (if navbar-visible? "opacity-100" "opacity-0")}]
       (when navbar-visible?
         [:div.absolute.top-0.left-0.w-full.h-full
          {:on-click #(swap! !state assoc :navbar-visible? false)}])
       [:div.absolute.top-0.left-0.h-full
        [:div.absolute.border-r.bg-slate-100.left-0.top-0.h-full.transition.transform
         {:class (str "w-[250px] " (if navbar-visible? "-translate-x-0 shadow-xl" "-translate-x-full"))}
         [navbar !state]
         [:button.w-8.h-8.absolute.top-1.right-1.flex.justify-center.items-center.hover:bg-slate-200.rounded.z-2
          {:on-click #(swap! !state assoc :navbar-visible? false)}
          [:svg.h-4.w-4.text-slate-400
           {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M6 18L18 6M6 6l12 12"}]]]]]])))

(dc/defcard navbars
  (r/with-let [!state-long (r/atom navbar-long)
               !state-toc (r/atom (assoc navbar-long :toc toc-pendulum))
               !state-nested (r/atom (-> navbar-nested (assoc-in [:items 0 :expanded?] true)))
               !state-branded-ductile (r/atom (-> navbar-nested
                                                  (assoc-in [:items 0 :expanded?] true)
                                                  (assoc :theme {:project "pt-[83px] pb-3"
                                                                 :toc "pt-10 pb-3"
                                                                 :heading "text-[12px] uppercase tracking-wider text-slate-300 font-medium px-5 mb-1"
                                                                 :back "text-[12px] text-slate-300 hover:bg-indigo-900 font-normal px-5 py-1"
                                                                 :expandable "text-[14px] hover:bg-indigo-900 text-white px-5 py-1"
                                                                 :triangle "text-slate-400"
                                                                 :item "text-[14px] hover:bg-indigo-900 text-white px-5 py-1"
                                                                 :icon "text-slate-400"})))
               !state-branded-nextjournal (r/atom (-> navbar-nested
                                                      (assoc-in [:items 0 :expanded?] true)
                                                      (assoc :theme {:project "pt-[80px] pb-3"
                                                                     :toc "pt-10 pb-3"
                                                                     :heading "text-[12px] uppercase tracking-wider text-slate-300 font-medium px-5 mb-1"
                                                                     :back "text-[12px] text-slate-300 hover:bg-white/10 font-normal px-5 py-1"
                                                                     :expandable "text-[14px] hover:bg-white/10 text-white px-5 py-1"
                                                                     :triangle "text-slate-400"
                                                                     :item "text-[14px] hover:bg-white/10 text-white px-5 py-1"
                                                                     :icon "text-slate-400"})))
               !state-mobile (r/atom navbar-long)
               !state-mobile-visible (r/atom (assoc navbar-long :navbar-visible? true))]
    [:div
     [:h4.text-base.mb-4.text-slate-400 "Desktop"]
     [:div.flex.flex-wrap
      [:div.mr-5.mb-12
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "Long Example"]]
       [:div.bg-slate-100.dark:bg-slate-800.border
        {:class "h-[600px]"}
        [navbar !state-long]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Emojis are automatically parsed and replace the document icon."]]
      [:div.mr-5.mb-12
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "TOC Example"]]
       [:div.bg-slate-100.dark:bg-slate-800.border
        {:class "h-[600px]"}
        [navbar !state-toc]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Sidebar items have a loading state while the TOC is fetched. Slide-in animation indicates that you are now inside the notebook."]]
      [:div.mr-5.mb-12
       {:class "w-[250px]"}
       [:div.text-slate-400.mb-1
        {:class "text-[11px]"}
        [:strong "Nested Example"]]
       [:div.bg-slate-100.dark:bg-slate-800.border
        {:class "h-[600px]"}
        [navbar !state-nested]]
       [:div.text-slate-400.mt-1
        {:class "text-[11px]"}
        "Expanded state can be set initially too so that we can use heuristics for more DWIM."]]]
     [:div
      [:h4.text-base.mb-4.text-slate-400 "Dark Mode & Theming"]
      [:div.flex.flex-wrap
       [:div.mr-5.mb-12.dark
        {:class "w-[250px]"}
        [:div.text-slate-400.mb-1
         {:class "text-[11px]"}
         [:strong "Dark Mode Example"]]
        [:div.bg-slate-100.dark:bg-slate-800.border
         {:class "h-[600px]"}
         [navbar !state-nested]]
        [:div.text-slate-400.mt-1
         {:class "text-[11px]"}
         "Dark mode is automatically applied when using the default theme."]]
       [:div.mr-5.mb-12
        {:class "w-[250px]"}
        [:div.text-slate-400.mb-1
         {:class "text-[11px]"}
         [:strong "Ductile-Branded Example"]]
        [:div.bg-indigo-800.border.relative
         {:class "h-[600px]"}
         [:a.absolute.w-full.pl-5.pr-12.top-6
          [:img {:src "https://snapshots.ductile.de/build-3518e610f1ea5a222bd83b496aa450d927d9acf6/images/ductile-logo-white.svg"}]]
         [navbar !state-branded-ductile]]
        [:div.text-slate-400.mt-1
         {:class "text-[11px]"}
         "Colors, spacing, font-sizes, &c can be overridden via the `theme` option."]]
       [:div.mr-5.mb-12
        {:class "w-[250px]"}
        [:div.text-slate-400.mb-1
         {:class "text-[11px]"}
         [:strong "Nextjournal-Branded Example"]]
        [:div.border.relative
         {:class "h-[600px]"
          :style {:background "#1f2937"}}
         [:a.absolute.w-full.pl-5.pr-12.top-6
          [:img {:src "https://nextjournal.com/images/nextjournal-logo-white.svg"}]]
         [navbar !state-branded-nextjournal]]]]]
     (let [{:keys [navbar-visible?]} @!state-mobile]
       [:div
        [:h4.text-base.mb-4.text-slate-400 "Mobile"]
        [:div.flex.flex-wrap
         [:div.mr-5
          {:class "w-[300px]"}
          [:div.text-slate-400.mb-1
           {:class "text-[11px]"}
           [:strong "Nav Hidden"]]
          [mobile-example !state-mobile]]
         [:div.mr-5
          {:class "w-[300px]"}
          [:div.text-slate-400.mb-1
           {:class "text-[11px]"}
           [:strong "Nav Visible"]]
          [mobile-example !state-mobile-visible]]]])]))
