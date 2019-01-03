;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george
  (:require
    [leiningen.core.eval :refer [sh]]
    [clojure.java.io :as cio]
    [clojure.pprint :refer [pprint]]
    [leiningen.clean :as lc]
    [leiningen.run :as lr]
    [leiningen.help :as help]
    [bultitude.core :as blt]
    [leiningen.uberjar :refer [uberjar]])
  (:import
    [java.io File]))


(def ^:dynamic *project* nil)


(defn windows? []
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"windows"))  boolean))


(defn mac? []
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"mac"))  boolean))


(defn assert-project [] 
  (assert (some? *project*) "'leiningen.george/*project*' is not bound!"))


(defn deployable-jar-path []
  (format "target/deployable/%s-%s-standalone.jar"  (:name *project*) (:version *project*)))


(defn assert-deployable []
  (let [p (deployable-jar-path)]
    (assert (.exists (cio/file p))
            (format "%s not found.\n  Do 'lein deployable' to build." p))))


(defn assert-jpms []
  (let [p (deployable-jar-path)]
    (assert (.exists (cio/file p))
            (format "%s not found. Do 'lein jpms' to build." p))))


(defn- java-home-str []
  (System/getenv "JAVA_HOME"))


(defn home 
  "Prints java home path (JAVA_HOME)"
  []
  (println (java-home-str)))


(defn java11? []
  (.startsWith (System/getProperty "java.version") "11"))


(defn assert-java11 []
  (assert (java11?)
          "This project requires Java 11.  See docs/java11.md for more."))



(defn copy-file-to-dir [dir file & [verbose?]]
  (let [f0 (.getAbsoluteFile (cio/file file))
        f1 (cio/file dir (.getName f0))]
    (when verbose?
      (println (format "Copying file: %s\n          to: %s" (str f0) (str f1))))
    (cio/copy f0 f1)
    f1))


(defn copy-files-to-dir [dir files & [verbose?]]
  (mapv #(copy-file-to-dir dir % verbose?) files))


(defn run
  "Does 'lein run -m <qualified-fn> & args'
  An easy way to run arbitrary functions within the project.
  Warning: This should only be used as a last call, as it call exit() at completion."
  ([qualified-fn]
   (run qualified-fn []))
  ([qualified-fn args]
   (apply lr/run (concat [*project* "-m" (str qualified-fn)] args))))


(defn- java-home-bin [cmd args]
  ;(prn 'java-home-bin cmd args)
  (assert-project)
  (let [exe (str (cio/file (java-home-str) "bin" (str cmd)))]
    ;(prn 'exe exe)
    (apply sh (cons exe args))))


(defn java 
  "Runs default 'java' with args"
  [args]
  (java-home-bin 'java args))


(defn jmod
  "Runs default 'jmod' with args"
  [args]
  (java-home-bin 'jmod args))


(defn jlink
  "Runs default 'jlink' with args"
  [args]
  (java-home-bin 'jlink args))


(defn- ts 
  "Prints ISO-timestamp"
  []
  (run 'george.launch.properties/print-now))


(defn clean [& [clean-target]]
  (lc/clean 
    (if-let [t clean-target] 
      (assoc *project* :clean-targets [(str t)]) 
      *project*)))


(defn ^File ensure-dir [dir]
  (let [d (cio/file dir)]
    (when-not (.exists d) (.mkdirs d))
    d))


(defn modules []
  (-> *project* :jre :modules))


(defn- spit-module-info []
  (let [reqs (apply str  (map #(str "    requires " (name %) ";\n")  (modules)))
        java (format "module george {\n    exports no.andante.george;\n%s}" reqs)]
    ;(format "(do (println \"%s\")(spit \"src/java/module-info.java\" \"%s\"))" java java)
    (println java)
    (spit "src/java/module-info.java" java)))


(defn- delete-module-info []
  (cio/delete-file "src/java/module-info.java" true))


(defn embed
  [args]
  (assert-project)
  (let [{:as kwargs} args
        kvargs (into {} (map (fn [[k v]] [(keyword (subs k 1)) v]) kwargs))]
    ;(prn kvargs)
    (run 'tasks.build/embed (concat [(:version *project*)] args))))


(defn deployable
  [args]
  (assert-project)
  (clean 'target/deployable)
  (clean 'target/uberjar)
  (embed args)
  (uberjar *project*)
  (run 'tasks.build/deployable))


(defn jpms 
  [args]
  (spit-module-info)
  ;; Java9+ modules does not allow unnamed packages (class-file in top-level). Therefore these must not be AOT-ed.   
  (deployable args)
  (delete-module-info))


(defn- tasks 
  "Similar to how help prints builtin and plugin tasks"
  []
  (->> (blt/namespaces-on-classpath :prefix "leiningen" :classpath "src_lein")
     (filter #(re-find #"^leiningen\.(?!do-args|george|server)[^\.]+$" (name %)))
     (distinct)
     (sort)))


(defn george [project]     

  (println "\nCustom tasks for George:")
  (doseq [task-ns (tasks)]
    (println (help/help-summary-for task-ns)))
  (println "\nRun `lein help $TASK` for details.\n"))
             

"

DONE:

lein embed
lein deployable

lein java 
lein java jlink
lein java jmod


lein jre
lein jre java
lein jre deployable  ;; runs dep on jre

lein jpms  ;; builds jpms (deployable)
lein java jpms   ;; rums jpms on java
lein jre  jpms  ;; runs jpms on jre

lein deployable install
lein deployable install-dir  ;; prints dir

lein aws deployable

lein serve 
lein serve status
lein serve stop
lein serve url 

TODO:

lein deployable uninstall

lein aws mac
lein aws win

lein native mac
lein native win


"