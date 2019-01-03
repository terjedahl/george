; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.serve
  (:require
    [leiningen.george :as g]
    [leiningen.server :refer [server]]
    [clojure.java.io :as cio]))



(defn- port []
  (or (-> g/*project* :server :port) 9999))


(defn- cmd [cmd]
  (try
    (slurp (format "http://localhost:%s/_cmd/%s" (port) cmd))
    (catch java.net.ConnectException _
      (binding [*out* *err*]
        (println "Server not running.  Do 'lein serve' to start serving deployable.")))))
        

(defn- maybe-println [s]
  (when s (println s)))


(defn- status 
  "Prints server status info"
  []
  (maybe-println (cmd "status")))
 
(defn- url
  "Prints the url for the running server"
  []
  (maybe-println (cmd "url")))


(defn- stop 
  "Stops the running server"
  []
  (cmd "stop"))



(defn- dir []
  "target/serve")


(defn- serve- []
  (g/assert-deployable)
  (g/assert-project)
  (let [d (dir)]
    (g/clean d)
    (g/ensure-dir d)
    (g/copy-files-to-dir d (-> "target/deployable" cio/file .listFiles seq) false)
    (apply server [g/*project* "--port" (port) "--dir" (str "./" d)])))


(defn serve
  "Serve deployable via http from dedicated dir
  
Copies jar + app-file to 'target/serve' and launches a web-server on project.clj -> :server -> :port ."
  {:subtasks [#'status #'stop #'url]}
  [project & [subtask & args- :as args]]
  (binding [g/*project* project]
    (case subtask
      "status" (status)
      "stop"   (stop)
      "url"    (url)
      (serve-))))