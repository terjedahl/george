;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:docs "Keeps track of \"Input\" history.  Also, persists state of editors et al."}
  george.core.history
  (:require
    [clojure.java.io :as cio]
    [clojure.edn :as edn]
    [george.util :as u]
    [george.util.file :as guf]
    [george.editor.core :as ed]
    [george.application.config :as conf])
  (:import
    [java.util Date]
    [java.sql Timestamp]
    [java.io File]))


(def history-file 
  (memoize
    #(guf/ensure-parent-dir
       (cio/file (conf/appdata-dir) "repl" "history.edn"))))


(def open-files-file
  (memoize
    #(guf/ensure-parent-dir
       (cio/file (conf/appdata-dir) "state" "open-files.edn"))))


(def NEXT 1)
(def PREV -1)


(defonce ^:private history_ (atom []))
(defonce ^:private repls-nr_ (atom 0))


(defn next-repl-nr [] 
  (swap! repls-nr_ inc))


(defn- do-load-history []
  (let [f (history-file)]
    (when (.exists f)
      (binding [*data-readers* {'inst clojure.instant/read-instant-timestamp}]
        (reset! history_ (-> f slurp  edn/read-string))))))
      

(defn- ensure-loaded-history []
  (when-not @history_
    (do-load-history)))


(defn- prune [vec max]
  "returns vector containing last 'max' of 'vec'"
  (let [
        len (count vec)
        i (if (> len max)  (- len max) 0)]
    (subvec vec i)))


(defn append-history [repl-uuid content]
  (ensure-loaded-history)
  (let [item {:repl-uuid repl-uuid
              :timestamp (Timestamp. (.getTime (Date.)))
              :content   content}]
        ;_ (println "item:" item)
    (swap! history_ #(-> % (prune 100) (conj item)))
    (future
      ;(println "writing history to file ...")
      (spit (history-file) (pr-str @history_)))))
      ;(println " ... done")


(defn do-history [code-area repl-uuid current-history-index_ direction global?]
  (ensure-loaded-history)
  (let [items-global
        (reverse @history_)

        items
        (if global?
          items-global
          (filter #(= (:repl-uuid %) repl-uuid) items-global))

        i (+ @current-history-index_ (- direction))
        i (if (< i -1) -1 i)
        i (if (> i (count items)) (count items) i)
        
        content
        (when (and (not (empty? items))
                   (not (< i 0))
                   (not (>= i (count items))))
            (:content (nth items i)))

        content
        (if content
          content
          (if (< i 0)
            ""
            (if (= (count items) (count items-global))
              ";; No more global history."
              ";; No more local history.\n;; To access global history use SHIFT-CLICK.")))]
    
    (reset! current-history-index_ i)
    (doto code-area
      (ed/set-text content))))


(defn set-open-files [paths]
  (future
    (spit (open-files-file) (pr-str {:open-files (vec paths)}))))


(defn get-open-files [] 
  (let [f ^File (open-files-file)]
    (if (.exists f)
      (-> f slurp edn/read-string :open-files)
      [])))