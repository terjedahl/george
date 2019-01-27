;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.launcher
  (:require
    [clojure.repl :refer [doc]]
    [clojure.java
     [browse :refer [browse-url]]]
    [environ.core :refer [env]]
    [g]
    [george
     [javafx :as fx]
     [applet :as applet]]
    [george.application.core :as core]
    [george.application.repl-server :as repl-server]
    [george.application.ui
     [stage :as ui-stage]
     [layout :as layout]
     [styled :as styled :refer [hr padding]]]
    [george.util.singleton :as singleton]
    [common.george.config :as c]
    [common.george.util.cli :refer [debug warn]])
  (:import
    [javafx.geometry Rectangle2D]
    [javafx.stage Stage WindowEvent]
    [javafx.application Platform]
    [javafx.scene.control Button MenuItem ContextMenu]
    [javafx.beans.property SimpleDoubleProperty]
    [javafx.scene.layout Pane VBox]
    [javafx.scene.text TextAlignment]
    [java.awt Desktop]
    [java.awt.desktop AboutHandler QuitHandler]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def tile-width 48)

(def launcher-width (+ ^int tile-width 20))

(def ABOUT_STAGE_KW ::about-stage)

(def versionf "
   George: %s
    build: %s
  Clojure: %s
     Java: %s")

(def copyright "
Copyright 2015-2019 Terje Dahl.
Powered by open source software.")


(defn george-version-ts []
  (let [{:keys [version ts]} (c/this-props)]
    [version ts]))


(defn- about-stage-create []
  (let [[version ts] (george-version-ts)
        version-info
        (doto
          (fx/new-label
            (format versionf
                    version
                    ts
                    (clojure-version)
                    (env :java-version))
            :font (fx/new-font "Roboto Mono" 12)))

        copyright-info
        (fx/new-label copyright :size 12)

        link
        (styled/new-link "www.george.andante.no" #(browse-url "http://www.george.andante.no"))

        root
        (fx/vbox
           (fx/imageview "graphics/George_logo.png" :width 160)
           version-info
           copyright-info
           link
           :padding 10)]

    (styled/style-stage
      (fx/stage
         :style :utility
         :sizetoscene true
         :title (str "About " (c/this-app))
         :onhidden #(singleton/remove ABOUT_STAGE_KW)
         :resizable false
         :scene (fx/scene root)))))


(defn- about-stage []
  (if-let [st ^Stage (singleton/get ABOUT_STAGE_KW)]
    (.hide st)
    (singleton/get-or-create ABOUT_STAGE_KW about-stage-create)))


(defmacro safe-desktop [& body]
  `(try (let [~'desktop (Desktop/getDesktop)] ~@body)
        (catch UnsupportedOperationException ~'e (warn (.getMessage ~'e)) ~'e)))
;(user/pprint (macroexpand-1 '(safe-desktop (println desktop))))


(defn- set-desktop-about-handler []
  (safe-desktop
    (.setAboutHandler desktop (reify AboutHandler (handleAbout [_ _] (fx/later (about-stage)))))))



;; TODO: BUG! Possibly in MacOS native code: The Quit dialog only works once!
(defn- DT-quit-handler []
  (reify QuitHandler
    (handleQuitRequestWith [_ _ response]
      (debug "Intercepted Quit")
      (.cancelQuit response)
      (fx/later
        (doto (core/get-application-stage)
          (.setIconified false)
          (#(.fireEvent % (WindowEvent. % WindowEvent/WINDOW_CLOSE_REQUEST))))))))


(defn- set-desktop-quit-handler []
  (safe-desktop
    (.setQuitHandler desktop (DT-quit-handler))))



(defn xyxy []
  (let [vb ^Rectangle2D (.getVisualBounds (fx/primary-screen))]
    [(.getMinX vb)
     (.getMinY vb)
     (+ (.getMinX vb) ^int launcher-width)
     (.getMaxY vb)]))


(defn launcher-height []
  (let [_xyxy (xyxy)]
    (- ^int (_xyxy 3) ^int (_xyxy 1))))


(defn- applet-tile
  "Builds a 'tile' (a parent) containing a labeled button (for the launcher)."
  [applet-info tile-width main-wrapper]
  ;(println app-info)
  ;(pprint app-info)
  ;(println ":icon-fn:" (:icon-fn app-info) (type (:icon-fn app-info)))
  (let [
        {:keys [label description icon main dispose]} applet-info
        arc 6
        label-font-size 10
        button-width (- ^int tile-width (* 2 arc))
        icon-width button-width
        dispose-fn
        (fn []
          (main-wrapper
            #(let [res (dispose)]
               (if (fx/node? res)
                   res
                   (styled/new-heading (format "'%s' unloaded" (label)))))))

        load-fn
        (fn []
          (future ;; avoid lag in button
            (fx/later (main-wrapper #(styled/scrolling-widget (format "Loading %s ..." (label)))))
            (Thread/sleep 200)  ;; enough time that the scroller will appear
            (fx/later (main-wrapper main))))

        tile
        (fx/vbox

          (doto (Button. nil (icon icon-width icon-width))
            (fx/set-tooltip (description))
            (fx/set-onaction load-fn)
            (.setPrefWidth button-width)
            (.setPrefHeight button-width)
            (.setStyle (format "-fx-background-radius: %s;" arc))
            (.setFocusTraversable false)
            (.setContextMenu
              (ContextMenu.
                (into-array
                  (list
                    (doto (MenuItem. (format "Dispose of (quit) '%s'" (label)))
                          (fx/set-onaction dispose-fn)))))))
          (doto (fx/new-label (label)
                              :size label-font-size)
                (.setMaxWidth tile-width)
                (.setWrapText true)
                (.setTextAlignment TextAlignment/CENTER))

          :alignment fx/Pos_CENTER
          :spacing 5)]
    tile))


(defn- launcher-root
  "The Launcher root node.  Was previously the sole content of Launcher.
  Is now inserted as \"master\" in the master-detail setup of the application window."
  [detail-setter]  ;; a 1-arg fn. If arg is ^javafx.scene.Node, then that node gets set as "detail" in application window.
  (let [
        welcome-node
        (styled/new-heading "Welcome to George" :size 24)

        main-wrapper ;; a function which calls the applet-fn, and passes the return-value to details-setter
        #(detail-setter (when % (%)))

        george-icon
        (fx/new-label nil 
           :graphic (doto (fx/imageview "graphics/George_icon_128_round.png")
                          (.setFitWidth tile-width)
                          (.setFitHeight tile-width)) 
           :tooltip  "\"home\""
           :mouseclicked  #(detail-setter (styled/new-heading (c/this-app) :size 24)))

        about-label
        (fx/new-label "About" 
                      :size 11
                      :mouseclicked about-stage)

        applet-infos
        (applet/load-applets)

        applet-tiles-and-paddings
        (flatten
          (map #(vector
                  (padding 20)
                  (applet-tile % tile-width main-wrapper))
               applet-infos))

        root ^VBox
        (apply fx/vbox
               (concat
                 [
                  (padding 10)
                  george-icon
                  (padding 10)
                  (hr launcher-width)]

                 applet-tiles-and-paddings

                 [
                  (fx/region :vgrow :always)

                  (hr launcher-width)
                  (padding 5)
                  about-label
                  (padding 5)
                  :padding 5
                  :alignment fx/Pos_TOP_CENTER]))

        dispose-fn
        #(doseq [applet applet-infos]
           (try ((:dispose applet))
                (catch Exception _ nil)))]

    (doto root
      (.setMaxWidth launcher-width)
      (.setMaxHeight (launcher-height)))

    (detail-setter welcome-node)

    (set-desktop-about-handler)
    (set-desktop-quit-handler)

    [root dispose-fn]))



(defn- stage-close-handler [^Stage application-stage dispose-fn]
  (fx/new-eventhandler
     (.toFront application-stage)
     (core/call-quit-dialog-listeners :show)
     (let [repl? (boolean (env :repl?))
           button-index
           (fx/now
             (fx/alert
               :title "Quit?"
               :text
               (str "Do you want to quit George?"
                    (when repl? "\n\n(You are running from a repl.\n'Quit' will not exit the JVM instance.)"))
               :options ["Quit"]  ;; quit is button-index 0
               :cancel-option? true
               :owner application-stage))
           exit? (= 0 button-index)]

          (if-not exit?
            (do
              (core/call-quit-dialog-listeners :cancel)
              (.consume event))
            (do
              (core/call-quit-dialog-listeners :quit)
              (repl-server/stop!)
              (dispose-fn)
              (println "Bye for now!" (if repl? " ... NOT" ""))
              (Thread/sleep 300)
              (when-not repl?
                (fx/now (Platform/exit))
                (shutdown-agents)  ;; For any lingering threads after using futures and such.
                (System/exit 0)))))))


(defn- double-property [init-value value-change-fn]
  (doto (SimpleDoubleProperty. init-value)
    (fx/add-changelistener (value-change-fn new-value))))


(defn- morphe-launcher-stage [^Stage stage ^Pane application-root [x y w h :as target-bounds]]
  ;; Fade out old content.
  (fx/later
    (doto stage
          (.toFront)
          (.setTitle  "...")))

  (ui-stage/swap-with-fades stage (fx/borderpane) true 300)
  (let [
        x-prop (double-property (.getX stage) #(.setX stage %))
        y-prop (double-property (.getY stage) #(.setY stage %))
        w-prop (double-property (.getWidth stage) #(.setWidth stage %))
        h-prop (double-property (.getHeight stage) #(.setHeight stage %))
        [root-node dispose-fn] application-root]
    ;; Transition stage.
    (future
      (fx/synced-keyframe
        300
        [x-prop x]
        [y-prop y]
        [w-prop w]
        [h-prop h])
      ;; Fade in Launcher root
      (ui-stage/swap-with-fades stage root-node true 400))

    (fx/later
      ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
      (doto stage
        (.setTitle (c/this-app))
        (.setResizable true)
        (core/set-application-stage)
        (fx/setoncloserequest (stage-close-handler stage dispose-fn))))))


;; TODO: This should be replaced by call to gui/...
(defn starting-stage
  "Called form Main/-start or launcher/main.
  Returns a small, centered stage, which will morph into the main application window."
  [& [^Stage stage]]
  (fx/now
    (doto (or stage (fx/stage))
      (.setTitle "Loading ...")
      (.setScene (fx/scene (ui-stage/scene-root-with-child) :size [240 80]))
      (.centerOnScreen)
      (.show)
      (.toFront))))


(defn application-root
  []
  (let [[master-detail-root master-setter detail-setter] (layout/master-detail)
        [l-root dispose-fn] (launcher-root detail-setter)]
    (core/init-state)
    (master-setter l-root)
    [master-detail-root dispose-fn]))


(defn start
  "Three versions of this method allow for different startup-strategies. The result is always that a created or given stage will be transformed (animated) into the launcher stage."
  ([]
   (start (styled/style-stage (starting-stage))))
  ([stage]
   (if-not stage
     (start)
     (start stage (application-root))))
  ([stage root]
   (morphe-launcher-stage stage root [0 0 1280 720])))


;;; DEV ;;;

;(when (env :repl?)  (fx/init) (start))

