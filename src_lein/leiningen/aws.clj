;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.aws
  (:require
    [leiningen.george :as g]))


(defn macos
  "Deploys native MacOS files to AWS"
  [args]
  (println "No IMPL yet."))


(defn windows
  "Deploys native Windows files to AWS"
  [args]
  (println "No IMPL yet."))


(defn deployable 
  "Deploys JAR + app-file to AWS"
  [args]
  (g/assert-deployable)
  (g/assert-project)
  (g/run 'tasks.deploy/aws args))


(defn aws 
  "Deploy deployable or native files to AWS
  
A subtask must be specified.
Currently for use only by Andante"

  {:subtasks [#'deployable #'macos #'windows]}
  [project & [subtask & args-]]
  (binding [g/*project* project]
    (case subtask
      "deployable" (deployable args-)
      "macos"      (macos args-)
      "windows"    (windows args-)
      (println "No subtask specified.\n Do 'lein help aws'"))))