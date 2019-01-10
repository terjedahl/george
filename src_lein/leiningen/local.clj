;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.local
  (:refer-clojure :exclude [list])
  (:require
    [leiningen.george.core :as g]
    [leiningen.help :as lh]))



(defn install
  "Copy the built JAR and properties-file to the local install dir."
  []
  (g/do-local-install))


(defn list
  "Prints the full path to the local install dir, and its content."
  []
  (g/do-local-list))

(defn clean
  "Deletes all files in local install dir, but leaves the dir itself in place."
  []
  (g/do-local-clean))

(defn local
  "Install or remove jar-file locally.     ..."
  {:subtasks [#'install  #'list #'clean]}
  [project & [subtask]]
  (binding [g/*project* project]
    (case subtask
      "install" (install)
      "list"  (list)
      "clean" (clean)
      (lh/help project "local"))))