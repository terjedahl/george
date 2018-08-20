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
    (warn (format "Exception: No '%s' found.  Have you done 'lein build-jar'?" b/APP_F))
    (exit -1)))


(defn- assert-aws! []
  (with-programs [which]
    (when (empty?  (which "aws" {:throw false}))
      (warn "Exception: No command 'aws' found.  You need Amazon's aws tool installed to run this subtask.")
      (exit -1))))


(def SERVE_D (cio/file "target/serve"))


(defn install-jar []
  (prn 'tasks.deploy/install-jar)
  (assert-built!)

  (let [appid (-> b/APP_F p/load :appid)
        install-d (-> appid conf/install-dir f/ensure-dir)
        source-files (seq (.listFiles (cio/file b/LAUNCH_D)))]
    (info (format "Copying files to '%s' ..." install-d))
    (f/copy-files-to-dir install-d source-files true)))


(defn serve-jar []
  (prn 'tasks.deploy/serve-jar)
  (assert-built!)
  (let [
        serve-d (-> SERVE_D (f/ensure-dir) (f/clean-dir))
        source-files (seq (.listFiles (cio/file b/LAUNCH_D)))]
    (info (format "Copying files to '%s' ..." serve-d))
    (f/copy-files-to-dir serve-d source-files true)))
  

(defn- aws-jar [& args]
  (prn 'tasks.deploy/aws-jar args)

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
