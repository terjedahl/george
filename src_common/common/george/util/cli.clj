;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.cli)


;;;; flags

;; allow debug print?
(def ^:dynamic *debug*  (System/getenv "DEBUG"))

;; allow info print?
(def ^:dynamic *info*   (not (or (System/getenv "LEIN_SILENT") (System/getenv "GEORGE_SILENT"))))


(defn err [& args]
  (binding [*out* *err*]
    (apply print args) (flush)))


(defn errln [& args]
  (binding [*out* *err*]
    (apply println args)))


(defn exit [& [code]]
  (System/exit (or code 0)))


(defn debug [& args]
  "Prints to stdout if DEBUG."
  (when *debug*
    (apply println (cons "[DEBUG]" args))))


(defn info
  "Prints to stdout if not LEIN_SILENT or GEORGE_SILENT."
  [& args]
  (when *info* (apply println (cons "[INFO]" args))))


(defn warn
  "Prints to stderr if not LEIN_SILENT or GEORGE_SILENT."
  [& args]
  (when *info*
    (apply errln (cons "[WARNING]" args))))


(defn except
  "Prints to stderr."
  [& args]
  (apply errln (cons "[EXCEPTION]" args)))



(defn error
  "Prints to stderr, THEN EXITS! with -1."
  [& args]
  (apply errln (cons "[ERROR]" args))
  (exit -1))
