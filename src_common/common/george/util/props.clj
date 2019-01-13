;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.props
  (:refer-clojure :exclude [load])
  (:require
    [clojure.walk :as cw])
  (:import
   [java.io StringReader StringWriter]
   [java.util Properties]))


(defn ^Properties str->properties [^String property-data]
  (when property-data
    (doto (Properties.) (.load (StringReader. property-data)))))


(defn properties->map [^Properties p]
  (-> (into {} p)  cw/keywordize-keys))


(defn ^Properties map->properties [m]
  (doto (Properties.) (.putAll (into {} (map (fn [[k v]] [(name k) (str v)]) m)))))


(defn ^String properties->str [^Properties p & [comment]]
  (let [writer (StringWriter.)]
    (.store p writer comment)
    (str writer)))


(defn load
  "Returns a map representing the properties, or nil.
  May throw UnknownHostException, IOException, FileNotFoundException"
  [props-url]
  (-> props-url slurp str->properties properties->map))


(defn dump
  "Writes the content of the map to a file. "
  [props-file props-map & [comment]]
  (-> props-map  map->properties (properties->str comment) (#(spit props-file %))))


