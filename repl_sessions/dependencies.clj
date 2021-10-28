(ns dependencies
  (:require [lambdaisland.classpath :as licp]
            [lambdaisland.classpath.watch-deps :as licp-watch]))

(licp/git-pull-lib "modules/command-bar/deps.edn" 're-frame/re-frame)
(licp/git-pull-lib "modules/devcards/deps.edn" 're-frame/re-frame)
(licp/git-pull-lib "modules/devdocs/deps.edn" 'com.nextjournal/clerk)
(licp/git-pull-lib "modules/devdocs/deps.edn" 're-frame/re-frame)
(licp/git-pull-lib 'com.nextjournal/clerk)

(comment
  (licp/classpath-chain)

  (licp/update-classpath! {})

  (licp-watch/start! {}))
