;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george.middleware
  (:require
    [leiningen.george.core :as g]
    [common.george.util.cli :refer [debug]]))


(defn inject-javafx-modules [project]
  (debug "leiningen.george.middleware/inject-javafx-modules")
  (binding [g/*project* project]
    (g/inject-javafx-modules)))