;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.files
  "java.nio.files functionality and more"
  (:require
    [clojure.java
     [io :as cio]
     [shell :refer [sh]]]
    [common.george.util
     [cli :refer [debug warn except]]
     [platform :as pl]])
  (:import
    [java.nio.file Paths Path Files LinkOption StandardCopyOption NoSuchFileException OpenOption]
    [java.io File InputStream OutputStream]
    [java.net URI URL]
    [java.awt Desktop]
    [java.nio.file.attribute FileAttribute]
    [java.util.zip Adler32]))


(defn symlink?
  "Checks if a File is a symbolic link or points to another file."
  [^File file]
  (let [canon 
        (if-not (.getParent file)
          file
          (-> file .getParentFile .getCanonicalFile (File. (.getName file))))]
          ;(-> (.. file getParentFile getCanonicalFile) (File. (.getName file))))]
    (not= (.getCanonicalFile canon) (.getAbsoluteFile canon))))


(defn real-directory?
  "Returns true if this file is a real directory, false if it is a symlink or a
  normal file."
  [f]
  (if (pl/windows?)
    (.isDirectory f)
    (and (.isDirectory f)
         (not (symlink? f)))))


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


(defn- checksum- [^Path path]
  (with-open [input (Files/newInputStream path (make-array OpenOption 0))]
    (let [checksum (Adler32.)
          buffer (byte-array 16384)]
      (loop []
        (let [size (.read input buffer)]
          (when-not (neg? size)
            (.update checksum buffer 0 size)
            (recur))))
      (.getValue checksum))))


(defn ^File ->cleaned-dir [^File dir]
  (when dir
    (doseq [f (.listFiles dir)]
      (delete-file-recursively f))
    dir))


(defprotocol ConvertPaths
  (to-file**   [ps] [ps more])
  (to-path**   [ps] [ps more])
  (to-string** [ps] [ps more])
  (to-url**    [ps] [ps more]))


(extend-protocol ConvertPaths
  File
  (to-file**
    ([f]        f)
    ([f more]   (apply cio/file (cons f more))))
  (to-path**
    ([f]        (.toPath f))
    ([f more]   (to-path** (apply cio/file (cons f more)))))
  (to-string**
    ([f]        (.getAbsolutePath f))
    ([f more]   (to-string** (apply cio/file (cons f more)))))
  (to-url**
    [f]         (-> f .toURI to-url**))

  Path
  (to-file**
    ([p]        (.toFile p))
    ([p more]   (apply cio/file (cons (to-file** p) (map to-file** more)))))
  (to-path**
    ([p]        p)
    ([p more]   (to-path** (to-string** p) more)))
  (to-string**
    ([p]        (-> p .toAbsolutePath str))
    ([p more]   (str (apply cio/file (cons (to-string** p) (map to-string** more))))))
  (to-url**
    [p]         (-> p .toUri to-url**))

  String
  (to-file**
    ([s]        (cio/file s))
    ([s more]   (apply cio/file (cons s more))))
  (to-path**
    ([s]        (Paths/get s (make-array String 0)))
    ([s more]   (Paths/get s (into-array String more))))
  (to-string**
    ([s]        s)
    ([s more]   (to-string** (to-file** s more))))
  (to-url**
    [s]         (URL. s))

  URI
  (to-path**
    [u]        (Paths/get u))
  (to-url**
    [u]        (.toURL u))

  URL
  (to-url**
    [u]        u))


(defn to-path [psf & more]
  (if (empty? more)
    (to-path** psf)
    (to-path** psf more)))


(defn to-file [psf & more]
  (if (empty? more)
    (to-file** psf)
    (to-file** psf more)))


(defn to-string [psf & more]
  (if (empty? more)
    (to-string** psf)
    (to-string** psf more)))


(defn to-url [psf]
  (to-url** psf))


(defprotocol ManipulatePaths
  "A collection of functions implemented for files - File and Path"
  (parent [fp]             "Returns the dir containing the file if it is named in the file-object, else 'nil'.")
  (filename [fp]           "Returns the file or dir name as String.")
  (size [fp]               "Returns size of files in bytes.")
  (checksum [fp]           "Returns Adler checksum of file.")
  (dir? [fp]               "Returns 'true' if is directory.")
  (exists? [fp]            "Returns 'true' if file or dir exists.")
  (hidden? [fp]            "Returns 'true' if the file is \"hidden\" by the OS.")
  (same? [fp fp]           "Returns 'true' if the files are equal.")
  (cleaned-dir [f]         "Deletes all contained files recursively if exists. Returns the dir or nil.")
  (ensured-dir [fp]        "Creates the dir and its parent dirs if needed. Returns the created dir.")
  (ensured-parent-dir [fp] "Creates only the parent dirs if needed. Returns the file or dir.")
  (ensured-file [fp]       "Creates the file and its parent dirs if needed. Returns the created file.")
  (move [fp fp]            "Moves the file or dir from one to the other. Returns the new location if successful, else 'nil'. Throws exceptions.")
  (delete [fp]             "Deletes the file or dir if it exists. Returns 'true' if something was deleted.")
  (reveal [fp]             "Reveal the file in the OS's Finder/Explorer.")
  (open [fp]               "Open the file in the OS's associated application.")
  (trash [fp]              "Moves the file or dir to the OS trash.  Returns true if move was successful."))


