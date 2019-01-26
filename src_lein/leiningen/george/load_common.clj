;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george.load-common
  "Loads all namespaces in src_common.  Simply 'require' this namespace before 'require'-ing anything in common.george.*"
  (:require
    [cemerick.pomegranate :as pom]))

(defn add-src_common []
  (when (System/getenv "DEBUG") "src_common added")
  (pom/add-classpath "src_common"))

(add-src_common)
