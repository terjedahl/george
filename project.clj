(defmacro defstr
  "defs name to have the root value of the body wrapped as an unavaluated set of expressions wrapped in a do.
  Makes it easy to write functionality which can then be evaluated in leiningen aliases."
  {:added "1.0"}
  [name & body]
  `(def ~name (prn-str (quote (do ~@body)))))

;(prn (macroexpand-1 '(defstr x (+ 4 5))))
;(prn (defstr y (+ 6 7)))
;(prn 'y y)


(defstr mkdirs
  (doseq [d ["target/runtime/classes" "target/runtime/jar" "target/runtime/mod"]]
    (.mkdirs (java.io.File. d))))


(defstr spit-javac-options
  (let [src "src/main/java"
        trg "target/runtime/classes"
        file "javac-options.txt"
        p (.toPath (java.io.File. src))
        files 
        (->>
          (.toArray (java.nio.file.Files/walk p (make-array java.nio.file.FileVisitOption 0)))
          (map str)
          (filter #(.endsWith % ".java")))
        out 
        (format "-d %s -s %s %s" trg src  (apply str (interpose " " files)))]
    (spit (str trg "/" file) out)))


(def modules
  (apply str 
    (interpose ","
      (map name
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
         
         ;; This module is needed by Clojure:
         :jdk.unsupported
         ;; Needed by Leiningen
         :java.compiler]))))
         ;; These modules are included so users may have access to them from the custom runtime (none yet):


(def xjava "target/runtime/jre/bin/java")




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
             
  :target-path "target/build/%s/"
  :clean-targets [:target-path]


  ;; Custom keys - used in aliases and profiles
  :java-home         ~(System/getenv "JAVA_HOME")
  :uberjar-path      "target/build/uberjar/george-application-${:version}-standalone.jar"
  
  
  :aliases {
            "assert10" ^{:doc "Asserts Java 10"}
            ["exec" "-e" "(assert (.startsWith (System/getProperty \"java.version\") \"10\") \"This project requires Java 10.  See docs/java10.md for more.\")"]
            
            "java" ^:pass-through-help
            ["do" ["assert10"] "shell" "${:java-home}/bin/java"]
            
            "jlink" ^:pass-through-help
            ["do" ["assert10"] "shell" "${:java-home}/bin/jlink"]

            "xjava" ^:pass-through-help
            ["shell" ~xjava]
            
            "build-jre"
            ["do"
             ["exec" "-e" "(prn 'build-jre '...)"]
             ["with-profile" "jre" "clean"]
             ["jlink"
              "--output" "target/runtime/jre"
              "--compress=2"
              "--no-header-files"
              "--add-modules" ~modules]]            
            
            "build-jar"
            ["do"
             ["exec" "-e" "(prn 'build-jar '...)"]
             "uberjar"]
             
             
            "build-all" ;; TODO: docstring
            ["do"
             ["exec" "-e" "(prn 'build-all '...)"]
             "build-jre" 
             "build-jar"]

            
            "xrun-jar" ;; TODO: docstring
            ["xjava" "-jar" :project/uberjar-path]

            "run-jar" ;; TODO: docstring
            ["java" "-jar" :project/uberjar-path]

            "clean-all" ;; TODO: docstring
            ["do" 
             "clean" 
             ["with-profile" "jre" "clean"]]}
  
  :codox {
          :doc-paths ["docs"]
          :output-path "target/docs"
          :namespaces [george.application.turtle.turtle]
          :source-uri
          ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
          "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
          :html {:namespace-list :flat}}

  
  :uberjar-exclusions [#"module-info.*"]
  
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
                   :target-path "target/runtime/"
                   :clean-targets ^:replace ["target/runtime/"]}})
             
             ;:jpms {
             ;       ;; Java9+ modules does not allow unnamed packages (class-file in top-level). Therefore these must not be AOT-ed.
             ;       :aot-exclude [#"g.*" #"user.*" #"clj.tuple.*" #"potemkin.*"]
             ;       ;; So the module-info does not get stripped out.
             ;       :uberjar-exclusions ^:replace []}})
