
;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.files
  "java.nio.files functionality"
  (:require 
    [clojure.java.io :as cio]
    [common.george.launch.config :as c])
  (:import
    [java.nio.file Paths Path]
    [java.io File]
    [java.net URI URL]))



(defn ^Path path
  "Returns a java.nio.files.Path"
  ([arg]
   (cond
     (instance? Path arg) arg
     (instance? File arg) (.toPath arg)
     (instance? URI arg) (Paths/get arg)))
  ([arg & more]
   (Paths/get (str arg) (into-array String (map str more)))))



(defn ^URL url 
 ([arg]
  (cond
    (instance? URL arg) arg
    (instance? URI arg) (.toURL arg)
    (instance? File arg) (-> arg .toURI url)
    (instance? Path arg) (-> arg .toUri url)
    (instance? String arg) (URL. arg))))
;(prn (url (str "http://xxx/" "xy")))


(defn symlink?
  "Checks if a File is a symbolic link or points to another file."
  [^File file]
  (let [canon 
        (if-not (.getParent file)
          file
          (-> (.. file getParentFile getCanonicalFile) (File. (.getName file))))]
    (not= (.getCanonicalFile canon) (.getAbsoluteFile canon))))


(defn real-directory?
  "Returns true if this file is a real directory, false if it is a symlink or a
  normal file."
  [f]
  (if (c/windows?)
    (.isDirectory f)
    (and (.isDirectory f)
         (not (symlink? f)))))


(defn copy-file-to-dir [dir file & [verbose?]]
  (let [f0 (.getAbsoluteFile (cio/file file))
        f1 (cio/file dir (.getName f0))]
    (when verbose?
      (println (format "Copying file: %s\n          to: %s" (str f0) (str f1))))
    (cio/copy f0 f1)
    f1))


(defn copy-files-to-dir [dir files & [verbose?]]
  (mapv #(copy-file-to-dir dir % verbose?) files))


(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
  Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (cio/file f)]
    (when (real-directory? f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (.setWritable f true)
    (cio/delete-file f silently)))


(defn ^File ensure-dir [dir]
  (let [d (cio/file dir)]
    (when-not (.exists d) (.mkdirs d))
    (assert (.isDirectory dir))
    d))


(defn ^File clean-dir [dir]
  (let [d (cio/file dir)]
    (doseq [f (.listFiles d)]
      (delete-file-recursively f))
    d))


(defn copy-dir [source target]
  (assert (.isDirectory source))
  (ensure-dir target)
  (doseq [f-str (.list source)]
    (cio/copy (cio/file source f-str)
              (cio/file target f-str))))

;(defn delete
;  "Delete path p. If silent? is nil or false, raise an exception on failure, else return the value of silently."
;  [p & [silent?]]
;  (if silent? 
;    (Files/deleteIfExists (path p))) 
;    (Files/delete (path p))