;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.load
  (:require
    [clojure.java.io :as cio])
  (:import
    [java.net URLClassLoader URL URI]
    [java.io File InputStream OutputStream]
    [clojure.lang DynamicClassLoader]
    [java.nio.file Files OpenOption]))



;(defn ^URL get-uberjar-url []
;  (let [jar-file (first (filter #(.contains % "standalone") (seq (.list (cio/file "target/uberjar")))))
;        jar-path (str "target/uberjar/" jar-file)]
;    (-> jar-path cio/file .toURI .toURL)))

;(defn ^File uberjar-url []
;  (require '[task.common :refer  [uberjar-file]])
;  (when-let [jar-file (uberjar-file)]
;    (-> jar-file cio/file .toURI .toURL)))



(defn transfer
  "Optional total-fn takes 1 args: An long indicating the total number of bytes read/written."
  [^URL source ^File target & [total-fn]]
  (prn 'tranfer)
  (prn 'source source)
  (prn 'target target)
  (prn 'total-fn total-fn)
  (let [buffer (make-array Byte/TYPE 65536)]
    (with-open [^InputStream input (.getInputStream (-> source .openConnection))
                ^OutputStream output (Files/newOutputStream (.toPath target) (make-array OpenOption 0))]
      (loop [total-bytes 0]
        (let [size (.read input buffer)]
          (when (pos? size)
            (.write output buffer 0 size)
            (when total-fn
              (total-fn (+ total-bytes size)))
            (recur (+ total-bytes size))))))))

;(transfer (get-uberjar-url) (cio/file "transfered.jar"))
;(transfer (get-uberjar-url) (cio/file "transfered.jar") #(println %))
;(transfer (get-uberjar-url) (cio/file "transfered.jar") #(println (/ % (double 14452523))))
;(transfer (get-uberjar-url) (cio/file "transfered.jar") #(do (dotimes [i (/ (* 100 %) (double 14452523))] (print "#")) (println "" %)))


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
    (try (javafx.application.Platform/startup #(do)) (catch Throwable _))
    (javafx.application.Platform/runLater #(.setContextClassLoader (Thread/currentThread) dynamic-loader))
    ;; We might as well handle this here
    (javafx.application.Platform/setImplicitExit false)

    (let [cl (Class/forName class-str true loader)
          main (-> cl (.getDeclaredMethod "main" (into-array [(class (make-array String 0))])))]
      (.invoke main nil (into-array Object [(into-array String args)])))))


;(run-jar (get-uberjar-url) "no.andante.george.Main" ["arg1" "arg2 arg3"])
;(run-jar (get-uberjar-url) "no.andante.george.Main" ["arg1" "arg2 arg3"])



                                          
