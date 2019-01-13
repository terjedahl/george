;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.code)


(defmacro code
  "Returns a single string containing the pr-str'd representations of the given expressions, wrapped in a do if more than one.
  Useful for writing expressions to be evaluated in leiningen aliases using 'exec -e'.
  (Similar to clojure.tools.nrepl/code, but for the potential 'do' wrapping.)"
  [& body]
  (if (< 1 (count body))
    `(prn-str (quote (do ~@body)))
    `(prn-str (quote ~@body))))
;(prn (macroexpand-1 '(code (+ 4 5))))
;(prn (code (+ 4 5)))
;(prn (macroexpand-1 '(code (+ 4 5) (+ 6 7))))
;(prn (code (+ 4 5) (+ 6 7)))


(defmacro defcode
  "Passes body to 'code', then defs the resulting string with the given name."
  [name & body]
  `(def ~name (code ~@body)))
;(prn (macroexpand '(defcode x (+ 4 5))))
;(prn (defcode y (+ 4 5)))
;(prn 'y y)
;(prn (macroexpand-1 '(defcode x (+ 4 5) (+ 6 7))))
;(prn (defcode z (+ 4 5) (+ 6 7)))
;(prn 'z z)
