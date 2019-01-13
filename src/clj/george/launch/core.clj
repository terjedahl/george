;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.core)


(defn gt
  "Is 'a' greater than 'b'. Similar to '>', but also works for other types, including strings and keywords (compare alphabetically).
  if 'silent?' is truthy, then 'nil' will be returned if 'a' or 'b' are nil, else NullPointerException is thrown."
  [a b & [silent?]]
  (if (or (nil? a) (nil? b))
    (when-not silent?
      (throw (NullPointerException. (format "(gt %s %s)" (pr-str a) (pr-str b)))))
    (< 0 (compare a b))))
;(println (gt "a" "b"))
;(println (gt "b" "a"))
;(println (gt "a" nil true)) ;; returns nil
;(println (gt "a" nil)) ;; throws exception
