;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.platform
  (:import
    [java.io File]))


(def WINDOWS "Windows")
(def MACOS "MacOS")
(def OTHER "other")


(def windows?
  (memoize #(-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"windows"))  boolean)))


(def macos?
  (memoize #(-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"mac"))  boolean)))


(def shortcut-key
  (memoize #(if (macos?) "CMD" "CTRL")))


(def platform
  "Returns 'Windows', 'MacOS' or 'Other'"
  (memoize
    #(let [os (.toLowerCase (System/getProperty "os.name"))]
       (cond
         (.contains os "windows") WINDOWS
         (.contains os "mac")     MACOS
         :else                    OTHER))))


(defn platforms []
  [WINDOWS MACOS OTHER])


(defn ^File user-home []
  (System/getProperty "user.home"))


(def file-sep
  "Returns '/' or '\\'"
  ^String #(File/separator))


(def path-sep
  "Returns ':' or maybe something else"
  ^String #(File/pathSeparator))
