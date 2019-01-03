;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

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


(def RSC_D (cio/file "src/rsc"))
(def DEPLYABLE_D (cio/file "target/deployable"))


(def EMBED_F (cio/file RSC_D p/PROP_N))
(def APP_F (cio/file DEPLYABLE_D p/PROP_N))


(defn embed* [version & {:keys [appid uri ts] :as kvargs}]
  (prn 'embed* version kvargs)
  (let [props (assoc (p/default appid uri ts) :version version)]
    (p/dump  EMBED_F props)
    (info (pformat props))))


(defn embed [version & args]
  (prn 'embed version args)
  (let [kv (map (fn [[k v]] [(keyword (subs k 1)) v])  (partition 2 args))]
    (apply embed* (cons version (flatten kv)))))


(def JAR_D (cio/file "target/uberjar"))


(defn ^File uberjar-file
  "Returns the file representing the built uberjar, else nil"
  []
  (when-let  [name
              (->> (-> JAR_D cio/file .list seq)
                   (filter #(.contains % "standalone"))
                   first)]
    (cio/file JAR_D name)))



(defn deployable []
  (prn 'deployable)
  (let [d (-> DEPLYABLE_D (f/ensure-dir) (f/clean-dir))
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

