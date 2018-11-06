; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns george.application.config
  (:require
    [clojure.java.io :as cio]
    [george.util.file :as guf])
  (:import
    [java.io File]))


(def WINDOWS "Windows")
(def MACOS   "MacOS")
(def OTHER   "other")


(def operating-system
  "Returns 'Windows', 'MacOS' or 'other'"
  (memoize
    #(let [os (.toLowerCase (System/getProperty "os.name"))]
       (cond
         (.contains os "windows") WINDOWS
         (.contains os "mac")     MACOS
         :else OTHER))))


(defn windows? []
  (= WINDOWS (operating-system)))


(defn macos? []
  (= MACOS (operating-system)))


(defn other? []
  (= OTHER (operating-system)))


(defn ^File user-home []
  (cio/file (System/getProperty "user.home")))


(def  documents-dir 
  "Returns the ensured default directory for storing users documents: 
all platforms: $HOME/George"
  ^File #(guf/ensure-dir (cio/file (user-home) "George")))


(def file-sep
  "Returns '/' or '\\'"
  ^String #(File/separator))


(def path-sep
  "Returns ':' or maybe something else"
  ^String #(File/pathSeparator))


(def appdata-dir
  "Returns the ensured default directory for storing app-data:
Windows: $HOME/AppData/Roaming/George
MacOS:   $HOME/Library/Application Support/George
other:   $HOME/Application Support/George"
  ^File (fn 
          ([] 
           (appdata-dir (operating-system)))
          ([os]  
           (let [d
                 (condp = os
                   WINDOWS  
                   (cio/file (user-home) "AppData" "Roaming" "George")
                   MACOS    
                   (cio/file (user-home) "Library" "Application Support" "George")
                   ;; OTHER
                   (cio/file (user-home) ".george"))]
             (guf/ensure-dir d)))))
          
;(prn (appdata-dir))
;(prn (appdata-dir WINDOWS))
;(prn (appdata-dir MACOS))
;(prn (appdata-dir OTHER))


