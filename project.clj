
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


(defproject no.andante.george/george-application  "2018.7-SNAPSHOT"

  :description "George - Application"
  :url         "https://bitbucket.org/andante-george/george-application"
  :license     {:name "Eclipse Public License"
                :url  "http://www.eclipse.org/legal/epl-v10.html"}
  
    
  :dependencies [[org.clojure/clojure "1.9.0"]
                 ;; https://github.com/clojure/core.async
                 [org.clojure/core.async "0.4.474"]
                 ;; https://github.com/clojure/tools.reader
                 [org.clojure/tools.reader "1.1.1"]
                 ;; https://github.com/mmcgrana/clj-stacktrace
                 [clj-stacktrace "0.2.8"]
                 ;[leiningen "2.8.1" :exclusions [org.clojure/clojure clj-stacktrace]]
                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha4"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.3.0"]
                 ;; https://github.com/cemerick/nREPL
                 [nrepl "0.4.1"]
                 ;; https://github.com/FXMisc/RichTextFX
                 [org.fxmisc.richtext/richtextfx "0.9.0"]
                 ;; https://github.com/TomasMikula/Flowless
                 [org.fxmisc.flowless/flowless  "0.6.1"]
                 ;; https://github.com/droitfintech/clj-diff
                 [tech.droit/clj-diff "1.0.0"]
                 ;; https://github.com/clojure/core.rrb-vector
                 [org.clojure/core.rrb-vector "0.0.11"]
                 ;; https://github.com/clojure/data.json
                 [org.clojure/data.json "0.2.6"]
                 ;; https://github.com/weavejester/environ
                 [environ "1.1.0"]
                 ;; https://github.com/ztellman/potemkin
                 ;; TODO: remove user.clj ... maybe
                 [potemkin "0.4.4"]
                 ;; https://github.com/clj-time/clj-time
                 [clj-time "0.13.0"]
                 ;; https://github.com/yogthos/markdown-clj
                 [markdown-clj "1.0.2"]
                 ;; https://github.com/alexander-yakushev/defprecated
                 [defprecated "0.1.3" :exclusions [org.clojure/clojure]]
                 ;; https://github.com/amalloy/ordered
                 [org.flatland/ordered "1.5.6"]
                 ;; https://github.com/terjedahl/junique
                 [it.sauronsoftware/junique "1.0.4"]
                 
                 ;; https://github.com/Raynes/conch
                 [me.raynes/conch "0.8.0" :exclusions [org.clojure/clojure]]
                 ;; included here also for IDE usability
                 [org.eclipse.jetty/jetty-server "9.0.0.v20130308"]]
  
                  
  :uberjar-exclusions [;; Used for Leiningen tasks
                       #"conch.*\.jar" 
                       #"org.eclipse.jetty.*"
                       ;; Java9+ modules does not allow unnamed packages (class-file in top-level).
                       #"^g[$|__init].*class" #"^user.*class" #"^clj.tuple.*class" #"^potemkin.*class"]

  :plugins [
            ;; https://github.com/weavejester/environ
            [lein-environ "1.1.0"]
            ;; https://github.com/weavejester/codox
            [lein-codox "0.10.3"]
            ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
            [lein-pprint "1.1.2"]
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
            [potemkin "0.4.4"]]
  
  :repositories [
                 ;; apache.commons.io
                 ["jcenter" "https://jcenter.bintray.com"]
                 ;; junique
                 ["github-terjedahl-junique"
                  {:url "https://raw.githubusercontent.com/terjedahl/junique/master/maven2"
                   :snapshots false}]]
                  
  :deploy-repositories [
                        ["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths      ["src/main/clj"]
  :java-source-paths ["src/main/java"]
  :resource-paths    ["src/main/rsc"]
  :test-paths        ["src/test/clj"]
  
  :javac-options     ["-target" "10" "-source" "10"]
  ;:javac-options     ["-target" "1.8" "-source" "1.8"]
  
  :prep-task         ["javac" 
                      "compile"]
  
  :aot [no.andante.george.Run
        no.andante.george.Launch]
        
  :main no.andante.george.Launch

  :jvm-opts [
             ;; should give crisper text on Mac
             "-Dapple.awt.graphics.UseQuartz=true"]
  
  ;; Is used by 'lein jar' and others
  :target-path "target/default/%s/"
  
  ;; Is used by 'lein clean'
  :clean-targets ^:replace ["target/"]
  
  ;; We want to use the target-path for different things, but not have it totally cleaned whenever we run 'uberjar'.
  ;; Of course we then need to clean it ourselves when necessary.
  :auto-clean false
  
  ;; Default config for 'lein server'
  ;; Port also used by 'lein serve'
  ;; Do 'lein help server' or 'lein help serve' for more
  :server {:port 9998 :dir "."}
  
  
  :jre {:modules [;; These modules are currently needed by George:
                  :java.sql
                  :javafx.controls
                  :javafx.graphics
                  :javafx.swing  ;; used in current javafx-init
                  :javafx.web
                  :java.scripting
                  :jdk.scripting.nashorn
                  ;; These module may be needed by George later:
                  :java.desktop
                  :java.logging
                  :javafx.media
                  ;; These modules are included so users may have access to them from the custom runtime (none yet):
                  ;; This module is needed by Clojure:
                  :jdk.unsupported]} 
  
  :aliases {
            "george" ^{:doc "            Print a list of custom tasks for this project"}
            ["george"]}
            
  
  :codox {
          :doc-paths ["docs"]
          :output-path "target/docs"
          :namespaces [george.application.turtle.turtle]
          :source-uri
          ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
          "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
          :html {:namespace-list :flat}}

  
  :profiles {
             :repl {
                    :env {:repl? "true"}}
             
             :dev {;; Is used by 'lein javac', 'lein compile', 'lein run'
                   :target-path       "target/classes/%s/"
                   :java-source-paths ["src/dev/java"]
                   :source-paths      ["src/dev/clj" "src/tasks"]
                   :resource-paths    ["src/dev/rsc"]}
                       
             :uberjar {;; Is applied by 'uberjar'
                       :target-path "target/uberjar/"
                       :aot :all}})
                       ;:manifest {"Main-Class" "no.andante.george.Launch"}}})
             
                    
