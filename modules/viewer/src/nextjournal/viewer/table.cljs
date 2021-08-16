(ns nextjournal.viewer.table
  (:require [nextjournal.ui.components.icon :as icon]
            [nextjournal.viewer :as v]
            [reagent.core :as r]
            [nextjournal.devcards :as dc]))

(defn viewer [lines]
  (r/with-let [current-page (r/atom 0)]
    (let [header (first lines)
          rest (rest lines)
          count (count rest)
          items-per-page 10
          pages (js/Math.floor (/ count items-per-page))
          page-count (inc pages)
          showing (min count items-per-page)
          rows (take showing rest)
          items (take items-per-page (drop (* @current-page items-per-page) rest))]
      (v/html
       [:div.monospace.rounded.border.border-color-black-10.relative
        {:style {:font-size 13 :padding-bottom 24}}
        [:div.overflow-auto (when (< 1 page-count) {:style {:min-height 265}})
         [:table.nj-viewer-table
          {:cell-padding 0 :cell-spacing 0 :style {:min-width "100%" :font-size 13}}
          [:thead
           [:tr
            (map-indexed (fn [i val]
                           ^{:key (str "header-" i)}
                           [:th.text-left.whitespace-no-wrap.px-2.border-b.border-solid.border-color-black-05
                            {:style {:height 24 :line-height "24px"}
                             :class (when-not (zero? i) "border-l")}
                            val])
                         header)]]
          [:tbody
           (map-indexed (fn [i row]
                          ^{:key (str "row-" i)}
                          [:tr
                           {:class (str (when (even? i) "bg-near-white ")
                                        "hover-bg-light-silver")}
                           (map-indexed (fn [j val]
                                          ^{:key (str "datum-" i "-" j)}
                                          [:td.px-2.text-left.whitespace-no-wrap.border-color-black-05
                                           {:style {:height 24 :line-height "14px"}
                                            :class (str (when-not (= i (dec showing)) "border-b ")
                                                        (when-not (zero? j) "border-l"))}
                                           val])
                                        row)])
                        items)]]]
        [:div.flex.items-center.justify-between.absolute.left-0.bottom-0.w-full.px-2.border-t.border-solid.border-color-black-05.bg-white
         {:style {:height 24 :line-height "24px" :font-size 10 :border-bottom-left-radius 3 :border-top-right-radius 3}}
         [:div.black-40
          (str count " items")]
         (when (> count items-per-page)
           [:div.flex.items-center
            [:span.black-40.mr-3 "showing page " (inc @current-page) "/" page-count]
            [:button.nj-button--unstyled.flex.items-center.cursor-pointer
             {:style {:padding "0 4px"}
              :disabled (zero? @current-page)
              :on-click #(swap! current-page dec)}
             [icon/view "ChevronLeft" {:size 16 :class "fill-teal dim"}]]
            [:button.nj-button--unstyled.flex.items-center.cursor-pointer
             {:style {:padding "0 0 0 4px"}
              :disabled (= @current-page pages)
              :on-click #(swap! current-page inc)}
             [icon/view "ChevronRight" {:size 16 :class "fill-teal dim"}]]])]]))))

(v/register-viewer! :table viewer)

(dc/defcard table-2
  [v/inspect (v/view-as :table [{:a 1 :b 2 :c 3}
                                {:a 1 :b 2 :c 3}])])
