; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.jre
  (:require
    [leiningen.core.eval :refer [sh]]
    [leiningen.george :as g]
    [clojure.java.io :as cio]
    [environ.core :refer [env]])
  (:import
    [java.io File]))


(def JRE_D  (cio/file (:user-dir env) "target" "jre"))
(def JAVA_F (cio/file JRE_D "bin" (if (g/windows?) "java.exe" "java")))


(defn- assert-jre []
  (assert (.exists JRE_D) (format "'%s' not found. Have you done 'lein jre'?" JRE_D)))


(defn- assert-java []
  (assert-jre)
  (assert (.exists ^File JAVA_F) (format "'%s' not found. Have you done 'lein jre'?" JAVA_F)))


(defn- java 
  "Call 'java' on the custom built JRE

Try 'lein jre java -version' 
 or 'lein jre java --list-modules'"
  [args]
  (g/assert-project)
  (assert-java)
  (apply sh (cons (str JAVA_F) args)))


;; https://jaxenter.com/jdk-9-replace-permit-illegal-access-134180.html
(defn- deployable 
  "Run the built deployable on the JRE"
  [args]
  (g/assert-deployable)
  (java (concat [;"--illegal-access=permit"
                 "--illegal-access=warn"
                 ;"--illegal-access=debug"
                 "-jar" (g/deployable-jar-path)] args)))


(defn- jpms
  "Run the built deployable on the JRE as JPMS"
  [args]
  (g/assert-jpms)
  (java (concat [ "--module-path" (g/deployable-jar-path)
                 "--module" "no.andante.george.Launch"]
                args)))


(defn jre- []
  (g/assert-project)
  (g/assert-java11)
  (let [modules-str (apply str (interpose "," (map name (g/modules))))]
    ;(prn modules-str)
    (g/clean 'target/jre)
    (g/jlink ["--output" "target/jre"
              "--compress=2"
              "--no-header-files"
              "--add-modules" modules-str])
    (g/clean "target/jre/legal")
    (java ["--list-modules"])))


(defn jre
  "Build a custom JRE

Modules to include are specified in project.clj -> :jre -> :modules
  
Do 'lein help jre java' for tips on testing."
  {:subtasks [#'java #'deployable #'jpms]}
  [project & [subtask & args]]
  (binding [g/*project* project]
    (case subtask
      "java"       (java args)
      "deployable" (deployable args)
      "jpms"       (jpms args)
      (jre-))))