(extend-protocol ManipulatePaths
  File
  (parent [f]             (.getParentFile f))
  (filename [f]           (.getName f))
  (size [f]               (size (to-path f)))
  (checksum [f]           (checksum (to-path f)))
  (dir? [f]               (-> f .getCanonicalFile .isDirectory))
  (exists? [f]            (.exists f))
  (hidden? [f]            (.isHidden f))
  (same [f1 f2]           (zero? (.compareTo f1 f2)))
  (cleaned-dir [f]        (->cleaned-dir f))
  (ensured-dir [f]        (.mkdirs f) f)
  (ensured-parent-dir [f] (.mkdirs (parent f)) f)
  (ensured-file [f]       (ensured-dir (parent f)) (.createNewFile f) f)
  (move [f0 f1]           (when (.renameTo f0 f1) f1))
  (delete [f]             (.delete f))
  (reveal [f]
    ;; (.browseFileDirectory (Desktop/getDesktop) f) doesn't work properly (at least on MacOS), so we role our own.
    (let [{:keys [err]} (cond
                          (pl/macos?)    (sh "open" "--reveal" (to-string f))
                          (pl/windows?)  (sh "explorer.exe" "/select," (to-string f))
                          (pl/linux?)    (sh "xdg-open" (to-string (parent f))) ;; Opens parent dir, but doesn't mark file.
                          :else          {:err (format "Reveal not implemented for platform '%s'" (pl/platform))})]
      (if (empty? err) true (warn err))))
  (open [f]
    (if (Desktop/isDesktopSupported)
      (do (.open (Desktop/getDesktop) f) true)
      (warn (format "Can't open file '%s'. (Desktop not supported)" f))))
  (trash [f]
    (if (Desktop/isDesktopSupported)
      (.moveToTrash (Desktop/getDesktop) f)
      (warn (format "Can't move file '%s' to trash. (Desktop not supported)" f))))

  Path
  (parent [p]             (.getParent p))
  (filename [p]           (-> p .getFileName str))
  (size [p]               (Files/size p))
  (checksum [p]           (checksum- p))
  (dir? [p]               (Files/isDirectory p (make-array LinkOption 0)))
  (exists? [p]            (Files/exists p (into-array (list LinkOption/NOFOLLOW_LINKS))))
  (hidden? [p]            (Files/isHidden p))
  (same? [p1 p2]          (try (Files/isSameFile p1 p2) (catch NoSuchFileException _ false)))
  (cleaned-dir [p]        (->cleaned-dir (to-file p)))
  (ensured-dir [p]        (Files/createDirectories p (make-array FileAttribute 0)) p)
  (ensured-parent-dir [p] (Files/createDirectories (parent p) (make-array FileAttribute 0)) p)
  (ensured-file [p]       (ensured-dir (parent p)) (Files/createFile p (make-array FileAttribute 0)) p)
  (move [p0 p1]           (Files/move p0 p1 (into-array (list StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE))))
  (delete [p]             (Files/deleteIfExists p))
  (reveal [p]             (-> p to-file reveal))
  (open [p]               (-> p to-file open))
  (trash [p]              (-> p to-file trash)))


(defn copy-file-to-dir [dir file & [verbose?]]
  (let [f0 (.getAbsoluteFile (cio/file file))
        f1 (cio/file dir (.getName f0))]
    (when verbose?
      (println (format "Copying file: %s\n          to: %s" (str f0) (str f1))))
    (cio/copy f0 f1)
    f1))


(defn copy-files-to-dir [dir files & [verbose?]]
  (mapv #(copy-file-to-dir dir % verbose?) files))


(defn copy-dir [source target]
  (assert (.isDirectory source))
  (ensured-dir target)
  (doseq [f-str (.list source)]
    (let [source-f (cio/file source f-str)
          target-f (cio/file target f-str)]
      (if (.isDirectory source-f)
        (copy-dir source-f target-f)
        (cio/copy source-f target-f)))))



;(defn delete
;  "Delete path p. If silent? is nil or false, raise an exception on failure, else return the value of silently."
;  [p & [silent?]]
;  (if silent?
;    (Files/deleteIfExists (path p)))
;    (Files/delete (path p))


(defn download
  "Optional total-fn takes 1 args: An long indicating the total number of bytes read/written."
  [^URL source ^File target & [total-fn]]
  (debug "c.g.u.files/download ...")
  (debug "  source:  " (str source))
  (debug "  target:  " (str target))
  (debug "  total-fn:" (boolean total-fn))
  (let [buffer (byte-array 65536)]
    (with-open [^InputStream input (.getInputStream (-> source .openConnection))
                ^OutputStream output (Files/newOutputStream (.toPath target) (make-array OpenOption 0))]
      (loop [total-bytes 0]
        (let [size (.read input buffer)]
          (when-not (neg? size)
            (.write output buffer 0 size)
            (when total-fn
              (total-fn (+ total-bytes size)))
            (recur (+ total-bytes size))))))))



