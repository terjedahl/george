;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.launch.utils
  (:import
    [java.io File InputStream OutputStream]
    [java.nio.file Files OpenOption]
    [java.net URL]))



(defn download
  "Optional total-fn takes 1 args: An long indicating the total number of bytes read/written."
  [^URL source ^File target & [total-fn]]
  (prn 'tranfer)
  (prn 'source source)
  (prn 'target target)
  (prn 'total-fn total-fn)
  (let [buffer (make-array Byte/TYPE 65536)]
    (with-open [^InputStream input (.getInputStream (-> source .openConnection))
                ^OutputStream output (Files/newOutputStream (.toPath target) (make-array OpenOption 0))]
      (loop [total-bytes 0]
        (let [size (.read input buffer)]
          (when (pos? size)
            (.write output buffer 0 size)
            (when total-fn
              (total-fn (+ total-bytes size)))
            (recur (+ total-bytes size))))))))