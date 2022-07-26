;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; implementation of: http://blog.netopyr.com/2012/06/14/using-the-javafx-animationtimer/

(ns
  example.stars
  (:require
    [george.javafx :as fx])
  (:import 
    [java.util Random]
    [javafx.scene.paint Color]
    [javafx.animation AnimationTimer]
    [javafx.stage Stage]
    [javafx.scene Node Scene ]
    [com.sun.javafx.perf PerformanceTracker]
    [java.security AccessControlException]
    [javafx.scene.control Label]))


;; primitive math is faster
;(set! *unchecked-math* :warn-on-boxed)

;; avoiding reflection is A LOT faster!
;(set! *warn-on-reflection* true)



(def STAR_COUNT 20000)
; (def STAR_COUNT 200000)

;; 2 billion possible steps between origo and edge of circle
(def TWO_BILL 2000000000)

;; A double value of the same, to avoid some casting
(def TWO_BILL_D (double TWO_BILL))

;; multiply with SQRT_" to ensure that the radius reaches corners of stage
(def SQRT_2 (Math/sqrt 2))
;(def SQRT_2 (* (Math/sqrt 2) 10.))

(def random (Random.))

;; precalculated seq of numbers - for iteration over
(def STAR_COUNT_RANGE (range STAR_COUNT))
;(def STAR_COUNT_RANGE (range (/ STAR_COUNT 1000)))



;; calculate random starting points along radius for all stars - into Java primitive array
(def starts (long-array (repeatedly STAR_COUNT #(. ^Random random nextInt TWO_BILL))))

;; calculate randome angles for all stars - into Java primite array
(def angles (double-array  (repeatedly STAR_COUNT #(* 2. Math/PI (. ^Random random nextDouble)))))

;; pre-calculate sines and cosines for angles - into Java primite arrays
(def angles_cos (double-array (map #(Math/cos %) angles)))
(def angles_sin (double-array (map #(Math/sin %) angles)))


(def nodes
    (vec (repeatedly STAR_COUNT #(fx/rectangle :size [1 1] :fill Color/WHITE))))


(def fpsl_ (atom nil))

(defn ^Label fps-label []
  (if-let [l @fpsl_]
    l
    (reset! fpsl_ (fx/new-label "FPS" :color Color/RED))))



(def ^Scene scene
  (memoize ;; effectively making this a lazy singleton.
    (fn []  
     (fx/scene
        (apply fx/group (into-array Node (conj nodes (fps-label))))
        :fill Color/BLACK))))


;; http://stackoverflow.com/questions/28287398/what-is-the-preferred-way-of-getting-the-frame-rate-of-a-javafx-application
(try
    (System/setProperty "prism.verbose" "true")
    (System/setProperty "prism.dirtyopts" "false")
    (System/setProperty "javafx.animation.fullspeed" "true")
    (System/setProperty "javafx.animation.pulse" "10")
    (catch AccessControlException e (. e printStackTrace)))

(def tracker (memoize (fn [] (PerformanceTracker/getSceneTracker (scene)))))

(defn- fps []
    (. ^PerformanceTracker (tracker) getAverageFPS))


(defn- timer [^Stage stage]
  (proxy [AnimationTimer] []
    (handle [now]
      (let [
            width (* 0.5 ^double (. stage getWidth))
            height (* 0.5 ^double (. stage getHeight))
            radius ^double (* ^double SQRT_2 (Math/max width height))
            ]
          (loop [i 0]
              (when (< i ^int STAR_COUNT)
                  (let [
                        t (rem (- ^int now (aget ^longs starts i)) ^int TWO_BILL)
                        d (/ (* t radius) ^double TWO_BILL_D)
                        ]
                      (doto ^Node (get nodes i)
                          (. setTranslateX (+ (*  (aget ^doubles angles_cos i) d) width))
                          (. setTranslateY (+ (*  (aget ^doubles angles_sin i) d) height))
                          )
                      (recur (inc i)))))
          )
        (. ^Label fps-label (setText (format " FPS: %.2f   [reset]" (fps))))
        )))

  
(defn -main [& _]
  (fx/init)
  (fx/later
      (let [stage 
            (fx/stage
               :title "stars.clj"
               :scene (scene)
               :size [800 600] )

            timer ^AnimationTimer (timer stage)]

          (.setOnCloseRequest stage (fx/event-handler (.stop timer)))

          (.setOnMouseClicked ^Label (fps-label) 
             (fx/event-handler
               (println "resetting fps ...")
               (.resetAverageFPS ^PerformanceTracker (tracker))))

          (.start timer)

          )))

;(println "Warning: Running george.example.stars/-main" (-main))
