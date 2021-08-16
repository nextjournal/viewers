(ns nextjournal.ui.components.positioning
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [goog.positioning :as pos]
            [goog.style]
            [goog.math.Box]
            ["react-dom" :as rdom]))

(defn new-portal-container []
  (doto (js/document.createElement "div")
    (js/document.body.appendChild)))

(defn portal
  ([content]
   [portal {:container (new-portal-container)} content])
  ([{:keys [container on-close on-open]} _content]
   (let [dom (or container (new-portal-container))]
     (reagent/create-class
      {:component-did-mount
       (fn []
         (when (fn? on-open)
           (on-open dom)))
       :component-will-unmount
       (fn []
         (when (fn? on-close)
           (on-close))
         (.remove dom))
       :reagent-render
       (fn [_options content]
         (rdom/createPortal
          (reagent/as-element
           [:div content])
          dom))}))))

(defn el-offsets [el]
  (let [box (.getBoundingClientRect el)
        scroll-el (or (js/document.querySelector ".notebook")
                      (.-body js/document))
        scroll-top (.-scrollTop scroll-el)
        scroll-left (.-scrollLeft scroll-el)
        client-top (.-clientTop scroll-el)
        client-left (.-clientTop scroll-el)]
    {:left (.round js/Math (- (+ (.-left box) scroll-left) client-left))
     :top (.round js/Math (- (+ (.-top box) scroll-top) client-top))}))

(defn attachment [corner & [offset]]
  (-> (case corner
        :top-center {:attachment "BOTTOM_CENTER" :target-attachment "TOP_CENTER"}
        :bottom-center {:attachment "TOP_CENTER" :target-attachment "BOTTOM_CENTER"}
        :center-left {:attachment "CENTER_LEFT" :target-attachment "CENTER_RIGHT"}
        :top-left {:attachment "BOTTOM_LEFT" :target-attachment "TOP_LEFT"}
        :top-right {:attachment "BOTTOM_RIGHT" :target-attachment "TOP_RIGHT"}
        :bottom-left {:attachment "TOP_LEFT" :target-attachment "BOTTOM_LEFT"}
        :bottom-right {:attachment "TOP_RIGHT" :target-attachment "BOTTOM_RIGHT"}
        :center-right {:attachment "CENTER_RIGHT" :target-attachment "CENTER_LEFT"}
        {:attachment "BOTTOM_CENTER" :target-attachment "TOP_CENTER"})
      (merge offset)))

(defn to-fixed-el
  "Positions `el` relative to a `target` element that is `position: fixed`.
   `target` can be a DOM element or a CSS selector."
  [target el &
   [{:keys [attachment offset]
     :or {attachment pos/Corner.BOTTOM_CENTER
          offset {:x 0, :y 0}}}]]
  (when-let [target-el (if (string? target)
                         (.querySelector js/document target)
                         target)]
    (pos/positionAtCoordinate
     (goog.style/getClientPosition target-el)
     el
     attachment
     (goog.math.Box. (:y offset) (:x offset) (:y offset) (:x offset)))))

