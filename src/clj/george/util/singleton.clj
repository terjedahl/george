;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util.singleton
  (:refer-clojure :exclude [get remove keys])  ;; https://gist.github.com/ghoseb/287710
  (:require
    [clojure.pprint :refer [pprint]]))


(def ^:dynamic *debug* false)

(defmacro debugm 
  "Executes 'body' if *debug* is bound to 'true'"
  [& body]
  `(when *debug*
     ~@body))

;(prn (macroexpand-1 '(debugm (println "no-debug") (prn 40))))
;(prn (binding [*debug* true] (macroexpand-1 '(debugm (println "debug") (prn 41)))))
;(binding [*debug* true] (debugm (print "should print ") (prn 42)))



(defn- debug
  "println 'args' if *debug* is bound to 'true'"
  [& args]
  (debugm (apply println args)))


(defmacro debug! [& body]
  `(binding [*debug* true]
     ~@body))
;(prn (macroexpand-1 '(debug! (debug "in debug-mode"))))
;(debug! (debug "in debug-mode"))



;;; Singleton patterns ;;;


;; this should be private!
(defonce singletons_ (atom {}))


(defn get
  "returns value for given key  if exists, else nil"
  [k]
  (@singletons_ k))


(defn put
  "Sets value for given key, then returns value"
  [k v]
  (swap! singletons_ assoc k v)
  v)


(defn get-or-create
  "Returns value for given key if exists,
  else calls provided function, setting its return-value to the key, and returning the value."
  [k f]
  (debugm (printf "singleton/get-or-create '%s' ... " k))
  (if-let [v (get k)]
    (do (debug " ... found")
        v)
    (do
      (debug " ... created")
      (let [v (f)]
        (put k v)))))


(defn remove
  "Removes singleton from singleton-map"
  [k]
  (debugm (printf "singleton/remove '%s' ... " k))
  (if-let [_ (get k)]
        (do
          (swap! singletons_ dissoc k)
          (debug "done"))
        (debug "not found")))


(defn remove-all
  "Removes all singletons by reset-ing the atom to an empty map."
  []
  (reset! singletons_ {}))


(defn keys []
  (clojure.core/keys @singletons_))


(defn print-all-keys []
  (pprint (keys)))