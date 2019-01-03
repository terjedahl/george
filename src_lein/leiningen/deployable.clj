;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.deployable
  (:require
    [leiningen.george :as g]))


(defn install-dir
  "Prints the install-dir 

To (un)install do:
'lein deployable install'
'lein deployable uninstall'"
  []
  (g/assert-deployable)
  (g/assert-project)
  (g/run 'tasks.deploy/install-dir))


(defn install 
  "Install the deployable locally

To see path do:
'lein deployable install-dir'"
  []
  (g/assert-deployable)
  (g/assert-project)
  (g/run 'tasks.deploy/install))


(defn deployable 
  "Build the uberjar, then places it in target/deployable together with app-file
  
Same optional args as with 'lein embed'.  
Do 'lein help embed' for more.

The deployable can be run on standard java or on the custom JRE:
'lein java deployable'
'lein jre deployable'

See documentation on building and deploying to learn more."
  {:subtasks [#'install #'install-dir]}
  [project & [subtask & _ :as args]]
  (binding [g/*project* project]
    (case subtask
      "install"     (install)
      "install-dir" (install-dir)
      (g/deployable args))))