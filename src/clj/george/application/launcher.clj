;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.launcher
  (:require
    [environ.core :refer [env]]
    [george
     [javafx :as fx]
     [applet :as applet]]
    [george.application.core :as core]
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
    [javafx.scene.control MenuItem ContextMenu]
    [javafx.beans.property SimpleDoubleProperty]
    [javafx.scene.layout Pane VBox]
    [javafx.scene.text TextAlignment]
    [java.awt Desktop]
    [java.awt.desktop AboutHandler QuitHandler]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def TILE_W 64)
(def MARGIN 15)
(def LAUNCHER_W (+ MARGIN TILE_W MARGIN))

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
        version-str  (format versionf version ts (clojure-version) (env :java-version))

        copy-fn
        #(let [stage (fx/stage
                       :style :undecorated
                       :scene (fx/scene
                                (doto
                                  (fx/vbox
                                    (fx/new-label "  Copied" :size 18)
                                    (fx/new-label version-str :font (fx/new-font fx/ROBOTO_MONO 12))
                                    :padding 10)
                                  (.setBorder (fx/new-border fx/GREY))))
                       :owner (core/get-application-stage))]
           (fx/set-clipboard-str version-str)
           (fx/future-sleep-later 1000 (.hide stage)))

        version-info
        (fx/new-label
          version-str
          :font (fx/new-font fx/ROBOTO_MONO 12)
          :mouseclicked copy-fn
          :tooltip "Click to copy version info")

        copyright-info
        (fx/new-label copyright :size 12)

        url "http://www.george.andante.no"

        link
        (doto (styled/new-link "www.george.andante.no"
                               (fn []
                                 (require '[clojure.java.browse])
                                 ((resolve 'clojure.java.browse/browse-url) url)))
          (.setFont (fx/new-font fx/ROBOTO 12))
          (fx/set-tooltip (str "Open in browser: " url)))

        root
        (fx/vbox
           (fx/imageview "graphics/George_logo.png" :width 160)
           version-info
           copyright-info
           link
           :padding 10)]

    (styled/style-stage
      (fx/stage
         :style       :utility
         :sizetoscene true
         :title       (str "About " (c/this-app))
         :onhidden   #(do (core/notify-dialog-listeners false)
                          (singleton/remove ABOUT_STAGE_KW))
         :resizable  false
         :scene      (fx/scene root)
         :owner      (core/get-application-stage)))))


(defn- about-stage []
  (if-let [st ^Stage (singleton/get ABOUT_STAGE_KW)]
    (.hide st)
    (do
      (core/notify-dialog-listeners true)
      (singleton/get-or-create ABOUT_STAGE_KW about-stage-create))))


(defmacro safe-desktop [& body]
  `(try (let [~'desktop (Desktop/getDesktop)] ~@body)
        (catch UnsupportedOperationException ~'e (warn (.getMessage ~'e)) ~'e)))
;(user/pprint (macroexpand-1 '(safe-desktop (println desktop))))


(defn- set-desktop-about-handler []
  (safe-desktop
    (.setAboutHandler desktop (reify AboutHandler (handleAbout [_ _] (fx/later (about-stage)))))))


;; BUG! Possibly in MacOS native code: The Quit dialog only works once!
(defn- DT-quit-handler []
  (reify QuitHandler
    (handleQuitRequestWith [_ _ response]
      ;(debug "Intercepted Quit")
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
     (+ (.getMinX vb) ^int LAUNCHER_W)
     (.getMaxY vb)]))


(defn launcher-height []
  (let [_xyxy (xyxy)]
    (- ^int (_xyxy 3) ^int (_xyxy 1))))


(defn- applet-tile
  "Builds a 'tile' (a parent) containing a labeled button (for the launcher)."
  [applet-info main-wrapper]
  (let [{:keys [label description icon main dispose]} applet-info

        icon-width (- TILE_W 12)

        dispose-fn
        (fn []
          (main-wrapper
            #(let [res (dispose)]
               (if (fx/node? res) res (styled/new-heading (format "'%s' unloaded" (label)))))))

        load-fn
        (fn []
          (fx/future-later ;; avoid lag in button
            (main-wrapper
              #(styled/scrolling-widget (format "Loading %s ..." (label)) true))
            (fx/future-sleep-later 50 ;; enough time that the scroller will render
              (main-wrapper main))))]

    (fx/vbox
      (doto
        (fx/new-button nil
           :graphic (icon icon-width icon-width)
           :tooltip (description)
           :onaction load-fn
           ;:style (format "-fx-background-radius: %s;" arc)
           :focusable? false
           :all-WH [TILE_W TILE_W])
        (fx/add-class "g-launcher-button")
        (.setContextMenu
          (ContextMenu. (into-array (list (doto (MenuItem. (format "Dispose of '%s'" (label)))
                                                (fx/set-onaction dispose-fn)))))))


      (doto (fx/new-label (label) :size 11)
        (.setMaxWidth TILE_W)
        (.setWrapText true)
        (.setTextAlignment TextAlignment/CENTER)
        (.setAlignment fx/Pos_CENTER))
      :spacing 5 :alignment fx/Pos_CENTER)))


(defn- launcher-root
  "The Launcher root node.  Was previously the sole content of Launcher.
  Is now inserted as \"master\" in the master-detail setup of the application window."
  [detail-setter]  ;; a 1-arg fn. If arg is ^javafx.scene.Node, then that node gets set as "detail" in application window.
  (let [welcome-node
        (styled/new-heading "Welcome to George" :size 24)

        main-wrapper ;; a function which calls the applet-fn, and passes the return-value to details-setter
        #(detail-setter (when % (%)))

        george-icon
        (fx/new-label nil 
           :graphic      (fx/imageview "graphics/George_icon_128.png" :width 64)
           :tooltip      (c/this-app)
           :mouseclicked #(detail-setter (styled/new-heading (c/this-app) :size 24)))

        about-label
        (doto
          (fx/new-label (first (george-version-ts))
                        :size 11
                        :color fx/THREES
                        :style "-fx-padding: 2 5 2 5; -fx-background-color: WHITE;"
                        :tooltip (format "Click to view or hide \"About %s\"" (c/this-app))
                        :mouseclicked about-stage)
          (fx/set-all-WH [TILE_W nil])
          (.setAlignment fx/Pos_CENTER)
          (.setBorder (fx/new-border fx/THREES 1.5 4)))

        applet-infos
        (applet/load-applets)

        tiles
        (interpose
          (padding  MARGIN)
          (map #(applet-tile % main-wrapper) applet-infos))

        root ^VBox
        (apply fx/vbox
               (concat
                 (list
                   (padding MARGIN LAUNCHER_W)
                   george-icon
                   (padding (* 2 MARGIN)))
                 tiles
                 (list
                   (fx/region :vgrow :always)
                   (padding (* 2 MARGIN))
                   about-label
                   (padding MARGIN)
                   :alignment fx/Pos_TOP_CENTER)))

        dispose-fn
        #(doseq [applet applet-infos]
           (try ((:dispose applet)) (catch Exception _ nil)))]

    (doto root
      (.setMaxWidth LAUNCHER_W)
      (.setMaxHeight (launcher-height)))

    (detail-setter welcome-node)

    (set-desktop-about-handler)
    (set-desktop-quit-handler)

    [root dispose-fn]))



(defn- stage-close-handler [^Stage application-stage dispose-fn]
  (fx/new-eventhandler
     (.toFront application-stage)
     (core/notify-dialog-listeners true)
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
              (core/notify-dialog-listeners false)
              (.consume event))
            (do
              (require '[george.application.repl-server :as repl-server])
              ((resolve 'repl-server/stop!))
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


(defn application-root []
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

