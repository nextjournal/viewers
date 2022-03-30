(ns repl-sessions.run-shadow
  (:require [shadow.cljs.devtools.api :as shadow]))

(comment
  (shadow/repl :browser)
  (js/console.log :ahoi "🌴"))
