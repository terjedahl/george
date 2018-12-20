
(defproject no.andante.george/george-application  "2018.10-SNAPSHOT"

  :description "George - Application"
  :url         "https://bitbucket.org/andante-george/george-application"
  :license     {:name "Eclipse Public License"
                :url  "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [;; https://clojure.org/community/downloads
                 [org.clojure/clojure "1.10.0"]
                 ;; https://github.com/clojure/core.async
                 [org.clojure/core.async "0.4.474"]
                 ;; https://github.com/clojure/tools.reader
                 [org.clojure/tools.reader "1.3.0"]
                 ;; https://github.com/mmcgrana/clj-stacktrace
                 [clj-stacktrace "0.2.8"]
                 ;; https://github.com/cemerick/nREPL
                 [nrepl "0.4.5"]
                 ;; https://github.com/FXMisc/RichTextFX
                 [org.fxmisc.richtext/richtextfx "0.9.2"]
                 ;; https://github.com/TomasMikula/Flowless
                 [org.fxmisc.flowless/flowless  "0.6.1"]
                 ;; https://github.com/droitfintech/clj-diff
                 [tech.droit/clj-diff "1.0.0"]
                 ;; https://github.com/clojure/core.rrb-vector
                 [org.clojure/core.rrb-vector "0.0.13"]
                 ;; https://github.com/clojure/data.json
                 [org.clojure/data.json "0.2.6"]
                 ;; https://github.com/weavejester/environ
                 [environ "1.1.0"]
                 ;; https://github.com/ztellman/potemkin
                 [potemkin "0.4.5"]
                 ;; https://github.com/clj-time/clj-time
                 [clj-time "0.15.0"]
                 ;; https://github.com/yogthos/markdown-clj
                 [markdown-clj "1.0.5"]
                 ;; https://github.com/alexander-yakushev/defprecated
                 [defprecated "0.1.3" :exclusions [org.clojure/clojure]]
                 ;; https://github.com/amalloy/ordered
                 [org.flatland/ordered "1.5.7"]
                 ;; https://github.com/zcaudate/hara
                 ;; http://docs.caudate.me/hara/hara-io-watch.html
                 [zcaudate/hara.common.watch "2.8.7"]
                 [zcaudate/hara.io.watch "2.8.7"]]

  :jar-exclusions     [#".DS_Store" #"arm.spraklab.*(clj|java)$"]
  
  :plugins [
            ;; https://github.com/weavejester/environ
            [lein-environ "1.1.0"]
            ;; https://github.com/weavejester/codox
            [lein-codox "0.10.5"]
            ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
            [lein-pprint "1.2.0"]]

  :repositories      []

  :deploy-repositories [
                        ["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths      ["src/main/clj"   "src/arm-spraklab/src/clj"]
  :java-source-paths ["src/main/java"  "src/arm-spraklab/src/java"]
  :resource-paths    ["src/main/rsc"   "src/arm-spraklab/src/rsc" 
                      "include"        "src/arm-spraklab/include"]

  :javac-options     ["-source" "1.8" "-target" "1.8"]
                      ;"-Xlint:unchecked"]

  :test-paths        ["src/test/clj"]

  :aot               [no.andante.george.Main]

  :main              no.andante.george.Main

  :jvm-opts          [
                      ;; should give crisper text on Mac
                      "-Dapple.awt.graphics.UseQuartz=true"]

  :target-path       "target/%s"

  :aliases           {}

  ;; to get process on port 55055:   sudo lsof -n -i :55055
  :repl-options      {:port 55055}

  :codox             {
                      :doc-paths ["docs"]
                      :output-path "target/docs"
                      :namespaces [george.application.turtle.turtle]
                      :source-uri
                      ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
                      "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
                      :html {:namespace-list :flat}}

  :profiles          {:repl {:env {:repl? "true"}}

                      :dev {:java-source-paths ["src/dev/java"]
                            :source-paths      ["src/dev/clj" "src/tasks"]
                            :resource-paths    ["src/dev/rsc"]}

                      :uberjar {:aot      :all
                                :manifest {"Main-Class" "no.andante.george.Main"
                                           "JavaFX-Preloader-Class" "no.andante.george.MainPreloader"
                                           "JavaFX-Application-Class" "no.andante.george.Main"}}})
