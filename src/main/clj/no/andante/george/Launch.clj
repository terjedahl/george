;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns no.andante.george.Launch

  (:require
    [clojure.java.io :as cio]
    [george.launch.properties :as p]
    [george.launch.load :as l]
    [george.files :as f]
    [george.launch.config :as c])

  (:import
    [no.andante.george Run])

  (:gen-class
    :name no.andante.george.Launch
    :main true))
    

;(defn- no-gui? [args]
;  ((set args) "--no-gui"))


(defn- no-check? [args]
  ((set args) "--no-check"))


(defn- no-installed-check? [args]
  (let [args- (set args)]
    (or (args- "--no-check") (args- "--no-installed-check"))))


(defn- no-online-check? [args]
  (let [args- (set args)]
    (or (args- "--no-check") (args- "--no-online-check"))))

;;;;;;;;;;;;;

(defn- use-installed? 
  "Returns true if there is an installed version and it's timestamp is newer than this."
  []
  (let [this-app (p/this-app)
        installed-app (p/installed-app (:appid this-app))]
    (when installed-app
      (p/gt (:ts installed-app) (:ts this-app)))))


(defn- use-online?
  "Returns true if there is an online version avaiable and it's timestamp is newer than this."
  []
  (let [this-app (p/this-app)
        online-app (p/online-app (:appid this-app) (:uri this-app))]
    (when online-app
      (p/gt (:ts online-app) (:ts this-app)))))


;;;;;;;;;;;;;


(defn- this-run [args]
  (prn 'Launch/this-run args)
  (Run/main (into-array String args)))


(defn- installed-load [args]
  (prn 'Launch/installed-load args)
  (let [appid (:appid (p/this-app))
        props (p/installed-app appid)
        jar-url (f/url (f/path (c/install-dir appid) (:file props)))]
    (l/run-jar jar-url "no.andante.george.Launch" args)))


(defn- online-download-load [args]
  (prn 'Launch/online-download-load args)

  (let [{:keys [appid uri]} (p/this-app)
        {:keys [file size]} (p/online-app appid uri)
        install-d           (f/ensure-dir (c/install-dir appid))]

    ;; install JAR and app-file
    (l/transfer  (f/url (str uri file))   (cio/file install-d file)  #(println "bytes:" % "/" size))
    (l/transfer  (f/url (str uri p/PROP_N))  (cio/file install-d p/PROP_N))

    ;; start the downloaded version
    (installed-load args)))


;;;;;;;;;;;;;


(defn main [& args]
  (prn 'Launch/main args)
  (println "  I am ts:" (:ts (p/this-app)))

  (cond
    (or (no-check? args)
        (and (no-online-check? args) (no-installed-check? args)))
    (this-run args)
  
    (and (not (no-installed-check? args)) (use-installed?))
    (installed-load args)
  
    (and (not (no-online-check? args)) (use-online?))
    (online-download-load args)
  
    :else
    (this-run args)))


(defn -main [& args]
  (apply main args))