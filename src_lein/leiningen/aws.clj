;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.aws
  (:require
    [leiningen.george.core :as g]
    [leiningen.help :as lh]))


(defn deploy
  "Deploy Site to AWS."
  []
  (g/aws-deploy))


(defn invalidate
  "Invalidate AWS CloudFront."
  []
  (g/aws-invalidate))


(defn aws
  "Execute AWS functions.                  ...

Prerequisites:
- *nix system
- AWS CLI tool installed and authenticated.
- Andante AS *

  * Currently this task is hardcoded to only work for Andante's AWS account."

  {:subtasks [ #'deploy #'invalidate]}
  [project & [subtask]]
  (binding [g/*project* project]
    (case subtask
      "deploy"      (deploy)
      "invalidate"  (invalidate)

      (lh/help project "aws"))))