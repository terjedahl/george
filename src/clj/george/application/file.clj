;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Misc file handling utilities and UI elements, for Editors and Files."}
  george.application.file
  (:require
    [clojure.java.io :as cio]
    [george.javafx :as fx]
    [george.application.core :as core]
    [common.george.config :as c]
    [common.george.launch.props :as p]
    [common.george.util
     [cli :refer [except]]
     [files :as f]])
  (:import
    [java.io File]))


(def missing-thing
  {:folder ["Directory missing!" "previous folder location"]
   :swap-dir ["File creation failed!" "file's directory"]
   :swap-file ["Save failed!" "swap file"]})


(defn show-something-missing-alert [what]
  (let [[title thing] (missing-thing what)]
    (fx/alert
      :title title
      :header (format "The %s has gone missing!" thing)
      :text "Don't know what to do about that, exactly. :-( \nCan you fix it?"
      :owner (core/get-application-stage)
      :type :error)))


(defonce clj-filechooser
         (doto (apply fx/filechooser (fx/filechooser-filters-clj))
           (.setInitialDirectory (c/documents-dir))))


(defn select-file-for-open
  "Returns (an existing) selected file or nil"
  []
  (let [fc (doto clj-filechooser (.setTitle "Select a file ..."))
        owner (core/get-application-stage)]
    (when-let [^File f
               (try (.showOpenDialog fc owner)
                    (catch IllegalArgumentException _
                           (show-something-missing-alert :folder)
                           (.setInitialDirectory fc (c/documents-dir))
                           (select-file-for-open)))]
      ;; leave the filechooser in a useful location
      (.setInitialDirectory clj-filechooser (.getParentFile f))
      ;; then return the selected file
      f)))


(defn create-file-for-save
  "Returns a new created file or nil"
  []
  (let [fc (doto clj-filechooser (.setTitle "Save file as ..."))
        owner (core/get-application-stage)]
    (when-let [^File f
               (try (.showSaveDialog fc owner)
                    (catch IllegalArgumentException _
                      (show-something-missing-alert :folder)
                      (.setInitialDirectory fc (c/documents-dir ))
                      (create-file-for-save)))]

      (.setInitialDirectory clj-filechooser (.getParentFile f))
      f)))


(def ^:const swap-re #"#.+#")

(defn swap?
  "Returns true if this is a swap-file (i.e. #filename#)"
  [^String n]
  (boolean (re-matches swap-re n)))

;(println (swap? "#xxx#"))
;(println (swap? "##"))
;(println (swap? "#xxx"))
;(println (swap? "#xxx#x"))


(defn swap-wrap
  "Returns the passed-in name as a swap-file name (a la emacs), but only if it isn't already so."
  [^String n]
  (if-not (swap? n)
    (format "#%s#" n)
    n))

;(println (swap-wrap "xxx"))
;(println (swap-wrap "#xxx"))
;(println (swap-wrap "#xxx#"))


(defn parent-dir-exists-or-alert-print [^File d alert?]
  (if (.exists d)
      true
      (fx/now
        (when alert?
          (show-something-missing-alert :swap-dir))
        (except "Directory missing: " (str d))
        false)))


(defn create-swap
  "Creates a swap-file in same location as f, and returns it.
  If parent-dir doesn't exist, then it returns nil."
  [f alert?]
  (let [d (f/parent f)
        n (.getName f)
        f (cio/file d (swap-wrap n))]
    (when (parent-dir-exists-or-alert-print d alert?)
      (f/ensured-file f))))


(defn swap-file-exists-or-alert-print [^File swapf alert?]
  (if (and swapf (.exists swapf))
    true
    (fx/now
      (when alert?
        (show-something-missing-alert :swap-file))
      (except "Swap file missing: " (str swapf))
      false)))
