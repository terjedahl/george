;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.load
  (:require [george.javafx :as fx])
  (:import
    [clojure.lang DynamicClassLoader]
    [java.net URLClassLoader URL]))


(defn run-jar
  "Take a jar-url, fully qualified class, and a list of 0 or more args, 
  creates clean classloaders, loads the jar, loads the class, and calls the 'main' function with the args."
  [jar-url class-str args]
  (let [
        ;; It is important to have a "clean" classloader - with no code from current version of application.
        ;; Therefore we use platformClassLoader as parent. (Otherwise it defaults to using the calling classes classloader, 
        ;; which will be the DynamicClassLoader with already loaded code from current version.)
        ;; That will give us everything from the JRE/standard libraries, but nothing more.
        loader (URLClassLoader. (into-array URL [jar-url]) (ClassLoader/getPlatformClassLoader))
        ;; Now we set a nice "clean" DynamicClassLoader as the contextClassLoader.
        ;; Clojure will not work without DynamicClassLoader as its ContextClassLoader.
        dynamic-loader (DynamicClassLoader. loader)]
    ;; It will propagate to all other threads.
    (.setContextClassLoader (Thread/currentThread) dynamic-loader)

    ;; Also, we need to set it on the JavaFX thread.
    (fx/init :classloader dynamic-loader)

    (let [cl (Class/forName class-str true loader)
          main (-> cl (.getDeclaredMethod "main" (into-array [(class (make-array String 0))])))]
      (.invoke main nil (into-array Object [(into-array String args)])))))
