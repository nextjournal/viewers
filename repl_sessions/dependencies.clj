(ns dependencies
  (:require [lambdaisland.classpath :as licp]
            [lambdaisland.classpath.watch-deps :as licp-watch]))

(licp/git-pull-lib "modules/command-bar/deps.edn" 're-frame/re-frame)
(licp/git-pull-lib "modules/devcards/deps.edn" 're-frame/re-frame)
(licp/git-pull-lib "modules/devdocs/deps.edn" 'com.lambdaisland/deja-fu)
(licp/git-pull-lib "modules/devdocs/deps.edn" 'com.lambdaisland/shellutils)
(licp/git-pull-lib 'com.nextjournal/clerk)

(licp/classpath-chain)

(licp/update-classpath! {})

(licp-watch/start! {})
