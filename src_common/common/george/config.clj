;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.config
  (:refer-clojure :exclude [load])
  (:require
    [clojure.java.io :as cio]
    [common.george.util
     [cli :refer [warn]]
     [time :as t]
     [text :refer [pprint]]
     [props :as p]
     [files :as f]
     [platform :as pl]])
  (:import
    [java.io File IOException FileNotFoundException]
    [java.net UnknownHostException]))

(declare
  installed-dir)


(def PROP_NAME   "app.properties")
(def DEFAULT_APP "George-DEV")


(defn get-file  [^File f]
  (if f (f/filename f) "NA"))


(defn get-size [^File f]
  (if f (str (f/size f)) "NA"))


(defn get-checksum [^File f]
  (if f (str (f/checksum f)) "NA"))


(defn print-now []
  (println (t/now-iso)))


(defn default-uri [& [app]]
  (format "https://download.george.andante.no/apps/%s/platforms/%s/jar/" (or app DEFAULT_APP) (pl/platform)))


(defn default-props [& [app uri ts]]
  (let [app (or app DEFAULT_APP)]
    {:app app
     :version "NA"
     :ts (or ts (t/now-iso))
     :uri (or uri (default-uri app))
     :file "NA"
     :size "NA"
     :checksum "NA"}))


(defn ensured-default
  "Returns a map that has all the keys of the default map, but also the any extra keys added."
  [props]
  (conj (default-props) props))


(defn load
  "Returns a map representing the properties, or nil"
  [props-url]
  (try
    (p/load props-url)
    (catch UnknownHostException e
      (warn (format "UnknownHostException (%s). No online access?"  (.getMessage e))))
    (catch IOException e
      (warn (format "IOException: \"%s\"" (.getMessage e))))
    (catch FileNotFoundException _
      (warn "File not found:" (str props-url)))))


;(defn uri->props [uri]
;  (-> (str uri PROP_NAME) load))


(defn dump
  "Writes the content of the map to a file, ensuring default keys."
  [props-file props-map & [comment]]
  (-> props-map ensured-default (#(p/dump props-file % comment))))


(defn this-props []
  (load (cio/resource PROP_NAME)))


(defn ^String this-app []
  (:app (this-props)))


(defn ^String get-app [^File file]
  (:app (load file)))


(defn installed-props [& [app]]
  (load
    (cio/file (installed-dir (or app DEFAULT_APP)) PROP_NAME)))


(defn online-props [& [app uri]]
  (load (str (or uri (default-uri app)) PROP_NAME)))


;(def appdata-dir
;  "Returns the ensured default directory for storing app-data:
;Windows: $HOME/AppData/Roaming/<app>
;MacOS:   $HOME/Library/Application Support/<app>
;other:   $HOME/Application Support/<app>"
;  ^File (fn
;          ([]
;           (appdata-dir (operating-system)))
;          ([os]
;           (let [app (p/this-app)
;                 d
;                 (condp = os
;                   WINDOWS
;                   (cio/file (user-home) "AppData" "Roaming" app)
;                   MACOS
;                   (cio/file (user-home) "Library" "Application Support" app)
;                   ;; OTHER
;                   (cio/file (user-home) (str "." app)))]
;             (guf/ensure-dir d)))))

;(prn (appdata-dir))
;(prn (appdata-dir WINDOWS))
;(prn (appdata-dir MACOS))
;(prn (appdata-dir OTHER))


(defn- platform-dir
  "Returns the ensured default directory for installing or storing app-data:
  Windows: $HOME/AppData/<Local|Roaming>/<app>/<dir>
  MacOS:   $HOME/Library/Application Support/<app>/<dir>
  other:   $HOME/AppData/<app>/<dir>"
  [^String app ^String dir]
  (let [home (pl/user-home)]
    (f/ensured-dir
      (apply cio/file
             (cond
               (pl/windows?)
               [home "AppData" (if (= dir "installed") "Local" "Roaming") app dir]
               (pl/macos?)
               [home "Library" "Application Support" app dir]
               :else
               [home "AppData" app dir])))))


(defn ^File installed-dir
 ([]    (installed-dir (this-app)))
 ([app] (platform-dir app "installed")))


(defn ^File data-dir
 ([]    (data-dir (this-app)))
 ([app] (platform-dir app "data")))


(defn  ^File documents-dir
  "Returns the ensured default directory for storing users documents:
all platforms: $HOME/George"
  ([]    (documents-dir (this-app)))
  ([app] (f/ensured-dir (cio/file (pl/user-home) app))))


;;;; DEV


;(defn print-props [p & [file]]
;  (let [bar "############################"]
;    (println bar)
;    (when file (print "# File: ") (prn file))
;    (pprint p)
;    (println bar)))


;(defn round-trip []
;  (dump "x.props" {:a 1} "Some comment")
;  (load "x.props") "x.props")
; (round-trip)

;(pprint (uri->props (:uri (default))))


;(defn ^String file [props]
;  (when props (.getProperty props "file")))



(defn list-props [& [app uri]]
  (let [this-props (this-props)

        app (or app (:app this-props))

        installed-props (installed-props app)

        uri (or uri
                (or (:uri installed-props)
                    (:uri this-props)))

        online-props (online-props app uri)]

    (println "THIS")              (pprint this-props)
    (println "INSTALLED")         (pprint installed-props)
    (printf  "ONLINE (%s)\n" uri) (pprint online-props)))

;(list-apps "George-DEV")

