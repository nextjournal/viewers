(ns nextjournal.ui.components.tooltip
  (:require [clojure.set :as set]
            #?@(:cljs [[reagent.dom :as rdom]
                       [nextjournal.ui.components.positioning :as pos]
                       [nextjournal.ui.dom :as ui.dom]
                       [nextjournal.ui.components :as ui]])))

#?(:cljs
   (do
     (def mouse-over-handler (atom nil))
     (def mouse-out-handler (atom nil))
     (def tooltip (atom nil))
     (def tip-el (atom nil))
     (def anchor (atom nil))
     (def checking? (atom false))

     (defn hide-tooltip
       ([event] (hide-tooltip))
       ([]
        (reset! checking? false)
        (set! (.. @tip-el -style -visibility) "hidden")
        (rdom/unmount-component-at-node @tip-el)))

     (defn check-hide []
       (if (and @checking? @anchor (js/document.body.contains @anchor))
         (reset! checking? (js/requestAnimationFrame check-hide))
         (hide-tooltip)))

     (defn show-tooltip [anchor-el]
       (let [{:keys [attachment offset class style tip] :or {attachment :bottom-center} :as config} @tooltip]
         (set! (.. @tip-el -style -visibility) "visible")
         (reset! anchor anchor-el)
         (rdom/render
          [:span.tooltip.fixed
           {:style style
            :ref (fn [el]
                   (when el
                     (set! (.. el -style -visibility) "visible")
                     (pos/to-el anchor-el el (pos/attachment attachment offset))
                     (js/requestAnimationFrame #(set! (.. el -style -opacity) 1))))
            :class (str "tooltip-" (name attachment) " " class)}
           [ui/panel
            {:arrow attachment
             :class class
             :style {:padding "5px 7px"}}
            [:div.leading-normal
             {:style {:font-size 11}}
             tip]]]
          @tip-el)
         (reset! checking? (js/requestAnimationFrame check-hide))))

     (defn on-mouse-out [event]
       (when (ui.dom/closest (.-target event) ".tooltip-container")
         (hide-tooltip)))

     (defn on-mouse-over [^js event]
       (when-let [target (ui.dom/closest (.-target event) ".tooltip-container")]
         (show-tooltip (.-firstElementChild target))))))

(defn view
  "Tooltips are heavily optimized to touch the DOM as little as possible.
   There is one container that is generated with the first view instance.
   After that, it is re-used for all subsequent tooltips. Only the content
   and position changes."
  [config & hs]
  (into
   [:span.tooltip-container
    (merge
     (set/rename-keys config {:container-class :class
                              :container-style :style})
     {#?@(:cljs [:on-mouse-enter #(reset! tooltip config)
                 :ref (fn [el]
                        (if el
                          (js/document.body.addEventListener "click" hide-tooltip)
                          (js/document.body.removeEventListener "click" hide-tooltip))
                        (when el
                          (when-not @tip-el
                            (reset! tip-el
                                    (doto (js/document.createElement "div")
                                      (.setAttribute "class" "tooltip-layout pointer-events-none")))
                            (.. js/document -body (appendChild @tip-el)))
                          (when-not @mouse-over-handler
                            (reset! mouse-over-handler on-mouse-over)
                            (js/document.body.addEventListener "mouseover" @mouse-over-handler))
                          (when-not @mouse-out-handler
                            (reset! mouse-out-handler on-mouse-out)
                            (js/document.body.addEventListener "mouseout" @mouse-out-handler))))])})]
   hs))

