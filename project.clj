(defproject no.andante.george/george-application  "2019.0-SNAPSHOT"

  :description         "George - Application"
  :url                 "https://bitbucket.org/andante-george/george-application"
  :license             {:name "Eclipse Public License"
                        :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies        [;; https://clojure.org/community/downloads
                        [org.clojure/clojure "1.10.0"]
                        ;; https://github.com/clojure/core.async
                        [org.clojure/core.async "0.4.490"]
                        ;; https://github.com/clojure/tools.reader
                        [org.clojure/tools.reader "1.3.2"]
                        ;; https://github.com/mmcgrana/clj-stacktrace
                        [clj-stacktrace "0.2.8"]
                        ;; https://github.com/cemerick/nREPL
                        [nrepl "0.5.3"]
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
                        [environ "1.1.0" :exclusions [org.clojure/clojure]]
                        ;; https://github.com/ztellman/potemkin
                        ;; TODO: remove user.clj ... maybe
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
                        [zcaudate/hara.io.watch "2.8.7"]
                        ;; https://github.com/terjedahl/junique
                        [it.sauronsoftware/junique "1.0.4"]

                        ;; https://github.com/Raynes/conch
                        [me.raynes/conch "0.8.0" :exclusions [org.clojure/clojure]]

                        ;; https://openjfx.io
                        ;; https://search.maven.org/search?q=g:org.openjfx%20AND%20v:11.0.1
                        [org.openjfx/javafx-controls "11.0.1"]
                        [org.openjfx/javafx-fxml     "11.0.1"]
                        [org.openjfx/javafx-swing    "11.0.1"] ;; required for Språklab
                        [org.openjfx/javafx-web      "11.0.1"]
                        [org.openjfx/javafx-media    "11.0.1"]

                        ;; included here also for IDE usability
                        [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]]

  :jar-exclusions      [#".DS_Store" #"arm.spraklab.*(clj|java)$"]


  :uberjar-exclusions  [;; Used for Leiningen tasks
                        #"conch.*\.jar"
                        #"org.eclipse.jetty.*"
                        ;; Java9+ modules does not allow unnamed packages (class-file in top-level).
                        #"^g[$|__init].*class" #"^user.*class" #"^clj.tuple.*class" #"^potemkin.*class"]

  :plugins             [;; https://github.com/weavejester/environ
                        [lein-environ "1.1.0"]
                        ;; https://github.com/weavejester/codox
                        [lein-codox "0.10.5"]
                        ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
                        [lein-pprint "1.2.0"]

                        ;; Used by different tasks
                        [environ "1.1.0" :exclusions [org.clojure/clojure]]
                        ;; https://github.com/yogthos/Selmer
                        [selmer "1.12.5"]
                        ;; https://www.eclipse.org/jetty
                        [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]

                        ;; https://github.com/cemerick/pomegranate
                        [com.cemerick/pomegranate "1.1.0"]

                        ;; Used in user.clj which also gets loaded for Leiningen tasks
                        [potemkin "0.4.5"]]

  :repositories        [;; junique
                        ["github-terjedahl-junique"
                         {:url "https://raw.githubusercontent.com/terjedahl/junique/master/maven2"
                          :snapshots false}]]

  :deploy-repositories [["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths        ["src/clj"      "src_spraklab/clj"  "src_common"]
  :java-source-paths   ["src/java"     "src_spraklab/java"]
  :resource-paths      ["src/rsc"      "src_spraklab/rsc"
                        "src/include"  "src_spraklab/include"]

  :javac-options       ["-source" "11"  "-target" "11"]
                        ;"-Xlint:unchecked"
                        ;"-Xlint:deprecation"]

  :prep-task           ["javac" "compile"]

  :aot                 [no.andante.george.Run
                        no.andante.george.Launch]

  :main                no.andante.george.Launch

  :jvm-opts            [;; should give crisper text on Mac
                        "-Dapple.awt.graphics.UseQuartz=true"]

  ;; Is used by 'lein jar' and others
  :target-path         "target/default/%s/"

  ;; Is used by 'lein clean'
  :clean-targets       ^:replace ["target/"]

  ;; We want to use the target-path for different things, but not have it totally cleaned whenever we run 'uberjar'.
  ;; Of course we then need to clean it ourselves when necessary.
  :auto-clean          false

  ;; Default config for 'lein server'
  :server              {:port 9998
                        :dir "."}

  :build               {:msi-upgrade-codes {"George"      "14DE2AD0-4422-4D1F-8D80-F8EC5B9186BA"
                                            "George-TEST" "CB327885-311F-4724-AD4F-C15C7EAB33AB"
                                            :default      "92DB4AE3-F596-4FCA-8CB1-5E7B45A95340"}
                        :site {:port 9999}}


  :modules             {;; Used when building JRE
                        :jre  [;; Currently needed
                               :java.sql
                               :java.scripting
                               :jdk.scripting.nashorn
                               ;; Needed for Marlin rendering engine and Clojure
                               ;; (JavaFX modules themselves are dependencies as per Java 11)
                               :jdk.unsupported
                               ;; May be may needed later
                               :java.desktop
                               :java.logging
                               ;; Needed to get javac
                               :jdk.compiler
                               ;; Needed to get jdeps, jlink, jmod, et al
                               :jdk.jlink]
                               ;; https://docs.oracle.com/en/java/javase/11/docs/api/index.html

                        ;; Used when building George as JPMS module.  (Doesn't work. See docs/java11.md)
                        :jpms [:javafx.controls
                               :javafx.fxml
                               :javafx.swing
                               :javafx.web
                               :javafx.media]}

  :aliases             {"george" ^{:doc "            Print a list of custom tasks for this project"}
                        ["george"]}

  ;; to get PID for this port in unix shell, do:   sudo lsof -n -i :55055
  :repl-options        {:port 55055}

  :codox               {:doc-paths ["docs"]
                        :output-path "target/docs"
                        :namespaces [george.application.turtle.turtle]
                        :source-uri
                        ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
                        "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
                        :html {:namespace-list :flat}}

  :profiles            {:repl {:env {:repl? "true"}}

                        :dev {;; Is used by 'lein javac', 'lein compile', 'lein run'
                              :target-path       "target/classes/%s/"
                              :java-source-paths ["src_dev/java"]
                              :source-paths      ["src_dev/clj" "src_lein"]
                              :resource-paths    ["src_dev/rsc"]
                              :dependencies      [;; https://repo.clojars.org/leiningen/leiningen/
                                                  [leiningen "2.8.1" :exclusions [org.clojure/clojure clj-stacktrace]]]}

                        :uberjar {;; Is applied by 'uberjar'
                                  :target-path "target/uberjar/"
                                  :prep-task   ^:replace ["clean" "javac" "compile"]
                                  :aot :all}})