;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns no.andante.george.Run
  
  (:require
    [george.javafx.java :as fxj]
    [george.application.launcher :as launcher]
    [george.launch.properties :as p])
  
  (:import
    [javafx.application Preloader$ProgressNotification])

  (:gen-class
    :name no.andante.george.Run
    :extends javafx.application.Application
    :implements [no.andante.george.IStageSharing]
    :main true))


(def state_ (atom {}))


(defn -handover
  "Implements interface IStageSharing (via gen-class).
  Is called by the preloader.
  The preloader hands over a stage which is then passed on to launcher/start,
  and where it is morphed into the main application window."
  [this stage]
  ;(println "/-handover")
  (swap! state_ assoc :handover-done? true)
  (fxj/thread (launcher/start stage (:root @state_))))


(defn -init [this]
  ;(println "no.andante.george.Main/-init")
  (fxj/thread
    (dotimes [i 50]
      (.notifyPreloader this (Preloader$ProgressNotification. (+ 0.0 (* 0.02 i))));
      (Thread/sleep 50))
    (.notifyPreloader this (Preloader$ProgressNotification. 1.0)));

  (let [root (launcher/application-root)]
    (swap! state_ assoc :root root)))


(defn -start [this ^javafx.stage.Stage stage]
  (println "no.andante.george.Main/-start args:" (-> this .getParameters .getRaw seq))
  ;(println "  ## @state_:" @state_)

  (when-not (:handover-done? @state_)
    (swap! state_ assoc :root (launcher/application-root))
    (-handover this (launcher/starting-stage stage))))


(defn -stop [this])
  ;(println "no.andante.george.Main/-stop"))


(defn- no-gui? [args]
  ((set args) "--no-gui"))


(defn main [& args]
  (prn 'Run/main args)
  (println "  I am ts:" (:ts (p/this-app)))
  (if (no-gui? args)
    (println "No GUI.")
    ;; DON'T IMPORT! IT WILL BREAK.
    (javafx.application.Application/launch  no.andante.george.Run (into-array String args))))


(defn -main
  [& args]
  (apply main args))