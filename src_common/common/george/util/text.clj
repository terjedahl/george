;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.text
  (:require
    [clojure.pprint :as cpp]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn return-char? [ch]
  (identical? \return ch))


(defn newline-char? [ch]
  (identical? \newline ch))


(defn formfeed-char? [ch]
  (identical? \formfeed ch))


(defn newline-formfeed-char? [ch]
  (or (newline-char? ch) (formfeed-char? ch)))


(defn space-char? [ch]
  (identical? \space ch))


(defn tab-char? [ch]
  (identical? \tab ch))


(defn whitespace-char? [ch]
  (or (space-char? ch)
      (newline-char? ch)
      (formfeed-char? ch)
      (tab-char? ch)
      (return-char? ch)))


(def readermacro-chars #{\#})


(def macro-chars #{\' \^ \@ \` \~})


(def coll-delim-chars #{\{ \[ \( \) \] \}})


(defn readermacro-char? [ch]
  (readermacro-chars ch))


(defn macro-char? [ch]
  (macro-chars ch))


(defn comment-char? [ch]
  (= ch \;))


(defn string-delim-char? [ch]
  (= ch \"))


(defn coll-delim-char? [ch]
  (coll-delim-chars ch))


(defn coll-delim-char-complement
  "Returns the coll-delim-char which matches the passed-in char, else nil"
  [ch]
  (case ch
    \( \)
    \) \(
    \[ \]
    \] \[
    \{ \}
    \} \{
    nil))


(defn coll-delim-char-complement? [ch1 ch2]
  (= (coll-delim-char-complement ch1) ch2))


(defn newline-end? [seq-of-chars-or-string]
  (= \newline (last (seq seq-of-chars-or-string))))


(defn ensure-newline [obj]
  "ensures that the txt ends with a newline"
  (let [txt (if (nil? obj) "nil" (str obj))]
    (if (newline-end? txt)
      txt
      (str txt \newline))))


(defn ^String ensure-trailing-slash [^String s]
  (if (.endsWith s "/") s (str s \/)))


(defn ^String strip-trailing-slash [^String uri]
  (if (.endsWith uri "/") (subs uri 0 (dec (count uri))) uri))


(defn ^String pprint
  "Same as clojure.core/pprint"
 ([object]
  (cpp/pprint  object))
 ([object writer]
  (cpp/pprint object writer)))


(defn ^String pformat
  "Returns the data as a pprint-ed string."
  [object]
  (cpp/write object :stream nil))


(defn **
  "Returns string consisting of n*s"
  [n s]
  (apply str (repeat n s)))
