;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.util.time
  (:import
    [java.time Instant]
    [java.time.temporal ChronoUnit]))

;; https://en.wikipedia.org/wiki/ISO_8601

(defn ^String now-iso ;; -extended
  "Returns a str version of 'now' (UTC) - yyyy-mm-ddThh:mm:ssZ.
  Adherent to JS standards."
  [& [offset-millis]]
  (-> (Instant/now)
      (.plusMillis  (or offset-millis 0))
      (.truncatedTo  ChronoUnit/SECONDS)
      str))


(defn ^String now-iso-basic
  "Similar to 'now-iso', but without problematic colons, making it file-safe, but less legible."
  [& [offset-millis]]
  (-> (now-iso (or offset-millis 0))
      (.replace "-" "")
      (.replace ":" "")))