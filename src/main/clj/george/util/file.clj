;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.file
  (:require
    [clojure.java.io :as cio])
  (:import
    [java.io File]
    [java.nio.file Files LinkOption Path]))


(defprotocol FileOrPath
  (parent [p]))

(extend-protocol FileOrPath
  File
  (parent [f] (.getParentFile f))
  Path
  (parent [p] (.getParent p)))


(defn ^Boolean exists? [^Path path]
  (Files/exists path (into-array [LinkOption/NOFOLLOW_LINKS])))


(defn ^Boolean hidden? [^Path path]
  (Files/isHidden path))


(defn ^Boolean visible? [^Path path]
  (not (hidden? path)))


(defn ^String filename [^Path path]
  (str (.getFileName path)))



(defn ^File parent-dir
  "Returns the dir containing the file if it is named in the file-object, else nil"
  [^File f]
  (.getParentFile f))


(defn ^Boolean create-dir
  "Returns true if any parent dirs were created."
  [^File f]
  (-> f .getParentFile .mkdirs))


(defn ^Boolean create-file
  "Returns true if the file was created"
  [^File f]
  (.createNewFile f))


(defn ^Boolean create-parent-dir
  "Returns true if any parent dirs were created"
  [^File f]
  (-> f .getParentFile .mkdirs))


(defn ^Boolean create-dir
  "Returns true if dir or parent dirs were created"
  [^File dir]
  (.mkdirs dir))


(defn ^File ensure-parent-dir
  "Returns the file, creating any parent dirs if necessary."
  [^File f]
  (doto f (create-parent-dir)))


(defn ^File ensure-dir
  "Returns the dir, creating it and any parent dirs if necessary."
  [^File dir]
  (doto dir  (create-dir)))


(defn ^File ensure-file
  "Returns the file, creating any parent dirs and the file itself if necessary."
  [^File f]
  (doto f 
    (ensure-parent-dir) 
    (create-file)))


(defn ^Path ->path [^File f]
  (.toPath f))


(defn ^File ->file [^Path p]
  (.toFile p))


(defn delete-file
  "Deletes file if it exists. Returns true if a file was deleted."
  [^File f]
  (-> f .toPath Files/deleteIfExists))