(defn to-el
  "Positions `el` relative to a `target` element.
   `target` can be a DOM element or a CSS selector."
  [target el & [{:keys [attachment target-attachment offset]
                             :or {attachment "BOTTOM_CENTER"
                                  target-attachment "TOP_CENTER"
                                  offset {:x 0 :y 0}}}]]
  (let [target-el (cond-> target
                    (string? target) (js/document.querySelector))
        er (.getBoundingClientRect el)
        tr (.getBoundingClientRect target-el)
        [target-y-side target-x-side y-side x-side]
        (map (comp keyword str/lower-case)
             (concat (str/split target-attachment #"_")
                     (str/split attachment #"_")))
        target-x (case target-x-side
                   :left (.-left tr)
                   :center (+ (.-left tr) (/ (.-width tr) 2))
                   :right (+ (.-left tr) (.-width tr)))
        target-y (case target-y-side
                   :top (.-top tr)
                   :center (+ (.-top tr) (/ (.-height tr) 2))
                   :bottom (+ (.-top tr) (.-height tr)))
        x (min (- js/innerWidth (.-width er))
               (max 0 (case x-side
                        :left target-x
                        :center (- target-x (/ (.-width er) 2))
                        :right (- target-x (.-width er)))))
        y (min (- js/innerHeight (.-height er))
               (max 0 (case y-side
                        :top target-y
                        :center (- target-y (/ (.-height er) 2))
                        :bottom (- target-y (.-height er)))))]
    (set! (.. el -style -left) (str (- x (:x offset)) "px"))
    (set! (.. el -style -top) (str (- y (:y offset)) "px"))))

(defn to-node [{:keys [id]} el]
  (to-el (str "[data-node-id='" id "']") el))

(defn ease-in-out-quart [time from d dur]
  (let [t (/ time (/ dur 2))]
    (if (< t 1)
      (+ (* (/ d 2) t t t t) from)
      (let [t (- t 2)]
        (+ (* (/ (* -1 d) 2) (- (* t t t t) 2)) from)))))

(defn heading-top [container-el heading-el]
  (+ (- (.. heading-el getBoundingClientRect -top)
        (.. container-el getBoundingClientRect -top)
        20)
     (.-scrollTop container-el)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; scroll 'locks' - a hack to prevent editor.doc `maybe-scroll-to-section`
;; from interfering with user-initiated scrolls (eg. clicking TOC)

(defonce scrolling? (atom false))
(defn will-scroll! []
  (reset! scrolling? true)
  (js/setTimeout #(reset! scrolling? false)))
(defn can-scroll? [] (not @scrolling?))

(defn scroll-step [start-time start-x start-y dx dy duration el timer]
  (let [t (- (.now js/Date) start-time)]
    (if (>= t duration)
      (js/window.cancelAnimationFrame @timer)
      (let [new-x (ease-in-out-quart t start-x dx duration)
            new-y (ease-in-out-quart t start-y dy duration)]
        (if el
          (do (set! (.-scrollLeft el) new-x)
              (set! (.-scrollTop el) new-y))
          (js/window.scrollTo new-x new-y))
        (reset! timer (js/window.requestAnimationFrame
                       #(scroll-step start-time start-x start-y dx dy duration el timer)))))))

(defn scroll-to [el x y]
  (when (can-scroll?)
    (will-scroll!)
    ;; is there an advantage to using positioning/smooth-scroll-to vs scrollTo with :behavior "smooth"?
    (.scrollTo el #js{:left (or x 0) :top y :behavior "smooth"})))

(defn smooth-scroll-to [x y & [{:keys [el duration] :or {duration 400}}]]
  (when (can-scroll?)
    (will-scroll!)
    (let [start-x (if el (.-scrollLeft el) (or (.-scrollX js/window) (.-pageXOffset js/window)))
          start-y (if el (.-scrollTop el) (or (.-scrollY js/window) (.-pageYOffset js/window)))
          dx (- x start-x)
          dy (- y start-y)
          start-time (.now js/Date)
          timer (atom nil)]
      (if (<= duration 0)
        (if el
          (do (set! (.-scrollLeft el) x)
              (set! (.-scrollTop el) y))
          (js/window.scrollTo x y))
        (reset! timer (js/window.requestAnimationFrame
                       #(scroll-step start-time start-x start-y dx dy duration el timer)))))))

(defn scroll-to-top [& [opts]]
  (smooth-scroll-to 0 0 (merge {:el (js/document.querySelector ".notebook-scroller")} opts)))

(defn scroll-to-node [id & [{:keys [offset] :or {offset 50}}]]
  (when-let [dom-el (js/document.querySelector (str "[data-node-id='" id "']"))]
    (smooth-scroll-to 0 (- (:top (el-offsets dom-el)) offset)
                      {:el (js/document.querySelector ".notebook-scroller")})))

(defn vertical-align [el target-selector]
  (let [target-rect (-> js/document
                        (.querySelector target-selector)
                        .getBoundingClientRect)
        el-height (-> el .getBoundingClientRect .-height)]
    (if (>= (+ (.-top target-rect)
               (.-height target-rect)
               el-height)
            (.-innerHeight js/window))
      :above
      :below)))

(defn has-bounding-rect? [el]
  (and el (fn? (.-getBoundingClientRect el))))

(defn in-viewport? [el]
  (let [viewport-height (-> js/document .-documentElement .-clientHeight)
        viewport-width (-> js/document .-documentElement .-clientWidth)
        rect (.getBoundingClientRect el)]
    (and
     (>= (.-bottom rect) 0)
     (>= (.-right rect) 0)
     (<= (.-top rect) viewport-height)
     (<= (.-left rect) viewport-width))))

(defn ids-in-viewport []
  (->> (.querySelectorAll js/document "[data-node-id]")
       (filter in-viewport?)
       (map #(.getAttribute % "data-node-id"))
       (into #{})))

(defn topbar-height []
  (if-let [topbar-el (js/document.querySelector "#topbar")]
    (.-offsetHeight topbar-el)
    58))

(defn focus-at-end [el]
  (let [end (count (.-value el))]
    (.focus el)
    (set! (.-selectionStart el) end)
    (set! (.-selectionEnd el) end)))

(defn scroll-to-bottom [dom-el]
  (when dom-el
    (set! (.-scrollTop dom-el) (.-scrollHeight dom-el))))

(defn point-in-rect? [{:keys [x y]} rect]
  (let [r (if (= (-> rect .-constructor .-name) "DOMRect")
            {:x (.-x rect) :y (.-y rect) :width (.-width rect) :height (.-height rect)}
            rect)]
    (and (<= (:x r) x)
         (<= x (+ (:x r) (:width r)))
         (<= (:y r) y)
         (<= y (+ (:y r) (:height r))))))
