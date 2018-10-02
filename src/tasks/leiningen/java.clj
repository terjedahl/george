; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.java
  (:require [leiningen.george :as g]))



(defn- deployable
  "Run the built deployable on the default java"
  [args]
  (g/assert-deployable)
  (g/java (concat ["--illegal-access=permit" "-jar" (g/deployable-jar-path)] args)))


(defn- jpms
  "Run the built deployable on default java as JPMS"
  [args]
  (g/assert-jpms)
  (g/java (concat [ "--module-path" (g/deployable-jar-path) "--module" "george/no.andante.george.Launch"] args)))


(defn java
  "Run default 'java' (JAVA_HOME)"
  {:subtasks [#'g/jmod #'g/jlink #'jpms #'g/home]}

  [project & [subtask & args- :as args]]
  (binding [g/*project* project]
    (case subtask
      "jmod"         (g/jmod args-)
      "jlink"        (g/jlink args-)
      "deployable"   (deployable args-)
      "jpms"         (jpms args-)
      "home"         (g/home)
      (g/java args))))
