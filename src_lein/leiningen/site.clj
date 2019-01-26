;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.site
  (:require
    [clojure.pprint :refer [pprint]]
    ;; Leiningen
    [leiningen.help :as lh]
    ;; src_lein
    [leiningen.serve :as lgs]
    [leiningen.george.core :as g]
    ;; src_common
    [common.george.util.cli :refer [error]])
  (:import
    [java.net ConnectException]))


(defn- port []
  (-> g/*project* :build :site :port))


(defn- send-cmd [cmd]
  (try
    (slurp (format "http://localhost:%s/_cmd/%s" (port) cmd))
    (catch ConnectException _
      (error  "Server not running. \n  To start serving Site dir, do:  lein site start "))))
        

(defn info
  "Print server info."
  []
  (when-let [s (send-cmd "info")]
    (-> s read-string pprint)))


(defn stop
  "Stop the running server."
  []
  (send-cmd "stop"))


(defn- start
  "Start the server."
  []
  (g/assert-project)
  (lgs/serve g/*project* ":port" (port) ":dir" (str (g/asserted-site-dir))))


(defn site
  "Serve Site dir on localhost.            ...

If, on *nix, you do:
  lein site start &
You can then do:
  lein site stop  # Or simply do 'kill <PID>'
  "
  {:subtasks [#'start #'info #'stop]}
  [project & [subtask]]
  (binding [g/*project* project]
    (case subtask
      "start" (start)
      "info"  (info)
      "stop"  (stop)
      (lh/help project "site"))))
