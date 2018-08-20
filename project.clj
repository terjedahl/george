(def module-list
  [
   ;; These modules are currently needed by George:
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
   :jdk.unsupported])


(require
  '[clojure.string :as cs]
  '[clojure.java.io :as cio])


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


(def modules-str
  (apply str (interpose "," (map name module-list))))


(def module-info-spitter 
  (let [reqs (apply str (map #(str "    requires " (name %) ";\n") module-list))         
        tmpl "module george {\n    exports no.andante.george;\n%s}"   
        java (format tmpl reqs)]
    (format "(do (println \"%s\")(spit \"src/main/java/module-info.java\" \"%s\"))" java java))) 
;(module-info-spitter)


(defcode module-info-deleter
  (require '[clojure.java.io :as cio]) (cio/delete-file "src/main/java/module-info.java" true))
;(module-info-deleter)


(defcode
  assert10
  (assert (.startsWith (System/getProperty "java.version") "10")
    "This project requires Java 10.  See docs/java10.md for more."))


(defcode server-status
    (try
      (println (slurp "http://localhost:9999/_cmd/status"))
      (catch java.net.ConnectException _
        (binding [*err* *out*]
          (println "Server not running.  Do 'lein server-start' to start jar-server.")))))

(defcode server-stop
  (try
      (println (slurp "http://localhost:9999/_cmd/stop"))
      (catch java.net.ConnectException _
        (binding [*err* *out*]
          (println "Waring: Server not running. Did you already stop it?")))))


(defproject no.andante.george/george-application  "2018.7-SNAPSHOT"

  :description "George - Application"
  :url "https://bitbucket.org/andante-george/george-application"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
    
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
                 ;; TODO: remove user.clj
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
                

                  ;; Used for Leiningen tasks
  :uberjar-exclusions [#"conch.*\.jar" 
                       #"org.eclipse.jetty.*"]

  :aot-exclude []  ;; just to avoid the warning

  :plugins [
            ;; https://github.com/weavejester/environ
            [lein-environ "1.1.0"]
            ;; https://github.com/weavejester/codox
            [lein-codox "0.10.3"]
            ;; https://github.com/technomancy/leiningen/tree/stable/lein-pprint
            [lein-pprint "1.1.2"]
            ;; https://github.com/pallet/lein-aot-filter
            [lein-aot-filter "0.1.0"]
            ;; https://github.com/hyPiRion/lein-shell 
            [lein-shell "0.5.0"]
            ;; https://github.com/kumarshantanu/lein-exec
            [lein-exec "0.3.7"]
            ;; https://github.com/technomancy/lein-thrush
            [lein-thrush "0.1.1"]

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
  
  :prep-task ["javac" "compile"]
  
  :aot [no.andante.george.Run
        no.andante.george.Launch]
        
  :main no.andante.george.Launch

  :jvm-opts [
             ;; should give crisper text on Mac
             "-Dapple.awt.graphics.UseQuartz=true"]
             
  :target-path "target/%s/"

  ;; We want to use the target-path for different things, but not have it totally cleaned whenever we run 'uberjar'.
  ;; Of course we then need to clean it ourselves when necessary.
  ;:auto-clean false

  ;; Custom keys - used in aliases and profiles
  :java-home         ~(System/getenv "JAVA_HOME")
  :uberjar-path      "target/uberjar/george-application-${:version}-standalone.jar"

  :server {:port 8080 :dir "."}

  ;;  TODO: Ensure help for all tasks  
  :aliases {
            "build-jar" ^{:doc "            Does 'embed', 'uberjar', 'post-jar'. Optional args the same as 'embed'."}
                        ["do-args"
                         ["assert10"]
                         ["embed" :args]
                         ["uberjar"]
                         ["post-jar"]]

            "run-jar" ^{:doc "              Runs the built jar on the standard runtime."}
            ["java" "--illegal-access=permit" "-jar" :project/uberjar-path]
            
            "embed" ^{:doc "                Embeds a properties file in the source. Optional ordered args are ['appid' 'uri' 'ts']"}
            ["run" "-m" "tasks.build/embed-properties" :project/version]
                          
            ;; Internal
            "post-jar" ^{:doc "             Internal: Copies the built jar to 'target/launch/', and writes an updated properties file."}
            ["run" "-m" "tasks.build/post-uberjar"]
                        
            "install-jar" ^{:doc "          Copies the result of 'build-jar' to the default install location."}
            ["run" "-m" "tasks.deploy/install-jar"]

            "install-dir" ^{:doc "          NO IMPL. Prints the directory used for installing on you machine."}
            ["echo" "NO IMPL"]
            
            "serve-jar" ^{:doc "            Copies the result of 'build-jar' to target/serve, to which 'lein server-start' defaults."}
            ["run" "-m" "tasks.deploy/serve-jar"]

            "server-start" ^{:doc "         NO IMPL."}
            ["server" "--port" "9999" "--dir" "./target/serve"]

            "server-status" ^{:doc "        NO IMPL."}
            ["eval" ~server-status]

            "server-stop" ^{:doc "          NO IMPL."}
            ["eval" ~server-stop]
            
            "aws-jar" ^{:doc "              Deploys the result of 'build-jar' to AWS."}
            ["run" "-m" "tasks.deploy/aws-jar"]
                        
            "echo" ^{:doc "                 Prints arguments to standard out."}
            ["shell" "echo"]
            
            "eval" ^{:doc "                 Evaluates argument as clojure."}
            ["exec" "-e"]
            
            "assert10" ^{:doc "             Asserts Java 10"}
            ["exec" "-e" ~assert10]
            
            "java" ^:pass-through-help
            ["do" ["assert10"] "shell" "${:java-home}/bin/java"]
            
            "jmod" ^:pass-through-help
            ["shell" "${:java-home}/bin/jmod"]
            
            "jlink" ^:pass-through-help
            ["shell" "${:java-home}/bin/jlink"]
            
            "xjava" ^:pass-through-help
            ["shell" "target/jre/bin/java"]
            
            "build" ^{:doc "                Builds the uberjar and the custom runtime."}
            ["do"
             ["assert10"]
             ["exec" "-e" "(prn 'build-all '...)"]
             "build-jar"
             ["exec" "-e" "(prn 'build-jre '...)"]
             ["with-profile" "jre" "clean"]
             ["jlink"
              "--output" "target/jre"
              "--compress=2"
              "--no-header-files"
              "--add-modules" ~modules-str]]
            
            "build-jpms" ^{:doc "           Builds the uberjar and the custom runtime."}
            ["do" 
              ["eval" ~module-info-spitter]
              ["with-profile" "jpms" "build"]
              ["eval" ~module-info-deleter]
              ["jmod" "describe" "--module-path" :project/uberjar-path]]
            
            "xrun" ^{:doc "                 Runs the built jar on the custom runtime."}
            ["xjava" "-jar" :project/uberjar-path]
            
            "xrun-jpms" ^{:doc "            Runs the built jar in JPMS mode on the custom runtime. (It will fail, even when with extra --add-export)"}
            ["xjava" "--module-path" :project/uberjar-path "--module" "george/no.andante.george.Main"]}
            
            
  
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
             
             :dev {               
                   :java-source-paths ["src/dev/java"]
                   :source-paths      ["src/dev/clj" "src/tasks"]
                   :resource-paths    ["src/dev/rsc"]}
                    
             :uberjar {  
                       :auto-clean true
                       :clean-targets ^:replace ["target/uberjar"]
                       
                       :aot :all
                       :manifest {"Main-Class" "no.andante.george.Launch"}}
             
             :jre {
                   :target-path "target/jre/"
                   :clean-targets ^:replace ["target/jre/"]}

             :jpms {
                    ;; Java9+ modules does not allow unnamed packages (class-file in top-level). Therefore these must not be AOT-ed.
                    :aot-exclude [#"g.*" #"user.*" #"clj.tuple.*" #"potemkin.*"]}})
                    
