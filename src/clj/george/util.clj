;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.util
  (:require
    [clojure.pprint :refer [pprint]])
  (:import
    [java.util UUID]
    [java.util.concurrent CountDownLatch]))


(defn uuid
  "Returns a new UUID string."
  []
  (str (UUID/randomUUID)))


(defrecord Labeled [label value]
  Object
  (toString [_] (str label)))

(defn labeled? [inst]
  (instance? Labeled inst))


(defn timeout*
  "Same as 'timeout', but evaluates the passed-in function 'f'."
  [timeout-ms timeout-val f]
  (let [fut# (future  (f))
        ret# (deref fut# timeout-ms timeout-val)]
    (when (= ret# timeout-val)
      (future-cancel fut#))
    ret#))


(defmacro timeout
  "Returns the result of evaluating body, else returns timeout-val if timeout-ms passed."
  [timeout-ms timeout-val & body]
  `(timeout* ~timeout-ms ~timeout-val (fn [] ~@body)))

;(prn 'res (timeout 1000 :failed (Thread/sleep 500) :sucess))
;(prn 'res (timeout 1000 :failed (Thread/sleep 1500) :sucess))


(defmacro with-latch
  "Prevents application from exiting when done -
  e.g. when running a JavaFX application without extending javafx.application.Application"
  [& body]
  `(let [latch# (CountDownLatch. 1)]
     ~@body
     (.await latch#)))

(defn- not-keyword? [x]
  (not (keyword? x)))


(defn partition-args
  "returns args in a vector vector: [args kwargs], where args is a seq and kwargs a map.
Optionally a map of default kwargs is applied to the kwargs.
If default-kwargs is supplies, then keywords not present in default will throw IllegalArgumentException."

  ([all-args]
   (partition-args all-args nil))

  ([all-args default-kwargs]
   (let [args (vec (take-while not-keyword? all-args))
         kwargs (apply hash-map (drop-while not-keyword? all-args))]

     (if-not default-kwargs
       ;; return args and kwargs as-is
       [args kwargs]
       ;; else check that all keyword are in default
       (let [unknowns (filter #(not ((set (keys default-kwargs)) %)) (keys kwargs))]
         (if (not-empty unknowns)
           (throw (IllegalArgumentException. (str "Unknown keywords: " (seq unknowns))))
           [args (merge default-kwargs kwargs)]))))))