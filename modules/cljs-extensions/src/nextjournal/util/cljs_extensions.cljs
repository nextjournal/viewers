(ns nextjournal.util.cljs-extensions)

(when (exists? js/NodeList)
  (extend-type js/NodeList
    ISeqable
    (-seq [array] (array-seq array 0))))

(when (exists? js/HTMLCollection)
  (extend-type js/HTMLCollection
    ISeqable
    (-seq [array] (array-seq array 0))))
