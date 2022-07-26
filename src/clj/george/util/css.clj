;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.css
  (:require [clojure.string :as cs]))



(defn stylable->style-map
  "Returns a map of styles currently set on the node"
  [stylable]
  (->> (cs/split  (.getStyle stylable) #";")
       (map #(let [[k v] (cs/split % #":")] [(cs/trim k) (cs/trim v)]))
       (into {})))


(defn style-map->str
  [m]
  (cs/join \newline
    (map (fn [[k v]] (format "%s: %s;" k v))
         m)))


(defn set-style-map [stylable style-map]
  (doto stylable
    (.setStyle  (style-map->str style-map))))