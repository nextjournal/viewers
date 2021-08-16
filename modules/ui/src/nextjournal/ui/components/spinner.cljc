(ns nextjournal.ui.components.spinner)

(defn view [{:keys [size class] :or {size 16 class "fill-teal"}}]
  [:svg {:x 0 :y 0 :viewBox "0 0 80 80" :width size :height size :class class}
   [:path {:d "M10,40c0,0,0-0.4,0-1.1c0-0.3,0-0.8,0-1.3c0-0.3,0-0.5,0-0.8c0-0.3,0.1-0.6,0.1-0.9c0.1-0.6,0.1-1.4,0.2-2.1c0.2-0.8,0.3-1.6,0.5-2.5c0.2-0.9,0.6-1.8,0.8-2.8c0.3-1,0.8-1.9,1.2-3c0.5-1,1.1-2,1.7-3.1c0.7-1,1.4-2.1,2.2-3.1c1.6-2.1,3.7-3.9,6-5.6c2.3-1.7,5-3,7.9-4.1c0.7-0.2,1.5-0.4,2.2-0.7c0.7-0.3,1.5-0.3,2.3-0.5c0.8-0.2,1.5-0.3,2.3-0.4l1.2-0.1l0.6-0.1l0.3,0l0.1,0l0.1,0l0,0c0.1,0-0.1,0,0.1,0c1.5,0,2.9-0.1,4.5,0.2c0.8,0.1,1.6,0.1,2.4,0.3c0.8,0.2,1.5,0.3,2.3,0.5c3,0.8,5.9,2,8.5,3.6c2.6,1.6,4.9,3.4,6.8,5.4c1,1,1.8,2.1,2.7,3.1c0.8,1.1,1.5,2.1,2.1,3.2c0.6,1.1,1.2,2.1,1.6,3.1c0.4,1,0.9,2,1.2,3c0.3,1,0.6,1.9,0.8,2.7c0.2,0.9,0.3,1.6,0.5,2.4c0.1,0.4,0.1,0.7,0.2,1c0,0.3,0.1,0.6,0.1,0.9c0.1,0.6,0.1,1,0.1,1.4C74,39.6,74,40,74,40c0.2,2.2-1.5,4.1-3.7,4.3s-4.1-1.5-4.3-3.7c0-0.1,0-0.2,0-0.3l0-0.4c0,0,0-0.3,0-0.9c0-0.3,0-0.7,0-1.1c0-0.2,0-0.5,0-0.7c0-0.2-0.1-0.5-0.1-0.8c-0.1-0.6-0.1-1.2-0.2-1.9c-0.1-0.7-0.3-1.4-0.4-2.2c-0.2-0.8-0.5-1.6-0.7-2.4c-0.3-0.8-0.7-1.7-1.1-2.6c-0.5-0.9-0.9-1.8-1.5-2.7c-0.6-0.9-1.2-1.8-1.9-2.7c-1.4-1.8-3.2-3.4-5.2-4.9c-2-1.5-4.4-2.7-6.9-3.6c-0.6-0.2-1.3-0.4-1.9-0.6c-0.7-0.2-1.3-0.3-1.9-0.4c-1.2-0.3-2.8-0.4-4.2-0.5l-2,0c-0.7,0-1.4,0.1-2.1,0.1c-0.7,0.1-1.4,0.1-2,0.3c-0.7,0.1-1.3,0.3-2,0.4c-2.6,0.7-5.2,1.7-7.5,3.1c-2.2,1.4-4.3,2.9-6,4.7c-0.9,0.8-1.6,1.8-2.4,2.7c-0.7,0.9-1.3,1.9-1.9,2.8c-0.5,1-1,1.9-1.4,2.8c-0.4,0.9-0.8,1.8-1,2.6c-0.3,0.9-0.5,1.6-0.7,2.4c-0.2,0.7-0.3,1.4-0.4,2.1c-0.1,0.3-0.1,0.6-0.2,0.9c0,0.3-0.1,0.6-0.1,0.8c0,0.5-0.1,0.9-0.1,1.3C10,39.6,10,40,10,40z"}
    [:animateTransform {:attributeType "xml"
                        :attributeName "transform"
                        :type "rotate"
                        :from "0 40 40"
                        :to "360 40 40"
                        :dur "0.6s"
                        :repeatCount "indefinite"}]]])

(defn uploading [{:keys [class size] :or {class "bg-black-30" size 10}}]
  [:div.dot {:style {:width size :height size}
             :class "uploading"}
   [:div.dot1 {:class class}]
   [:div.dot2 {:class class}]])

(defn downloading [{:keys [class size] :or {class "bg-black-30" size 10}}]
  [:div.dot {:style {:width size :height size}
             :class "downloading"}
   [:div.dot1 {:class class}]
   [:div.dot2 {:class class}]])

(defn dot [{:keys [animating? class size] :or {animating? true size 10 class "bg-black-30"}}]
  [:div.dot {:style {:width size :height size}
             :class (when animating? "animating")}
   [:div.dot1 {:class class :style {:border-radius (/ size 2)}}]
   [:div.dot2 {:class class :style {:border-radius (/ size 2)}}]])

(defn radial-progress [{:keys [animating? size line-width duration initial-progress progress base-color fill-color style]
                        :or {animating? true size 20 line-width 2 duration 5000 initial-progress 0 progress 1 base-color "rgba(0,0,0,.5)" fill-color "#000"}}]
  (let [outer-r (/ size 2)
        r (/ (- size line-width) 2)
        circumference (* 2 Math/PI r)
        duration (/ duration 1000)]
    [:svg.radial-progress {:width size :height size :viewBox (str "0 0 " size " " size)
                           :style (merge {:transform "rotate(-90deg)"} style)}
     [:circle {:cx outer-r :cy outer-r :r r :fill "none" :stroke base-color :stroke-width line-width}]
     [:circle.radial-progress-value
      {:cx outer-r :cy outer-r :r r :fill "none" :stroke fill-color :stroke-width line-width
       :stroke-dasharray circumference
       :stroke-dashoffset (* circumference (- 1 initial-progress))
       :style {:transition (str "stroke-dashoffset " duration "s linear")}
       :ref (fn [el]
              #?(:cljs
                 (when (and el animating?)
                   (js/requestAnimationFrame
                    (fn [_] (set! (.-strokeDashoffset (.-style el))
                                  (* circumference (- 1 progress))))))))}]]))
