;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns no.andante.george.Launch

  (:require
    [clojure.java.io :as cio]
    [george.launch
     [config :as c]
     [load :refer [run-jar]]
     [properties :as p]
     [utils :refer [download]]]
    [george.files :as f]
    [george.javafx :as fx])
  (:import
    [javafx.scene Parent Node Scene]
    [javafx.scene.control ProgressIndicator ProgressBar Label]
    [javafx.scene.layout VBox StackPane]
    [no.andante.george Run]
    [javafx.application Platform]
    [javafx.geometry Insets]
    [javafx.stage Stage])


  (:gen-class
    :name no.andante.george.Launch
    :main true))


(defn- loader-root []
  (doto
    (StackPane. (into-array [(ProgressIndicator.)]))
    ;(.setPrefSize 200 80)
    (.setPrefSize 240 120)
    (.setPadding (Insets. 10))))


(defn- updater []
  (let [bar (doto (ProgressBar. -1)
                  (.setStyle "-fx-pref-width: 200;"))
        label (doto (Label. "Updating ...")
                    (.setStyle "-fx-font-weight: bold;"))
        box (doto (VBox. (into-array Node [label bar]))
                  (.setStyle "-fx-spacing: 10; -fx-padding: 25;"))]
    
    [box label bar]))


(defonce screen_ (atom nil))


(defn- set-updater []
  (when @screen_
    (let [[box label bar] (updater)
          root (-> @screen_ :stage .getScene .getRoot)]
      (Platform/runLater
        #(doto (.getChildren root) 
               (.clear)
               (.add box)))
      
      (swap! screen_ assoc :text label :bar bar)
      [label bar])))


(defn- ensure-updater []
  (when-let [screen @screen_]
    (if (:text screen)
        [(:text screen) (:bar screen)]
        (set-updater))))
      

(defn- update-progress [^double progress]
  (when-let [[_ bar] (ensure-updater)]
    (Platform/runLater
      #(.setProgress bar progress))))              


(defn- update-text [s]
  (println "[UPDATE]:" s)
  (when-let [[label _] (ensure-updater)]
    (Platform/runLater
      #(.setText label s))))


(defn- init-launch-screen []
  (fx/init)
  ;(fx/preload-fonts))
  (Platform/runLater
    #(let [stage 
             (doto (Stage.)
                   (.setTitle "Launch")
                   (.setScene (Scene. (StackPane. (into-array [(loader-root)]))))      
                   (.show))]
       (reset! screen_ {:stage stage}))))
;(init-launch-screen)


(defn- destroy-launcher-screen []
  (when-let [stage (:stage @screen_)]
    (Platform/runLater #(.hide stage))
    (reset! screen_ nil)))


;;;;;;;;;;;;;


(defn- no-gui? [args]
  ((set args) "--no-gui"))


(defn- no-check? [args]
  ((set args) "--no-check"))


(defn- no-installed-check? [args]
  (let [args- (set args)]
    (or (args- "--no-check") (args- "--no-installed-check"))))


(defn- no-online-check? [args]
  (let [args- (set args)]
    (or (args- "--no-check") (args- "--no-online-check"))))

;;;;;;;;;;;;;

(defn- use-installed? 
  "Returns true if there is an installed version and it's timestamp is newer than this."
  []
  (let [this-app (p/this-app)
        installed-app (p/installed-app (:appid this-app))]
    (when installed-app
      (p/gt (:ts installed-app) (:ts this-app)))))


(defn- use-online?
  "Returns true if there is an online version avaiable and it's timestamp is newer than this."
  []
  ;(println "## LAUNCHER - Inform that we are checking online.")
  (update-text "Checking online version ...")
  (let [this-app (p/this-app)
        online-app (p/online-app (:appid this-app) (:uri this-app))]
    (when online-app
      (p/gt (:ts online-app) (:ts this-app)))))


;;;;;;;;;;;;;


(defn- this-run [args]
  (prn 'Launch/this-run args)
  ;(println "## LAUNCHER - inform that (this) application is loading.")
  (update-text "Loading application ...")
  (future (Thread/sleep 2000) (destroy-launcher-screen))
  (Run/main (into-array String args)))
  ;(println "## LAUNCHER - close. (application will open it's own stage - for now!)"))
  ;; TODO: Can we pass stage to main, or alternative  function (main1)
  ;; TODO: We perhaps need to split up 'run-jar so we can have more fine-grained control over which functions etc.


;; TODO: is it possible to pass this stage to new jar?
(defn- installed-load [args]
  (prn 'Launch/installed-load args)
  ;(println "## LAUNCHER - inform that (installed) application is loading.")
  (update-text "Loading installed application ...")
  (let [appid (:appid (p/this-app))
        props (p/installed-app appid)
        jar-url (f/url (f/path (c/install-dir appid) (:file props)))]
    (future (Thread/sleep 2000) (destroy-launcher-screen))
    (run-jar jar-url "no.andante.george.Launch" args)))
    ;(println "## LAUNCHER - close. (New version will be used - for now!)")
    ;; TODO: is it possible to pass this stage to new jar?


(defn- online-download-load [args]
  (prn 'Launch/online-download-load args)
  ;(println "## LAUNCHER - inform that (online) application is downloading.")
  (update-text "Downloading ...")
  (update-progress -1)
  (let [{:keys [appid uri]} (p/this-app)
        {:keys [file size]} (p/online-app appid uri)
        install-d           (f/ensure-dir (c/install-dir appid))
        bytes-total-d       (Double/parseDouble size)]
        
    ;; install JAR and app-file
    ;(println "## LAUNCHER - update download progress.")
    ;(download  (f/url (str uri file))   (cio/file install-d file)  #(println "bytes:" % "/" size))
    (download  (f/url (str uri file))   (cio/file install-d file)  #(update-progress (/ % bytes-total-d)))

    (update-text "Installing ...")
    (update-progress -1)
    (download  (f/url (str uri p/PROP_N))  (cio/file install-d p/PROP_N))

    ;; start the downloaded version
    (installed-load args)))


;;;;;;;;;;;;;



(defn main1 [args]
  (prn 'Launch/main1 args)
  (println "  I am ts:" (:ts (p/this-app)))

  (when-not (no-gui? args)
    (init-launch-screen))
  
  ;(println "## LAUNCHER - an initial scroller should show (replacing splashimage).")

  (cond
    (or (no-check? args)
        (and (no-online-check? args) (no-installed-check? args)))
    (this-run args)
  
    (and (not (no-installed-check? args)) (use-installed?))
    (installed-load args)
  
    (and (not (no-online-check? args)) (use-online?))
    (online-download-load args)
  
    :else
    (this-run args)))


(defn -main [& args]
  (main1 args))