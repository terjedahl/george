;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tasks.deploy
  (:require
    [clojure.java.io :as cio]
    [me.raynes.conch :refer [programs with-programs let-programs]]
    [me.raynes.conch.low-level :refer [proc stream-to-out]]
    [tasks
     [common :refer [info warn debug exit]]
     [build :as b]]
    [george.launch
     [properties :as p]
     [config :as conf]]
    [george.files :as f]))




(defn- assert-built! []
  (when-not (.exists (cio/file b/APP_F))
    (warn (format "Exception: No '%s' found.  Have you done 'lein george build'?" b/APP_F))
    (exit -1)))


(defn- assert-aws! []
  (with-programs [which]
    (when (empty?  (which "aws" {:throw false}))
      (warn "Exception: No command 'aws' found.  You need Amazon's aws tool installed to run this subtask.")
      (exit -1))))


(defn install-jar []
  (prn 'install-jar)
  (assert-built!)

  (let [appid (-> b/APP_F p/load :appid)
        install-d (-> appid conf/install-dir f/ensure-dir)
        source-files (seq (.listFiles (cio/file b/LAUNCH_D)))]
    (info (format "Copying files to '%s' ..." install-d))
    (f/copy-files-to-dir install-d source-files true)))


(defn- deploy-jar [& args]
  (prn 'deploy-jar args)

  (assert-built!)
  (assert-aws!)

  (let [appid (-> b/APP_F p/load :appid)]
    ;(prn p)

    (info (format "Deploying application-jar and app.properties to Amazon S3 for '%s' ..." appid))
    (.delete (cio/file b/LAUNCH_D ".DS_Store"))
    (stream-to-out
      (proc "aws" "s3" "cp" (str b/LAUNCH_D) (format "s3://download.george.andante.no/%s/launch" appid) "--acl" "public-read" "--recursive" "--region" "eu-central-1")
      :out)

    (info "Invalidating CloudFront caches ...")
    (stream-to-out
      (proc "aws" "cloudfront" "create-invalidation" "--distribution-id" "E3QSHE6V41FUEZ" "--paths" (format "/%s/launch/*" appid))
      :out)

    (info "Give it a second or two ...")
    (Thread/sleep 5000)

    (info "Verifying 'app.properties' ...")
    (println (slurp (format "https://download.george.andante.no/%s/launch/app.properties" appid)))

    (exit)))



;(defn -main 
;  "Tasks for deploying. Usage: deploy [jar|mac|win]"
;  [& [subtask & rest]]
;  (prn 'subtask subtask rest)
;  (case subtask
;    "jar" (apply deploy-jar rest)
;    "help" (println "some help here")
;
;    nil (warn "No subtask")
;    :else (warn "Unkown subtask:" subtask)))