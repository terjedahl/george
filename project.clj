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



(defproject no.andante.george/george-application  "2018.6-SNAPSHOT"

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
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]
                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha4"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.3.0"]
                 ;; https://github.com/cemerick/nREPL
                 [com.cemerick/nrepl "0.3.0-RC1"]
                 ;; https://github.com/FXMisc/RichTextFX
                 [org.fxmisc.richtext/richtextfx "0.8.1"]
                 ;; https://github.com/TomasMikula/Flowless
                 [org.fxmisc.flowless/flowless  "0.6"]
                 ;; https://github.com/brentonashworth/clj-diff
                 [clj-diff "1.0.0-SNAPSHOT"]
                 ;; https://github.com/clojure/core.rrb-vector
                 [org.clojure/core.rrb-vector "0.0.11"]
                 ;; https://github.com/clojure/data.json
                 [org.clojure/data.json "0.2.6"]
                 ;; https://github.com/weavejester/environ
                 [environ "1.1.0"]
                 ;; https://github.com/ztellman/potemkin
                 [potemkin "0.4.4"]
                 ;; https://github.com/clj-time/clj-time
                 [clj-time "0.13.0"]
                 ;; https://github.com/yogthos/markdown-clj
                 [markdown-clj "1.0.2"]
                 ;; https://github.com/alexander-yakushev/defprecated
                 [defprecated "0.1.3" :exclusions [org.clojure/clojure]]
                 ;; https://github.com/amalloy/ordered
                 [org.flatland/ordered "1.5.6"]]

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
            [lein-exec "0.3.7"]]

  :repositories [
                 ;; apache.commons.io
                 ["jcenter" "https://jcenter.bintray.com"]]
                  
  :deploy-repositories [
                        ["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths      ["src/main/clj"]
  :java-source-paths ["src/main/java"]
  :resource-paths    ["src/main/rsc"]
  :test-paths        ["src/test/clj"]
  
  :javac-options     ["-target" "10" "-source" "10"]
  
  :prep-task ["javac" "compile"]
  
  :aot [no.andante.george.Main]
  :main no.andante.george.Main

  :jvm-opts [
             ;; should give crisper text on Mac
             "-Dapple.awt.graphics.UseQuartz=true"]
             
  :target-path "target/%s/"


  ;; Custom keys - used in aliases and profiles
  :java-home         ~(System/getenv "JAVA_HOME")
  :uberjar-path      "target/uberjar/george-application-${:version}-standalone.jar"

  
  :aliases {
            "echo" ^{:doc "Prints arguments to standard out."}
            ["shell" "echo"]
            
            "eval" ^{:doc "Evaluates argument as clojure."}
            ["exec" "-e"]
            
            "assert10" ^{:doc "Asserts Java 10"}
            ["exec" "-e" ~assert10]
            
            "java" ^:pass-through-help
            ["do" ["assert10"] "shell" "${:java-home}/bin/java"]
            
            "jmod" ^:pass-through-help
            ["shell" "${:java-home}/bin/jmod"]
            
            "jlink" ^:pass-through-help
            ["shell" "${:java-home}/bin/jlink"]
            
            "xjava" ^:pass-through-help
            ["shell" "target/jre/bin/java"]
            
            "build" ^{:doc "Builds the uberjar and the custom runtime."}
            ["do"
             ["assert10"]
             ["exec" "-e" "(prn 'build-all '...)"]
             "uberjar"
             ["exec" "-e" "(prn 'build-jre '...)"]
             ["with-profile" "jre" "clean"]
             ["jlink"
              "--output" "target/jre"
              "--compress=2"
              "--no-header-files"
              "--add-modules" ~modules-str]]
            
            "build-jpms" ^{:doc "Builds the uberjar and the custom runtime."}
            ["do" 
              ["eval" ~module-info-spitter]
              ["with-profile" "jpms" "build"]
              ["eval" ~module-info-deleter]
              ["jmod" "describe" "--module-path" :project/uberjar-path]]
            
            "xrun" ^{:doc "Runs the built jar on the custom runtime."}
            ["xjava" "-jar" :project/uberjar-path]
            
            "xrun-jpms" ^{:doc "Runs the built jar in JPMS mode on the custom runtime. (It will fail, even when with extra --add-export)"}
            ["xjava" "--module-path" :project/uberjar-path "--module" "george/no.andante.george.Main"]
            
            "srun" ^{:doc "Runs the built jar on the standard runtime."}
            ["java" "-jar" :project/uberjar-path]}
            
  
  :codox {
          :doc-paths ["docs"]
          :output-path "target/docs"
          :namespaces [george.application.turtle.turtle]
          :source-uri
          ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
          "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
          :html {:namespace-list :flat}}

  
  :profiles {
             :repl {:env {:repl? "true"}}
             
             :dev {:depencencies []
                   :java-source-paths ["src/dev/java"]
                   :source-paths      ["src/dev/clj"]
                   :resource-paths    ["src/dev/rsc"]}
                    
             :uberjar {:aot :all
                       :manifest {"Main-Class" "no.andante.george.Main"
                                  "JavaFX-Preloader-Class" "no.andante.george.MainPreloader"
                                  "JavaFX-Application-Class" "no.andante.george.Main"}}
             
             :jre {
                   :target-path "target/jre/"
                   :clean-targets ^:replace ["target/jre/"]}

             :jpms {
                    ;; Java9+ modules does not allow unnamed packages (class-file in top-level). Therefore these must not be AOT-ed.
                    :aot-exclude [#"g.*" #"user.*" #"clj.tuple.*" #"potemkin.*"]}})
                    
