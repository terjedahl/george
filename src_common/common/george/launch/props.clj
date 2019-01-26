;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns common.george.launch.props
  (:refer-clojure :exclude [load])
  (:require
    [clojure.java.io :as cio]
    [common.george.config :as c]
    [common.george.util
     [cli :refer [warn]]
     [time :as t]
     [text :refer [pprint]]
     [platform :as pl]
     [files :as f]
     [props :as p]])
  (:import
    [java.io File IOException FileNotFoundException]
    [java.net UnknownHostException]))


