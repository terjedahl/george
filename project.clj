(defproject no.andante.george/george-application  "2019.1.2"

  :description         "George - the main application code"
  :url                 "https://bitbucket.org/andante-george/george-application"
  :license             {:name "Eclipse Public License"
                        :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies        [;; https://clojure.org/community/downloads
                        [org.clojure/clojure "1.10.0"]
                        ;; https://github.com/clojure/core.async
                        [org.clojure/core.async "0.4.490" :exclusions [org.ow2.asm/asm-all]]
                        ;; https://github.com/clojure/tools.reader
                        [org.clojure/tools.reader "1.3.2"]
                        ;; https://github.com/mmcgrana/clj-stacktrace
                        [clj-stacktrace "0.2.8"]
                        ;; https://github.com/cemerick/nREPL
                        [nrepl "0.5.3"]
                        ;; https://github.com/FXMisc/RichTextFX
                        [org.fxmisc.richtext/richtextfx "0.10.0"]
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
                        [potemkin "0.4.5"]
                        ;; https://github.com/yogthos/markdown-clj
                        [markdown-clj "1.0.5"]
                        ;; https://github.com/amalloy/ordered
                        [org.flatland/ordered "1.5.7"]
                        ;; https://github.com/zcaudate/hara
                        ;; http://docs.caudate.me/hara/hara-io-watch.html
                        [zcaudate/hara.common.watch "2.8.7"]
                        [zcaudate/hara.io.watch "2.8.7"]
                        ;; https://github.com/terjedahl/junique
                        [it.sauronsoftware/junique "1.0.4"]
                        ;; https://github.com/aalmiray/ikonli
                        [org.kordamp.ikonli/ikonli-javafx "11.1.0"]
                        ;; https://fontawesome.com/
                        [org.kordamp.ikonli/ikonli-fontawesome5-pack "11.1.0"]
                        ;; https://asm.ow2.io/index.html
                        [org.ow2.asm/asm "7.1"]
                        [org.ow2.asm/asm-tree "7.1"]
                        ;; https://github.com/java-diff-utils/java-diff-utils
                        [io.github.java-diff-utils/java-diff-utils "4.0"]]

  :jar-exclusions      [#".DS_Store" #"arm.spraklab.*(clj|java)$"]

  :plugins             [;; Required as plugins
                        ;; https://github.com/weavejester/environ
                        [lein-environ "1.1.0"]
                        ;; https://github.com/weavejester/codox
                        [lein-codox "0.10.5"]
                        ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
                        [lein-pprint "1.2.0"]

                        ;; Required by custom tasks
                        [environ "1.1.0" :exclusions [org.clojure/clojure]]
                        ;; https://github.com/yogthos/Selmer
                        [selmer "1.12.5"]
                        ;; https://www.eclipse.org/jetty
                        [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]
                        ;; https://github.com/cemerick/pomegranate
                        [com.cemerick/pomegranate "1.1.0"]
                        ;; https://github.com/kumarshantanu/lein-exec
                        [lein-exec "0.3.7"]
                        ;; https://github.com/xsc/lein-ancient
                        [lein-ancient "0.6.15"]]

  :repositories        [;; junique
                        ["github-terjedahl-junique"
                         {:url "https://raw.githubusercontent.com/terjedahl/junique/master/maven2"
                          :snapshots false}]]

  :deploy-repositories [["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths        ["src/clj"      "src_spraklab/clj"      "src_common"]
  :java-source-paths   ["src/java"     "src_spraklab/java"]
  :resource-paths      ["src/rsc"      "src_spraklab/rsc"
                        "src/include"  "src_spraklab/include"]

  :javac-options       ["-source" "11"  "-target" "11"]
                        ;"-Xlint:unchecked" "-Xlint:deprecation"]
                        ;; --module-path and --add-modules= ar appended via middleware

  :jvm-opts            [;; should give crisper text on Mac
                        "-Dapple.awt.graphics.UseQuartz=true"
                        ;; Prevent rendering issue seen on Windows and in lein run (from IDE shell) ... and even MacOS
                        ;; TODO: Investigate more!
                        "-Dprism.dirtyopts=false"]
                        ;; --module-path and --add-modules=are appended via middleware

  :prep-task           ["javac" "compile"]

  :aot                 [no.andante.george.Launch]

  :main                no.andante.george.Launch

  ;; Is used by 'lein jar' and others
  :target-path         "target/default/%s/"

  ;; Is used by 'lein clean'
  :clean-targets       ^:replace ["target/"]

  ;; We want to use the target-path for different things, but not have it totally cleaned whenever we run 'uberjar'.
  ;; Of course we then need to clean it ourselves when necessary.
  :auto-clean          false

  ;; Custom middleware
  :middleware         [leiningen.george.middleware/inject-javafx-modules]
  ;:implicit-middleware false

  ;; Default config for 'lein server'
  :server              {:port 9998
                        :dir "."}

  :build               {:msi-upgrade-codes {"George"      "14DE2AD0-4422-4D1F-8D80-F8EC5B9186BA"
                                            "George-TEST" "CB327885-311F-4724-AD4F-C15C7EAB33AB"
                                            :default      "92DB4AE3-F596-4FCA-8CB1-5E7B45A95340"}
                        :splash-image      "george_icon_128.png"
                        :site              {:port 9999}}

  :modules             {;; Download SKSs and jmods from: https://gluonhq.com/products/javafx/
                        ;; Required for javac, compile, java (building JAR and running lein and/or repl)
                        :libs {"Windows" "javafx-libs\\Windows\\javafx-sdk-12.0.1\\lib"
                               "MacOS"   "javafx-libs/MacOS/javafx-sdk-12.0.1/lib"
                               "Linux"   "javafx-libs/Linux/javafx-sdk-12.0.1/lib"}

                        ;; Required for jlink (building JRE)
                        :mods {"Windows" "javafx-libs\\Windows\\javafx-jmods-12.0.1"
                               "MacOS"   "javafx-libs/MacOS/javafx-jmods-12.0.1"
                               "Linux"   "javafx-libs/Linux/javafx-jmods-12.0.1"}

                        ;; https://docs.oracle.com/en/java/javase/11/docs/api/index.html
                        :java  [;; Currently required
                                :java.sql
                                :java.scripting
                                :jdk.scripting.nashorn
                                :java.desktop

                                ;; Required for Marlin rendering engine and Clojure
                                ;; (JavaFX modules themselves are dependencies as per Java 11)
                                :jdk.unsupported

                                ;; Required for Swing JavaFX interop.  Will not be needed for JavaFX 12
                                ;; https://bugs.openjdk.java.net/browse/JDK-8210759
                                :jdk.unsupported.desktop

                                ;; Currently not required
                                :java.logging

                                ;; Required to get javac
                                :jdk.compiler
                                ;; Required to get jdeps, jlink, jmod, et al
                                :jdk.jlink
                                ;; Required for no.andante.george.Agent
                                :java.instrument]

                        ;; https://openjfx.io
                        :javafx [:javafx.controls
                                 :javafx.fxml
                                 :javafx.swing  ;; required for george.javafx and Språklab
                                 :javafx.web
                                 :javafx.media]}

  :aliases             {"george" ^{:doc "            Print a list of custom tasks for the George project."}
                        ["george"]
                        "run-splash" ^{:doc "        Same as (lein) 'run', but includes the splash screen."}
                        ["with-profile" "with-splash" "run"]}

  ;; to get PID for this port in unix shell, do:   sudo lsof -n -i :55055
  ;:repl-options        {:port 55055}

  :codox               {:doc-paths ["docs"]
                        :output-path "target/docs"
                        :namespaces [george.application.turtle.turtle]
                        :source-uri
                        ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
                        "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
                        :html {:namespace-list :flat}}

  :profiles            {:repl {:env {:repl? "true"}}

                        :dev [:dev-common :dev-overrides]

                        :dev-overrides {} ;; Optionally  override this in your profiles.clj

                        :dev-common {;; Is used by 'lein javac', 'lein compile', 'lein run'
                                     :target-path       "target/classes/%s/"

                                     :java-source-paths ["src_dev/java"]

                                     :source-paths      ["src_dev/clj" "src_lein"]

                                     :resource-paths    ["src_dev/rsc"]

                                     ;; https://github.com/technomancy/leiningen/wiki/Faster
                                     :jvm-opts ["-Xverify:none"]

                                     :dependencies      [;; Not required, but included here (also) for IDE support

                                                         [selmer "1.12.5"]
                                                         ;; https://repo.clojars.org/leiningen/leiningen/
                                                         [leiningen "2.8.1" :exclusions [org.clojure/clojure clj-stacktrace]]

                                                         [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]

                                                         [org.openjfx/javafx-controls "12.0.1"]
                                                         [org.openjfx/javafx-fxml     "12.0.1"]
                                                         [org.openjfx/javafx-swing    "12.0.1"]
                                                         [org.openjfx/javafx-web      "12.0.1"]
                                                         [org.openjfx/javafx-media    "12.0.1"]]

                                     :build {:properties {;; You may want to override this in your profiles.clj
                                                          ;; (as :dev-overrides, not :dev or :dev-common)
                                                          :app "George-DEV"}}}

                        :uberjar {;; Is applied by 'uberjar' TODO: Investigate: Doesn't seem to have any effect.
                                  :target-path "target/uberjar/"
                                  :prep-task   ^:replace ["clean" "javac" "compile"]
                                  :aot :all
                                  :manifest   {;; Required to pick up the Java9+ version from RichTextFX MRJ
                                               "Multi-Release"            "true"
                                               ;; Required for no.andante.george.Agent
                                               "Premain-Class"            "no.andante.george.Agent"
                                               "Can-Retransform-Classes"  "true"}}

                        :with-splash {:with-splash true}})