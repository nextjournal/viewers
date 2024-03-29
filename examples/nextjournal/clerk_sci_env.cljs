(ns nextjournal.clerk-sci-env
  (:require [nextjournal.clerk.sci-env]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards-ui]
            [reagent.core]
            [reitit.frontend.easy]
            [reitit.frontend.history]
            [sci.ctx-store]
            [sci.core :as sci]))

(def sci-namespaces
  {'nextjournal.devcards        {'registry dc/registry}
   'nextjournal.devcards-ui     (sci/copy-ns nextjournal.devcards-ui (sci/create-ns 'nextjournal.devcards-ui))
   'reitit.frontend.easy        (sci/copy-ns reitit.frontend.easy (sci/create-ns 'reitit.frontend.easy))
   ;; `sci/copy-ns` cause NPE
   'reitit.frontend.history     {'href reitit.frontend.history/href}
   'reagent.core                {'atom reagent.core/atom}})

(sci.ctx-store/swap-ctx! sci/merge-opts {:namespaces sci-namespaces})
