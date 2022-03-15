(ns nextjournal.ui.components.motion
  (:require ["framer-motion" :refer [motion AnimatePresence]]))

(def div (.-div motion))
(def nav (.-nav motion))
(def img (.-img motion))
(def span (.-span motion))
(def button (.-button motion))

(def animate-presence AnimatePresence)
