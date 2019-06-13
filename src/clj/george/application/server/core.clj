;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.server.core
  (:require
    [clojure.java.io :as cio]
    [environ.core :refer [env]]
    [common.george.util
     [text :as gut]
     [cli :refer [debug info warn]]]))


(def ^:dynamic *server* (-> (env :george-server (slurp (cio/resource "george-server")))
                            gut/ensure-trailing-slash))
(if (env :george-server)
  (info (format "Using env var GEORGE_SERVER: '%s'" *server*))
  (debug (format "Reading george-server from resource: '%s'" *server*)))


(defn ^String url
  "Returns a complete url base on *server*"
  [path]
  (str (gut/ensure-trailing-slash *server*) path))


(defmacro with-server
  "Supports re-bind *server*"
  [& body]
  `(binding [*server* (gut/ensure-trailing-slash *server*)]
       ~@body))



