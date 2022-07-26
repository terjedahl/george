;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns example.app)

(defn -main [& args]
    (println "Clojure: (george.example.application/-main ...): Hello World!")
    (println "   args:"
             (if (empty? args)
                 "NO ARGS"
                 (apply str (interpose " " args)))))



(defn hello []
    "Hello from george.example.application/hello")
