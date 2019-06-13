;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.application.state.george-projects
  (:require
    [clojure.java.io :as cio]
    [clojure.edn :as edn]
    [common.george.config :as c]
    [common.george.util
     [files :as f]
     [cli :refer [debug]]])
  (:import
    [java.io File]))


(def george-projects-file
  (memoize
    #(cio/file (c/data-dir) "state" "george-projects.edn")))


(defn- load-data []
  (let [f ^File (george-projects-file)]
    (if (.exists f)
      (-> f slurp edn/read-string)
      nil)))


(defn- dump-data [data]
  (future
    (spit (f/ensured-file (george-projects-file))
          (pr-str data))))


(defn get-uri []
  (:uri (load-data)))


(defn set-uri [^String uri]
  (dump-data
    (merge
      (load-data)
      {:uri uri})))


(defn get-licensed []
  (:licensed (load-data)))


(defn set-licensed [licensed]
  (dump-data (assoc (load-data) :licensed licensed)))


(defn remove-licensed []
  (dump-data (dissoc (load-data) :licensed)))


(defn- deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))


(defn get-step [id]
  (-> (load-data)
      :projects
      (get id)
      :current-step))


(defn set-step [id step-index]
  (dump-data
    (deep-merge
      (load-data)
      {:projects {id {:current-step step-index}}})))
