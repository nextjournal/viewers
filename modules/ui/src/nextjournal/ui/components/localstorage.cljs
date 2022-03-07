(ns nextjournal.ui.components.localstorage)

(defn set-item! [key val]
  (.setItem (.-localStorage js/window) key val))

(defn get-item [key]
  (cljs.reader/read-string
    (.getItem (.-localStorage js/window) key)))

(defn remove-item!
  "Remove the browser's localStorage value for the given `key`" [key]
  (.removeItem (.-localStorage js/window) key))