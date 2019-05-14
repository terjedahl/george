;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.data
  (:require
    [clojure.string :as cs]
    [george.application.history :as gah]))


;; If true, then only code is shown, with eval and mark + copy options
(defonce ^:private code-mode_ (atom false))


(defn code-mode
  ([b] (reset! code-mode_ b))
  ([]  @code-mode_))


(defn strip-trailing-slash [^String uri]
  (if (.endsWith uri "/") (subs uri 0 (dec (count uri))) uri))


(defn load-uri-vec [v]
  (->> v (interpose "/") (apply str) slurp))


(defn read-uri-vec [v]
  (read-string (load-uri-vec v)))


(defn read-final [^String uri ^String id]
  (read-uri-vec [uri id "final.clj"]))


(defn read-steps
 ([^String uri ^String id]
  (read-steps (str uri "/" id)))
 ([^String project-uri]
  (read-uri-vec [project-uri "steps.edn"])))


(defn read-description [^String uri ^String id]
  (read-uri-vec [uri id "description.edn"]))


(defn read-index [^String uri]
  (read-uri-vec [uri "index.edn"]))


(defn read-index-data [^String uri]
  (read-uri-vec [uri "index-data.edn"]))


(defn load-welcome [^String uri]
  (load-uri-vec [uri "welcome.html"]))


;(def default-uri "http://localhost:50000")
(def default-uri "https://projects.george.andante.no")


(defn get-uri [& [not-default?]]
  (or (gah/get-george-projects-uri)
      (when-not not-default? default-uri)))


(defn set-uri [^String uri]
  (gah/set-george-projects-uri uri))


(defn get-current-step-index [id]
  (gah/get-george-projects-step id))


(defn set-current-step-index [id step-index]
  (gah/set-george-projects-step id step-index))


(defn replace-base [html uri]
  (cs/replace html #"<base.+href=\"https://projects.george.andante.no/\"" (format "<base href=\"%s\"" uri)))
