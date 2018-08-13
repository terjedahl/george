; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.launch.config
  (:require 
    [clojure.java.io :as cio])
  (:import
    [java.io File]))


(def WINDOWS "Windows")
(def MACOS "MacOS")
(def OTHER "other")


(defn operating-system
  "Returns 'Windows', 'MacOS' or 'other'"
  []
  (let [os (.toLowerCase (System/getProperty "os.name"))]
    (cond
      (.contains os "windows") WINDOWS
      (.contains os "mac")     MACOS
      :else OTHER)))


(defn windows? []
  (= WINDOWS (operating-system)))

(defn macos? []
  (= MACOS (operating-system)))


(defn user-home []
  (System/getProperty "user.home"))


(defn- the-dir [appid dir-typ]
  (let [h (user-home)]
    (apply cio/file
           (cond
             (windows?)
             [h "AppData" (if (= dir-typ "installed") "Local" "Roaming") appid dir-typ]
             (macos?)    
             [h "Library" "Application Support" appid dir-typ]
             :else
             [h "AppData" appid dir-typ]))))


(defn ^File install-dir [appid]
  (the-dir appid "installed"))


(defn ^File data-dir [appid]
  (the-dir appid "data"))
