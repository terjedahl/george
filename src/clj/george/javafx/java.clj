;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.javafx.java)


(defmacro vargs [& body]
    `(into-array [~@body]))

(defmacro vargs* [body]
    `(into-array ~body))

(defmacro vargs-t [typ & body]
    (assert (instance? Class (eval typ)) "First argument must be of type Class")
    `(into-array ~typ [~@body]))

(defmacro vargs-t* [typ body]
    (assert (instance? Class (eval typ)) "First argument must be of type Class")
    `(into-array ~typ ~body))

;; (vargs " a " (str 42)) -> (into-array [" a " (str 42)])
;; (vargs-t String " a " (str 42)) -> (into-array String [" a " (str 42)])
(comment prn (macroexpand-1 '(vargs " a " (str 42))))
(comment prn (macroexpand-1 '(vargs-t String "a" (str 42))))


(defmacro import! [specs]
    `(import ~@(if (instance? clojure.lang.Symbol specs) (eval specs) specs)))

;(defn singlethreadexecutor []
;    (java.util.concurrent.Executors/newSingleThreadExecutor))

;(defn singlethreadexecute-fn []
;    (let [exec (singlethreadexecutor)]
;        (fn [f] (. exec execute f))))