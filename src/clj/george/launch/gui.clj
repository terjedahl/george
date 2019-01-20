;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.gui
  (:require
    [george.javafx :as fx]
    [george.application.ui.styled :refer [style-stage]]
    [common.george.util
     [cli :refer [debug info]]])
  (:import
    [javafx.scene.control ProgressIndicator ProgressBar]))

"
Holds a map of either (initially)
  nil
or (after init)
  {:stage <stage-instance>}
or (when text or progress are updated)
  {:stage  <stage-instance>
   :label  <label-instance>
   :bar    <porgressbar-instance>
   :box    <vbox-instance>}
or (after destroy)
  nil
"
(defonce screen_
         (atom nil))


(defn- new-updater-root []
  (doto
    (fx/stackpane (ProgressIndicator.))
    (fx/set-pref-WH [300 120])
    (fx/set-padding 10)))


(defn- new-updater-layout []
  (let [bar   (doto (ProgressBar. -1) (.setStyle "-fx-pref-width: 260;"))
        label (fx/new-label "Launching ..." :size 14)]
    {:box   (fx/vbox label bar :spacing 14 :padding 25)
     :label label
     :bar   bar}))


(defn- set-updater-layout []
  (when @screen_
    (let [layout  (new-updater-layout)]
      (fx/later
        (-> @screen_ :stage .getScene .getRoot
            fx/children-clear
            (fx/children-add (:box layout))))
      (swap! screen_ merge layout))))


(defn- get-updater-layout []
  (when-let [screen @screen_]
    (if (:box screen)
      screen
      (set-updater-layout))))


(defn destroy-updater [& [delay-millis]]
  (when-let [stage (:stage @screen_)]
    (fx/future-sleep-later (or delay-millis 0) (.hide stage))
    (reset! screen_ nil)))


(defn init-updater []
  (fx/init)
  (fx/now
    (let [stage
          (style-stage
            (fx/stage
              :title "Launch George"
              :scene (fx/scene (fx/stackpane (new-updater-root)))
              :tofront true
              :onhidden #(destroy-updater)))]
      (reset! screen_ {:stage stage})
      stage)))


(defn set-progress [^double progress]
  (when-let [bar (:bar (get-updater-layout))]
    (fx/later (.setProgress bar progress))))


(defn set-text [s]
  (info "Updater:" s)
  (when-let [label (:label (get-updater-layout))]
    (fx/later (.setText label s))))


(defn get-stage []
  (:stage @screen_))