;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.applet.turtle-ide
  (:require
    [george.applet :refer [->AppletInfo]]
    [george.javafx :as fx]
    [george.application.ui.styled :as styled])

  (:import
    [javafx.animation Timeline]
    [javafx.scene.paint Color]
    [javafx.scene.shape Polygon]
    [java.util List]))

;(set! *warn-on-reflection* true)

(defn- turtle-poly []
  (fx/polygon
    5 0
    -5 -5
    -3 0
    -5 5
    :fill fx/ANTHRECITE))


(defn icon
  "Returns a node of the given dimension - to be place in the middle fo the launcher button."
  [w h]
  (let [x1 10
        y1 (/ h 2)
        x2 (- w 10)
        y2 (/ h 2)

        background-rect
        (fx/rectangle :size [w h] :fill Color/TRANSPARENT)
        trtl
        (doto (turtle-poly)
              (fx/set-translate-XY [x1 y1]))
        t
        (doto ^Timeline (fx/simple-timeline
                          1000
                          nil
                          [(.translateXProperty ^Polygon trtl) x2]
                          [(.translateYProperty ^Polygon trtl) y2])
          (.setCycleCount Timeline/INDEFINITE)
          (.setAutoReverse true))

        g
        (doto
              (fx/group
                background-rect
                (doto (fx/line :x1 x1
                               :y1 y1
                               :x2 x2
                               :y2 y2
                               :color Color/DODGERBLUE)
                  (-> .getStrokeDashArray (.setAll ^List (list 5. 5.))))
                trtl)

          (.setOnMouseEntered (fx/new-eventhandler (.play t)))
          (.setOnMouseExited (fx/new-eventhandler (.pause t))))]
    g))


(defn label []
  "Turtle Geometry")


(defn description []
  "Turtle Geometry IDE
(Interactive Development Environment)")


(defn main []
  (require '[george.application.environment :as ide])
  ((resolve 'ide/ide-root) :turtle))


(defn dispose []
  ((resolve 'ide/ide-root-dispose) :turtle)
  (styled/new-heading (format "%s has been disposed" (label))))


(defn applet-info []
  (->AppletInfo
    'label
    'description
    'icon
    'main
    'dispose))
