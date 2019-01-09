;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.online
  (:require
    [leiningen.george.core :as g]
    [leiningen.help :as lh]))


(defn aws
  "Deploys all artifacts to AWS.  ...

  Prerequisites:
   - *nix system
   - AWS CLI tool installed and authenticated.
   - Andante AS *

  * Currently this task is hardcoded to only work for Andante's AWS account.
  "
  [& [cmd]]
  (case cmd
    ":invalidate" (g/push-aws-invalidate)
    (g/push-aws)))



;(defn scp [user-AT-host-COL-dir]
;      (g/push-scp user-AT-host-COL-dir))


(defn online
  "Deploy artifacts.                       ..."
  {:subtasks [ #'aws]} ;#'scp
  [project & [subtask & args]]
  (binding [g/*project* project]
    (case subtask
      "aws"   (apply aws args)
      ;"scp"   (apply scp args)
      (lh/help project "online"))))