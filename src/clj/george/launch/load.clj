;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.load
  (:require
    [george.javafx :as fx]
    [common.george.util.cli :refer [debug]])
  (:import
    [clojure.lang DynamicClassLoader]
    [java.net URLClassLoader URL]))


(def STRING_ARRAY_CLASS (class (make-array String 0)))


(defn- new-url-classloader [jar-url]
  ;; It is important to have a "clean" classloader - with no code from current version of application.
  ;; Therefore we use platformClassLoader as parent. (Otherwise it defaults to using the calling classes classloader,
  ;; which will be the DynamicClassLoader with already loaded code from current version.)
  ;; platformClassLoader will give us everything from the JRE/standard libraries, but nothing more.
  (URLClassLoader. (into-array URL (list jar-url)) (ClassLoader/getPlatformClassLoader)))

(defn- new-dynamic-classloader [loader]
  ;; Now we set a nice "clean" DynamicClassLoader as the contextClassLoader.
  ;; Clojure will not work without DynamicClassLoader as its ContextClassLoader.
  (DynamicClassLoader. loader))


(defn- set-context-classloader [loader]
  ;; It will propagate to all other threads.
  (.setContextClassLoader (Thread/currentThread) loader)
  loader)


(defn- set-fx-classloader [loader]
  ;; Also, we need to set it on the JavaFX thread.
  (fx/init :classloader loader)
  loader)


(defn make-and-set-classloader-for-jar
  "Creates a clean dynamic classloader for the jar,
  sets it as context-classloader and as classloader in fx-thread,
  and returns it."
  [jar-url with-javafx?]
  (-> jar-url
      new-url-classloader
      new-dynamic-classloader
      set-context-classloader
      (#(if with-javafx? (set-fx-classloader %) %))))


(defn invoke-static-main [loader class-str args]
  (let [class (Class/forName class-str true loader)
        main (-> class (.getDeclaredMethod "main" (into-array (list (class (make-array String 0))))))]
    (.invoke main nil (into-array Object (list (into-array String args))))))


(defn invoke-loadOrRun [loader stage args]
  (let [class     (Class/forName "no.andante.george.Launch" true loader)
        loadOrRun (.getDeclaredMethod class "loadOrRun"
                    (into-array (list javafx.stage.Stage STRING_ARRAY_CLASS)))]
    (.invoke loadOrRun nil
      (into-array Object (list stage (into-array String args))))))


#_(defn set-application-name [^String name]
    (let [a (Application/GetApplication)
          _ (doseq [f (seq (.getDeclaredFields Application))] (prn '- f))
          n  (.getDeclaredField ^Class Application "name")
          DN  (.getDeclaredField ^Class Application "DEFAULT_NAME")]
      (.setAccessible n true)
      (.setAccessible DN true)
      (debug 'DEFAULT_NAME (.get DN a))
      (debug 'name (.get n a))
      (debug "Setting name field ..." name)
      (.set n a name)
      (debug 'name (.get n a))))