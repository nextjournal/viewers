(ns nextjournal.ui.components.motion
  (:require ["framer-motion" :as framer-motion :refer [motion AnimatePresence]]))

(def div (.-div motion))
(def nav (.-nav motion))
(def img (.-img motion))
(def span (.-span motion))
(def button (.-button motion))
(def svg (.-svg motion))
(def g (.-svg motion))
(def circle (.-circle motion))
(def path (.-path motion))

(def animate-presence AnimatePresence)

(defn animate [from to opts]
  (.animate framer-motion from to (clj->js opts)))