(ns repl-sessions.run-shadow
  (:require [clojure.java.io :as io]
            [clojure.java.browse :as browse]
            [clojure.java.shell :as shell]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.server.runtime :as runtime]))

(comment
  (shell/sh "yarn" "install")
  (server/start!)
  (shadow/watch :main)
  (shadow/nrepl-select :main)
  (shadow/repl :browser)
  (js/console.log :ahoi )
  (browse/browse-url "http://localhost:7799"))
