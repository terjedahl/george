;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.gui
  (:require
    [george.javafx :as fx]
    [george.application.ui.styled :refer [style-stage]]
    [george.util.math :as um]
    [common.george.util.cli :refer [debug info]])
  (:import
    [javafx.scene.control ProgressIndicator ProgressBar]))


(def W 300)
(def H 120)

(def proto-screen 
  {:stage nil  ;; stage-instance
   :layout nil ;; nil or layout-instance
   :label nil  ;; nil or label-instance
   :bar nil})  ;; nil or progressbar-instance

(defonce screen_ (atom nil))


(defn- new-updater-root []
  (doto
    (fx/stackpane (ProgressIndicator.))
    (fx/set-pref-WH [W H])
    (fx/set-padding 10)))


(defn- new-updater-layout []
  (let [bar   (doto (ProgressBar. -1) (.setStyle "-fx-pref-width: 260;"))
        label (fx/new-label "Launching ..." :size 14)]
    {:layout   (fx/vbox label bar :spacing 14 :padding 25)
     :label label
     :bar   bar}))


(defn- merge-updater-layout []
  (let [layout (new-updater-layout)]
    (fx/now
      (-> @screen_ :stage .getScene .getRoot
          fx/children-clear
          (fx/children-add (:layout layout))))
    (swap! screen_ merge layout)))


(defn- get-updater-layout []
  (when-let [screen @screen_]
    (if (:layout screen)
      screen
      (merge-updater-layout))))


(defn destroy-updater [& [delay-millis]]
  (when-let [stage (:stage @screen_)]
    (fx/future-sleep-later (or delay-millis 0) (.hide stage))
    (reset! screen_ nil)))


(defn init-updater []
  (fx/init :classloader (.getContextClassLoader (Thread/currentThread)))
  (let [[p-w p-h] (fx/primary-WH)
        stage
        (fx/now
          (fx/stage
            :title "Launch George"
            :scene (fx/scene (fx/stackpane (new-updater-root)))
            :location [(um/half-diff p-w W) (um/half-diff p-h H)]
            :tofront true
            :onhidden #(destroy-updater)))]
    (reset! screen_ (assoc proto-screen :stage stage))
    (fx/later (style-stage stage))
    stage))


(defn set-progress [^double progress]
  (when-let [bar (:bar (get-updater-layout))]
    (fx/later (.setProgress bar progress))))


(defn set-text [s]
  (info "Updater:" s)
  (when-let [label (:label (get-updater-layout))]
    (fx/later (.setText label s))))


(defn get-stage []
  (:stage @screen_))