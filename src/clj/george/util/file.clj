;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.file
  (:require
    [clojure.java.io :as cio])
  (:import
    [java.io File]
    [java.nio.file Files LinkOption Path StandardCopyOption NoSuchFileException Paths]
    [java.awt Desktop]
    [java.nio.file.attribute FileAttribute]
    [java.net URI]))


(defprotocol ToFileOrPathOrString
  (to-file   [psf] [psf more])
  (to-path   [psf] [psf more]) 
  (to-string [psf] [psf more]))


(extend-protocol ToFileOrPathOrString
  File
  (to-file   ([f]        f)
             ([f more]   (apply cio/file (cons f more))))
  (to-path   ([f]        (.toPath f))
             ([f more]   (to-path (apply cio/file (cons f more)))))
  Path
  (to-file   ([p]        (.toFile p))
             ([p more]   (apply cio/file (cons (to-file p) (map to-file more)))))
  (to-path   ([p]        p)
             ([p more]   (to-path (to-string p) more)))
  (to-string ([p]      (-> p .toAbsolutePath str))
             ([p more] (str (apply cio/file (cons (to-string p) (map to-string more))))))
  String
  (to-file   ([s]        (cio/file s))
             ([s more]   (apply cio/file (cons s more))))
  (to-path   ([s]        (Paths/get s (make-array String 0)))
             ([s more]   (Paths/get s (into-array String more))))
  (to-string ([s]        s)
             ([s more]   (to-string (to-file s more))))
  URI
  (to-path    [u]        (Paths/get u)))


(defn ->path [psf & more]
  (if (empty? more)
    (to-path psf)
    (to-path psf more)))


(defn ->file [psf & more]
  (if (empty? more)
    (to-file psf)
    (to-file psf more)))


(defn ->string [psf & more]
  (if (empty? more)
    (to-string psf)
    (to-string psf more)))


(defprotocol FileOrPath 
  "A collection of functions implemented for files - File and Path"
  (parent [fp]            "Returns the dir containing the file if it is named in the file-object, else 'nil'.") 
  (filename [fp]          "Returns the file or dir name as String.")
  (dir? [fp]              "Returns 'true' if is directory.")
  (exists? [fp]           "Returns 'true' if file or dir exists.")
  (hidden? [fp]           "Returns 'true' if the file is \"hidden\" by the OS.")
  (same? [fp fp]          "Returns 'true' if the files are equal.")
  (ensure-dir [fp]        "Creates the dir and its parent dirs if needed. Returns the created dir.")
  (ensure-parent-dir [fp] "Creates only the parent dirs if needed. Returns the file or dir.")
  (ensure-file [fp]       "Creates the file and its parent dirs if needed. Returns the created file.")
  (move [fp fp]           "Moves the file or dir from one to the other. Returns the new location if successful, else 'nil'. Throws exceptions.")
  (delete [fp]            "Deletes the file or dir if it exists. Returns 'true' if something was deleted.")
  ;(trash [fp]            "Moves the file or dir to the OS trash.  Returns ???."))  ;; TODO: Implement in Java11+ `moveToTrash(File f)`
  (open [fp]              "Reveal the file in the OS-s Finder/Explorer."))
  

(extend-protocol FileOrPath
  File
  (parent [f]            (.getParentFile f))
  (filename [f]          (.getName f))
  (dir? [f]              (.isDirectory f))
  (exists? [f]           (.exists f))
  (hidden? [f]           (.isHidden f))
  (same [f1 f2]          (zero? (.compareTo f1 f2)))
  (ensure-dir [f]        (.mkdirs f) f)
  (ensure-parent-dir [f] (.mkdirs (parent f)) f)
  (ensure-file [f]       (ensure-dir (parent f)) (.createNewFile f) f)
  (move [f0 f1]          (when (.renameTo f0 f1) f1))
  (delete [f]            (.delete f))
  (open [f]
    ;; https://stackoverflow.com/questions/12339922/opening-finder-explorer-using-java-swing
    (if (Desktop/isDesktopSupported)
      (.open (Desktop/getDesktop) f)
      (binding [*out* *err*]
        (println (format "Warning: Can't open file %s. (Desktop not supported)" (str f))))))

  Path
  (parent [p]            (.getParent p))
  (filename [p]          (-> p .getFileName str))
  (dir? [p]              (Files/isDirectory p (make-array LinkOption 0)))
  (exists? [p]           (Files/exists p (into-array [LinkOption/NOFOLLOW_LINKS])))
  (hidden? [p]           (Files/isHidden p))
  (same? [p1 p2]         (try (Files/isSameFile p1 p2) (catch NoSuchFileException _ false)))
  (ensure-dir [p]        (Files/createDirectories p (make-array FileAttribute 0)) p)
  (ensure-parent-dir [p] (Files/createDirectories (parent p) (make-array FileAttribute 0)) p)
  (ensure-file [p]       (ensure-dir (parent p)) (Files/createFile p (make-array FileAttribute 0)) p)
  (move [p0 p1]          (Files/move p0 p1 (into-array [StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE])))
  (delete [p]            (Files/deleteIfExists p))
  (open [p]              (-> p ->file open)))
