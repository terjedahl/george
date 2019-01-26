(ns george.util.math)

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)

(defn clamp-int
  "low and high (both inclusive)"
  [low  x  high]
  (max ^int low (min ^int x ^int high)))


(defn clamp-double
  "low and high (both inclusive)"
  [low  x  high]
  (max ^double low (min ^double x ^double high)))


(defn in-range?
  "returns true if x is in range of low and high (both inclusive)"
  [low high x]
  (<= low  x high))


(defn half-diff [x1 x2]
  (-> (- x1 x2) (/ 2)))


(defn radians->x-factor [rad]
  (Math/cos rad))

(defn radians->y-factor [rad]
  (Math/sin rad))

(defn degrees->x-factor [deg]
  (radians->x-factor (Math/toRadians deg)))

(defn degrees->y-factor [deg]
  (radians->y-factor (Math/toRadians deg)))

(defn degrees->xy-factor [deg]
  [(degrees->x-factor deg) (degrees->y-factor deg)])

(defn hypotenuse [x y]
  (Math/sqrt (+ (* x x) (* y y))))