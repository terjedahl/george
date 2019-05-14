;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.core
  (:require
    [clojure.string :as cs]
    [george.javafx :as fx]
    [george.projects
     [data :as d]
     [view :as v]]
    [common.george.util
     [cli :refer [debug info warn]]
     [text :refer [pformat pprint]]]
    [george.util.singleton :as singleton])
  (:import
    [javafx.stage Stage]))


(defn hide-projects-stage []
  (when-let [stage (singleton/get ::projects-stage)]
    (when (.isShowing stage) (fx/later (.hide stage)))
    (singleton/remove ::projects-stage))
  nil)


(defn show-projects-stage []
  (fx/later
    (if-let [st ^Stage (singleton/get ::projects-stage)]
      (if (.isAlwaysOnTop st)
        (doto st (.setAlwaysOnTop false) (.toBack))
        (doto st (.setAlwaysOnTop true)))
      (singleton/get-or-create ::projects-stage #(v/new-projects-stage hide-projects-stage))))
  nil)
