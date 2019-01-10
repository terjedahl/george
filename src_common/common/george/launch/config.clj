;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.launch.config
  (:require 
    [clojure.java.io :as cio])
  (:import
    [java.io File]))


(def WINDOWS "Windows")
(def MACOS "MacOS")
(def OTHER "other")


(defn windows? []
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"windows"))  boolean))


(defn macos? []
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"mac"))  boolean))


(defn platform
  "Returns 'Windows', 'MacOS' or 'Other'"
  []
  (let [os (.toLowerCase (System/getProperty "os.name"))]
    (cond
      (.contains os "windows") WINDOWS
      (.contains os "mac")     MACOS
      :else OTHER)))


(defn platforms []
  [WINDOWS MACOS OTHER])


(defn user-home []
  (System/getProperty "user.home"))


(defn- the-dir [app dir-typ]
  (let [h (user-home)]
    (apply cio/file
           (cond
             (windows?)
             [h "AppData" (if (= dir-typ "installed") "Local" "Roaming") app dir-typ]
             (macos?)    
             [h "Library" "Application Support" app dir-typ]
             :else
             [h "AppData" app dir-typ]))))


(defn ^File install-dir [app]
  (the-dir app "installed"))


(defn ^File data-dir [app]
  (the-dir app "data"))
