;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns no.andante.george.Launch
  (:require
    [clojure.java.io :as cio]
    [clojure.pprint :refer [pprint]]
    [environ.core :refer [env]]
    [george.launch
     [gui :as gui]
     [load :refer [make-and-set-classloader-for-jar invoke-loadOrRun]]]
    [common.george.config :as c]
    [common.george.util
     [files :as f]
     [cli :refer [debug info except exit]]]
    [george.application.launcher :as launcher]
    [george.javafx :as fx]
    [george.util :as u])
  (:import
    [it.sauronsoftware.junique JUnique AlreadyLockedException])
  (:gen-class
    :name no.andante.george.Launch
    :extends javafx.application.Application
    :methods [^:static [loadOrRun [javafx.stage.Stage "[Ljava.lang.String;"] void]]
    :main true))


(defn- gt
  "Returns true if 'a' is greater than 'b'. Similar to '>', but also works for other types,
  including strings and keywords (compare alphabetically).
  if 'silent?' is truthy, then 'nil' will be returned if 'a' or 'b' are nil, else NullPointerException is thrown."
  [a b & [silent?]]
  (if (or (nil? a) (nil? b))
    (when-not silent?
      (throw (NullPointerException. (format "(gt %s %s)" (pr-str a) (pr-str b)))))
    (< 0 (compare a b))))
;(println (gt "a" "b"))
;(println (gt "b" "a"))
;(println (gt "a" nil true)) ;; returns nil
;(println (gt  nil "a" true)) ;; returns nil
;(println (gt "a" nil)) ;; throws exception


;;;;


(def ^:dynamic *args* nil)
(def ^:dynamic *args-set* nil)


(defmacro with-args
  "Binds args to *args* and as a set to *args-set*, allowing for easier and faster access for those functions that are aware."
  [args & body]
  `(binding [*args* ~args *args-set* (set ~args)]
     ~@body))
;(pprint (macroexpand-1 '(with-args ["-a"])))


(defn- no-installed-check? []
  (when-let [res (or (*args-set* "--no-installed-check") (*args-set* ":no-installed-check"))]
    (debug res)
    true))


(defn- no-online-check? []
  (when-let [res (or (*args-set* "--no-online-check") (*args-set* ":no-online-check"))]
    (debug res)
    true))


(defn- no-check? []
  (if-let [res (or (*args-set* "--no-check") (*args-set* ":no-check"))]
    (do (debug res)
        true)
    (and (no-installed-check?) (no-online-check?))))


(defn- no-gui? []
  (when-let [res (or (*args-set* "--no-gui") (*args-set* ":no-gui"))]
    (debug res)
    true))


(defn- no-mutex? []
  (when-let [res (or (*args-set* "--no-mutex") (*args-set* ":no-mutex"))]
    (debug res)
    true))


(defn- help? []
  (when-let [res (#{"-h" "--help" "help" ":help"} (first *args*))]
    (debug res)
    true))


(defn- use-online? [this-ts installed-ts online-ts]
  (and
    ;; true if online-ts and online-ts GT this-ts
    (gt online-ts this-ts true)
    ;; true if installed-ts and online-ts and online-ts GT installed-ts
    (if installed-ts
      (gt online-ts installed-ts true)
      true)))


(defn- use-installed? [this-ts installed-ts]
  ;; true if installed-ts and installed-ts GT this-ts
  (gt installed-ts this-ts true))


;; TODO: Pass the args on to the main application!
(defn- this-run [stage args]
  (debug "this-run" stage)
  (gui/set-text "Loading application ...")
  (if (no-gui?)
    ;; TODO: main application should decide what to do if :no-gui not here!
    (do (info "No GUI. Exiting.") (exit))
    (future (launcher/start stage))))


(defn- installed-load [stage args]
  (gui/set-text "Loading installed application ...")
  (let [app (c/this-app)
        dir (c/installed-dir app)
        file (:file (c/installed-props app))
        jar-url (f/to-url (f/->path dir file))]
    (-> (make-and-set-classloader-for-jar jar-url)
        (invoke-loadOrRun stage args))))


(defn- online-download-load [stage args]
  (gui/set-text "Downloading newer version ...")
  (gui/set-progress -1)
  (let [{:keys [app uri]}   (c/this-props)
        {:keys [file size]} (c/online-props app uri)
        install-d           (c/installed-dir app)
        bytes-total-d       (Double/parseDouble size)]
    ;; Download jar
    (f/download (f/to-url (str uri file)) (cio/file install-d file) #(gui/set-progress (/ % bytes-total-d)))
    ;; Download props
    (gui/set-text "Installing ...")
    (gui/set-progress -1)
    (f/download (f/to-url (str uri c/PROP_NAME)) (cio/file install-d c/PROP_NAME))
    ;; Run the downloaded version
    (installed-load stage args)))


;; Will recur until this-run is reached
(defn- load-or-run [stage]
  (debug "Launch/load-or-run" stage)

  (if (no-check?)
    (this-run stage *args*)

    (let [{:keys [app uri ts]}
          (c/this-props)

          installed-ts
          (when-not (no-installed-check?)
               (:ts (c/installed-props app)))

          online-ts
          (when-not (no-online-check?)
               (:ts (c/online-props app uri)))]

      (debug (format "this-ts: %s  installed-ts: %s  online-ts: %s" ts installed-ts online-ts))

      (cond
        (use-online? ts installed-ts online-ts) (online-download-load stage *args*)
        (use-installed? ts installed-ts)        (installed-load stage *args*)
        :else                                   (this-run stage *args*)))))


(defn- main1 [& args]
  (debug "Launch/main1" args)
  (debug "logfile:" (env :logfile))
  (with-args args
    (fx/close-splash)
    (load-or-run (when-not (no-gui?) (gui/init-updater)))))


(defn- print-help []
  (println "George CLI help:

Optional arguments are:
  :help | help
    Prints this help text.
  :no-gui
    Runs without GUI, and then exits. Useful for quick testing of update-mechanism.
  :no-check
    Immediately runs this version, without checking installed or online version.
  :no-installed-check
    Does not check installed version, but may check online version, and if that is newer, will install and run it.
  :no-online-check
    Does not check online version, but may check installed version, and if that is newer, will run it.
  :no-mutex
    Allows starting George without setting or checking mutex lock.
"))


(defn- acquire-mutex-lock []
  (try (JUnique/acquireLock (c/this-app))
       (debug "Mutex lock acquired.")
       true
       (catch AlreadyLockedException _
         (except "Mutex lock not acquired.")
         false)))

;;;; GEN-CLASS


(defn -loadOrRun [stage args-array]
  (debug "Launch/-loadOrRun" stage (seq args-array))
  (with-args (seq args-array) (load-or-run stage)))


;; Don't run this from repl, as it will not return in the last case.
;; Run instead 'main'.
(defn -main [& args]
  (with-args args
             (when (help?)
               (print-help)
               (exit))

             (when-not (no-mutex?)
               (when-not (acquire-mutex-lock)
                 (exit -1)))

             (if (no-gui?)
               (apply main1 args)
               (u/with-latch (apply main1 args)))))
