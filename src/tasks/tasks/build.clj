; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns tasks.build
  (:require
    [clojure.java.io :as cio]
    [tasks.common :refer [info warn debug exit]]
    [george.launch
     [config :as c]
     [properties :as p]]
    [george.files :as f]
    [george.util.text :refer [pprint pformat]])
  (:import (java.io File)))


(def RSC_D (cio/file "src/main/rsc"))
(def LAUNCH_D (cio/file "target/launch"))

(def EMBED_F (cio/file RSC_D p/PROP_N))
(def APP_F (cio/file LAUNCH_D p/PROP_N))


(defn embed-properties [& _]
  (prn 'embed-properties *command-line-args*)
  (let [[version appid uri ts] (map #(when (not= % "nil") %) *command-line-args*)
        props (assoc (p/default appid uri ts) :version version)]
    (p/dump  EMBED_F props)
    (info (pformat props))))


(def JAR_D (cio/file "target/uberjar"))


(defn ^File uberjar-file
  "Returns the file representing the built uberjar, else nil"
  []
  (when-let  [name
              (->> (-> JAR_D cio/file .list seq)
                   (filter #(.contains % "standalone"))
                   first)]
    (cio/file JAR_D name)))



(defn post-uberjar []
  (prn 'post-uberjar)
  (let [d (-> LAUNCH_D (f/ensure-dir) (f/clean-dir))
        j0 (uberjar-file)
        p0 (p/load EMBED_F)
        ;; copy the jar and output a complete app.properties
        j1 (f/copy-file-to-dir d j0)
        p1 (assoc p0 
                  :file (p/get-file j1) 
                  :size (p/get-size j1)
                  :checksum (p/get-checksum j1))]
    (info (pformat p1))
    (p/dump APP_F p1)))



;(defn -main
;  "Tasks for building. Usage: build [|jar|jre|mac|win]"
;  [& [subtask & rest]]
;  (prn 'subtask subtask rest)
;  (case subtask
;    "jar" (apply build-jar rest)
;    "help" (println "some help here")
;
;    nil (warn "No subtask")
;    :else (warn "Unkown subtask:" subtask)))