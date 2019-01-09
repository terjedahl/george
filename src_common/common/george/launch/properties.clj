;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.launch.properties
  (:refer-clojure :exclude [load])
  (:require
    [clojure.java.io :as cio]
    [clojure.walk :as cw]
    [common.george.launch.config :as c]
    [common.george.launch.utils :as u]
    [common.george.util.text :refer [pprint]])
  (:import
    [java.io File StringReader IOException StringWriter FileNotFoundException]
    [java.util Properties]
    [java.net UnknownHostException]
    [java.nio.file Files]))


(def PROP_N     "app.properties")
(def DEFAULT_APP "George-DEV")


(defn get-file  [^File f]
  (if f (.getName f) "NA"))


(defn get-size [^File f]
  (if f (str (Files/size (.toPath f))) "NA"))


(defn get-checksum [^File f]
  (if f (str (u/checksum (.toPath f))) "NA"))


(defn now-ts [& [offset-millis]]
  (str (.truncatedTo (.plusMillis (java.time.Instant/now) (or offset-millis 0)) java.time.temporal.ChronoUnit/SECONDS)))

(defn print-now []
  (println (now-ts)))

(defn default-uri [& [app]]
  (format "https://dowload.george.andante.no/apps/%s/platforms/%s/jar/" (or app DEFAULT_APP) (c/platform)))


(defn default [& [app uri ts]]
  (let [app (or app DEFAULT_APP)]
    {:app app
     :version "NA"
     :ts (or ts (now-ts))
     :uri (or uri (default-uri app))
     :file "NA"
     :size "NA"   
     :checksum "NA"}))


(defn ensure-default
  "Returns a map that has all the keys of the default map, but also the any extra keys added."
  [props]
  (conj (default) props))


(defn str->properties [^String property-data]
  (when property-data
    (doto (Properties.)
      (.load (StringReader. property-data)))))


(defn properties->map [^Properties p]
  (-> (into {} p)  cw/keywordize-keys))


(defn ^Properties map->properties  [m]
  (doto (Properties.) 
    (.putAll 
      (into {} 
            (map (fn [[k v]] [(name k) (str v)]) m)))))


(defn properties->str [^Properties p & [comment]]
  (let [writer (StringWriter.)]
    (.store p writer comment)
    (str writer)))


(defn load
  "Returns a map representing the properties, or nil"
  [props-url]
  (try
    (-> props-url slurp str->properties properties->map)
    (catch UnknownHostException e
      (println (format "Warning: UnknownHostException (%s). No online access?"  (.getMessage e))))
    (catch IOException e
      (println (format "Warning: IOException: \"%s\"" (.getMessage e))))
    (catch FileNotFoundException e
      (println "Warning: File not found:" (str props-url)))))


(defn uri->props [uri]
  (-> (str uri PROP_N) load))


(defn dump
  "Writes the content of the map to a file. " 
  [props-file props-map & [comment]]
  (-> props-map ensure-default map->properties (properties->str comment) (#(spit props-file %))))


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


(defn ^String file [props]
  (when props (.getProperty props "file")))


(defn this-props []
  (load (cio/resource PROP_N)))


(defn ^String this-app []
  (:app (this-props)))


(defn get-props [^File file]
  (load file))

(defn ^String get-app [^File file]
  (:app (get-props file)))


(defn installed-props [& [app]]
  (load 
    (cio/file 
      (c/install-dir (or app DEFAULT_APP))
      PROP_N)))


(defn online-props [& [app uri]]
  (load (str (or uri (default-uri app))
             PROP_N))) 
      

(defn list-props [& [app uri]]
  (let [this-props (this-props)

        app (or app (:app this-props))

        installed-props (installed-props app)

        uri (or uri 
                (or (:uri installed-props) 
                    (:uri this-props)))

        online-props (online-props app uri)]

    (println "THIS")             (pprint this-props)
    (println "INSTALLED")        (pprint installed-props)
    (printf "ONLINE (%s)\n" uri) (pprint online-props)))

;(list-apps "George-DEV")



(defn gt
  "Is 'a' greater than 'b'. Similar to '>', but also works for other types, including strings and keywords (compare alphabetically).
  if 'silent?' is truthy, then 'nil' will be returned if 'a' or 'b' are nil, else NullPointerException is thrown."
  [a b & [silent?]]
  (if (or (nil? a) (nil? b))
    (when-not silent? 
      (throw (NullPointerException. (format "(gt %s %s)" (pr-str a) (pr-str b)))))
    (< 0 (compare a b))))
;(println (gt "a" "b"))
;(println (gt "b" "a"))
;(println (gt "a" nil true)) ;; returns nil
;(println (gt "a" nil)) ;; throws exception


