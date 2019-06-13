;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.data
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [george.application.state.george-projects :as state]
    [george.application.server.core :as gas]
    [common.george.util.cli :refer [except]])
  (:import
    [java.io IOException]))


;; If true, then only code is shown, with eval and mark + copy options
(defonce ^:private code-mode_ (atom false))


(defn code-mode
  ([b] (reset! code-mode_ b))
  ([]  @code-mode_))


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
  (or (state/get-uri)
      (when-not not-default? default-uri)))


(defn set-uri [^String uri]
  (state/set-uri uri))


(defn get-current-step-index [id]
  (state/get-step id))


(defn set-current-step-index [id step-index]
  (state/set-step id step-index))


(defn replace-base [html uri]
  (cs/replace html #"<base.+href=\"https://projects.george.andante.no/\"" (format "<base href=\"%s\"" uri)))



(defn get-license-key [short-code]
  (try
    (slurp (format "%sgumroad/key_for_code?short_code=%s" gas/*server* short-code))
    (catch IOException _ nil)))


(defn get-short-code [license-key]
  (slurp (format "%sgumroad/code_for_key?license_key=%s" gas/*server* license-key)))


(defn verify-license-key
  "Returns a map: {:valid [:valid :valid-school :expired] :name 'some name' :school 'name or nil'}
  'increment?' should only be applied for private users, and only for first-time check (i.e. when the user enters the key or gets it via code, not when we are simply doing standard check)"
  [license-key]
  (let [res (slurp (format "%sgumroad/verify_key?license_key=%s" gas/*server* license-key))]
    (try
      (read-string res)
      (catch Exception e
        (.printStackTrace e)
        (except "Could not parse received data:\n" res)))))


(defonce licensed_ (atom nil))

(defn licensed []
  @licensed_)

(defn set-licensed [licensed]
  (-> (reset! licensed_ licensed)
      state/set-licensed))


(defn remove-licensed []
  (state/remove-licensed)
  (reset! licensed_ nil))


(defn project-locked? [id]
  (if (= id "stars")
      false
      (not @licensed_)))


(defn init-licensed
  "Called from g.p.view/new-project-stage"
  []
  (let [l (state/get-licensed)]
    (when l
      (let [res (verify-license-key (:license-key l))]
        (set-licensed (assoc res :short-code (:short-code l)))))))
