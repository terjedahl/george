
(defmacro code
  "Returns a single string containing the pr-str'd representations of the given expressions, wrapped in a do if more than one.
  Useful for writing expressions to be evaluated in leiningen aliases using 'exec -e'.
  (Similar to clojure.tools.nrepl/code, but for the potential 'do' wrapping.)"
  [& body]
  (if (< 1 (count body))
    `(prn-str (quote (do ~@body)))
    `(prn-str (quote ~@body))))
;(prn (macroexpand-1 '(code (+ 4 5))))
;(prn (code (+ 4 5)))
;(prn (macroexpand-1 '(code (+ 4 5) (+ 6 7))))
;(prn (code (+ 4 5) (+ 6 7)))


(defmacro defcode
  "Passes body to 'code', then defs the resulting string with the given name."
  [name & body]
  `(def ~name (code ~@body)))
;(prn (macroexpand '(defcode x (+ 4 5))))
;(prn (defcode y (+ 4 5)))
;(prn 'y y)
;(prn (macroexpand-1 '(defcode x (+ 4 5) (+ 6 7))))
;(prn (defcode z (+ 4 5) (+ 6 7)))
;(prn 'z z)


(defproject no.andante.george/george-application  "2019.0-SNAPSHOT"

  :description       "George - Application"
  :url               "https://bitbucket.org/andante-george/george-application"
  :license           {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies      [;; https://clojure.org/community/downloads
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
                      [environ "1.1.0"]
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
                      [org.openjfx/javafx-graphics "11.0.1"]
                      [org.openjfx/javafx-fxml     "11.0.1"]
                      [org.openjfx/javafx-swing    "11.0.1"] ;; used in Språklab
                      [org.openjfx/javafx-web      "11.0.1"]
                      [org.openjfx/javafx-media    "11.0.1"]

                      ;; included here also for IDE usability
                      [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]]

  :jar-exclusions    [#".DS_Store" #"arm.spraklab.*(clj|java)$"]


  :uberjar-exclusions [;; Used for Leiningen tasks
                       #"conch.*\.jar"
                       #"org.eclipse.jetty.*"
                       ;; Java9+ modules does not allow unnamed packages (class-file in top-level).
                       #"^g[$|__init].*class" #"^user.*class" #"^clj.tuple.*class" #"^potemkin.*class"]

  :plugins           [
                      ;; https://github.com/weavejester/environ
                      [lein-environ "1.1.0"]
                      ;; https://github.com/weavejester/codox
                      [lein-codox "0.10.5"]
                      ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
                      [lein-pprint "1.2.0"]
                      ;; https://github.com/hyPiRion/lein-shell
                      [lein-shell "0.5.0"]
                      ;; https://github.com/kumarshantanu/lein-exec
                      [lein-exec "0.3.7"]
                      ;; https://github.com/technomancy/lein-thrush
                      ;[lein-thrush "0.1.1"]

                      ;; https://www.eclipse.org/jetty
                      ;; Used by 'server' task
                      [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]
                      ;; Needed because it is in user.clj which is run as part of accessing the application code via .lein-classpath
                      [potemkin "0.4.5"]]

  :repositories      [
                      ;; junique
                      ["github-terjedahl-junique"
                       {:url "https://raw.githubusercontent.com/terjedahl/junique/master/maven2"
                        :snapshots false}]]

  :deploy-repositories [
                        ["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths      ["src/main/clj"   "src/arm-spraklab/src/clj"]
  :java-source-paths ["src/main/java"  "src/arm-spraklab/src/java"]
  :resource-paths    ["src/main/rsc"   "src/arm-spraklab/src/rsc"
                      "include"        "src/arm-spraklab/include"]
  :test-paths        ["src/test/clj"]

  :javac-options     ["-source" "11"  "-target" "11"]
                      ;"-Xlint:unchecked"
                      ;"-Xlint:deprecation"]

  :prep-task         ["javac" "compile"]

  :aot               [no.andante.george.Run
                      no.andante.george.Launch]

  :main              no.andante.george.Launch

  :jvm-opts          [
                      ;; should give crisper text on Mac
                      "-Dapple.awt.graphics.UseQuartz=true"]

  ;; Is used by 'lein jar' and others
  :target-path       "target/default/%s/"

  ;; Is used by 'lein clean'
  :clean-targets     ^:replace ["target/"]

  ;; We want to use the target-path for different things, but not have it totally cleaned whenever we run 'uberjar'.
  ;; Of course we then need to clean it ourselves when necessary.
  :auto-clean        false

  ;; Default config for 'lein server'
  ;; Port also used by 'lein serve'
  ;; Do 'lein help server' or 'lein help serve' for more
  :server            {:port 9998 :dir "."}


  :jre               {:modules [;; These modules are currently needed by George:
                                :java.sql
                                :java.scripting
                                :jdk.scripting.nashorn
                                ;; This module is needed for Marlin rendering engine and Clojure:
                                :jdk.unsupported
                                ;; These module may be needed by George later:
                                :java.desktop
                                :java.logging]}

  :aliases           {
                      "george" ^{:doc "            Print a list of custom tasks for this project"}
                      ["george"]}

  ;; to get process on port 55055:   sudo lsof -n -i :55055
  :repl-options     {:port 55055}


  :codox            {
                     :doc-paths ["docs"]
                     :output-path "target/docs"
                     :namespaces [george.application.turtle.turtle]
                     :source-uri
                     ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
                     "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
                     :html {:namespace-list :flat}}


  :profiles        {
                    :repl {
                           :env {:repl? "true"}}

                    :dev {;; Is used by 'lein javac', 'lein compile', 'lein run'
                          :target-path       "target/classes/%s/"
                          :java-source-paths ["src/dev/java"]
                          :source-paths      ["src/dev/clj" "src/tasks"]
                          :resource-paths    ["src/dev/rsc"]}

                    :uberjar {;; Is applied by 'uberjar'
                              :target-path "target/uberjar/"
                              :prep-task   ["clean" "javac" "compile"]

                              :aot :all}})
                             ;:manifest {"Main-Class" "no.andante.george.Launch"}}})


