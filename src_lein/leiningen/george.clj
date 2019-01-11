;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george
  (:require
    [leiningen.help :as lh]
    [bultitude.core :as blt]))


(defn- george-tasks
  "Similar to how help prints builtin and plugin tasks"
  []
  (->> (blt/namespaces-on-classpath :prefix "leiningen" :classpath "src_lein")
     (filter #(re-find #"^leiningen\.(?!do-args|george|serve)[^\.]+$" (name %)))
     (distinct)
     (sort)))


(defn george "List custom tasks for George."
  [& _]
  (println "\nCustom tasks for George:")
  (doseq [task-ns (george-tasks)]
    (println (lh/help-summary-for task-ns)))
  (println "\nRun `lein help $TASK` for details. (...)\n"))
