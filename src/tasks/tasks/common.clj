;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tasks.common
  (:require
    [clojure.java.io :as cio])
  (:import 
    [java.io File]))



(def ^:dynamic *debug* (System/getenv "DEBUG"))
(def ^:dynamic *info* (not (System/getenv "LEIN_SILENT")))


(defn info [& args]
  (when *info* (apply println args)))

(defn warn [& args]
  (when *info*
    (binding [*out* *err*]
      (apply println args))))

(defn debug [& args]
  (when *debug*
    (apply println args)))

(defn exit [& [code]]
  (System/exit (or code 0)))


(defn ^File uberjar-file 
  "Returns the file representing the built uberjar, else nil"
  []
  (when-let  [name 
              (->> (-> "target/uberjar" cio/file .list seq)
                   (filter #(.contains % "standalone"))
                   first)]
         (cio/file "target/uberjar/" name)))
       
  