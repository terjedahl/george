; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns tasks.george
  (:require 
    [george.launch.properties :as p]))


(defn print-now []
  (println "now:" (p/now-ts)))


(defn- build-help [] 
  (println "Usage: lein george jar build <optional-ordered-args>
  
Builds the jar.
First  embeds app-file in source, then does uberjar, then moves uberjar to dir 'target/jar', then writes app-file next to jar-file.
  
Optional ordered args (which are inserted in app-file) are appid, uri, timestamp.
Write nil for default. Use ':serve' for uri if you wish to do testing with project server functionality. 
  
Examples:
  'lein george jar build'
  'lein george jar build nil nil <some-timestamp-here>'
  'lein george jar build nil http://localhost:9999 2019-01-01T00:00:00>'
  'lein george jar build nil :serve 2019-01-01T00:00:00>'
  'lein george jar build George'"))


(defn- do-build []
  (println "Buidling ...(NOT)"))
  

(defn- build [args]
  (case (first args)
    "help" (build-help)
    ;; else
    (do-build)))  


(defn jar [& args]
  (case (first args)
    "build" (build (rest args))
    ;; else
    (println "Usage: lein george jar [subtask] <args>
These subtasks are available:

  build     Builds the jar.  Do 'lein george jar build help' for more. 
  run       <optional args to jars main method>")))


(defn props [& args]
  (case (first args)
    "embed" (println "Embedding app-file ... (NOT)")
    "write" (println "Writing app-file ... (NOT)")
    ;; else
    (println "Usage: lein george props <subtask>
Subtasks available are:
  embed    Embeds app-file in source.  Args are the same as for build.
  write    Writes a complete app-file next to the jar-file based on values in embedded app-file.")))