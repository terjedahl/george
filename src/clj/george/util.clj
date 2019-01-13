;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.util
  (:require
    [clojure.pprint :refer [pprint]])
  (:import
    [java.util UUID]))


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
  (let [fut (future  (f))
        ret (deref fut timeout-ms timeout-val)]
    (when (= ret timeout-val)
      (future-cancel fut))
    ret))


(defmacro timeout
  "Returns the result of evaluating body, else returns timeout-val if timeout-ms passed."
  [timeout-ms timeout-val & body]
  `(timeout* ~timeout-ms ~timeout-val (fn [] ~@body)))
