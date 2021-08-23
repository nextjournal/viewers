(ns nextjournal.view.expo
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro view-source [name]
  (let [lines (-> "nextjournal/view/expo.cljs"
                  io/resource
                  slurp
                  (str/split #"\n"))]
    [:pre
     [:code.language-clojure
      (->> lines
           (drop-while #(not (str/includes? % (str " " name))))
           (take-while #(not (re-find #"^$" %)))
           (str/join "\n"))]]))
