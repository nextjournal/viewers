(ns nextjournal.ui.dom
  (:require [goog.dom :as gdom]))

(def ^:private closest-supported? (exists? js/Element.prototype.closest))
(defn closest [^js el sel]
  (when el
    (let [el (if (instance? js/Text el)
               (.-parentElement el)
               el)]
      (if closest-supported?
        (.closest el sel)
        (gdom/getAncestor el (fn [node] (.matches node sel)) true)))))
