;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george
  (:require
    [clojure.java.io :as cio]
    [leiningen.core.main :refer [info warn debug]]
    [leiningen.core.eval :refer [eval-in-project]]
    [leiningen.uberjar :refer [uberjar]]
    [leiningen.clean :refer [delete-file-recursively]]))




(defn  george
  "Tasks for building, testing, and deploying George."
  ;{:subtasks [#'a]}
  [project & [sub1 sub2]]
  
  (debug "DEBUG=true")
  (prn 'george sub1 sub2))
  ;(case sub1
  ;  "a"  (a project sub2)
  ;  nil      (bad-subtask "x")
  ;  (bad-subtask sub1)))

;; https://github.com/technomancy/leiningen/blob/master/doc/PLUGINS.md#project-specific-tasks
