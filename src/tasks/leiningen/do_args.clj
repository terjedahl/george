; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.do-args
  (:require
    [leiningen.do :as ld]))


(defn ^:no-project-needed ^:higher-order do-args
  "Similar to lein's 'do', but strips out any not-vector args at end, and joins them in place or ':args' at end of each vector.

This is useful to make all CLI args available to any sub-tasks in a do-statement (in stead of as args to the do task itself.)

USAGE: 
[\"do-args\" 
  [\"some-task\"]\n  [\"some-task-gets-args012\" \"arg0\" :args]\n  [\"some-task-gets-args12\" :args]\n  \"arg1\"\n  \"arg2\"]
RESULTS IN:
[\"do\"
  [\"some-task\"] \n  [\"some-task-gets-args012\" \"arg0\" \"arg1\" \"arg2\"] \n  [\"some-task-gets-args12\" \"arg1\" \"arg2\"]]\n"


  [project & args]
  ;(prn 'do-args args)
  (let [tasks  (take-while vector? args)
        args   (drop-while vector? args)
        tasks1 (map 
                 #(if (= (last %) :args) 
                      (vec (concat (drop-last %) args))
                      %)
                  tasks)]
    ;(prn 'tasks1 tasks1)
    ;(prn 'args args)
    (binding [*command-line-args* args]
      (apply ld/do (cons project tasks1)))))