; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.embed
  (:require [leiningen.george :as g]))


(defn embed
  "Write a bare-bones app-file in src/rsc
  
Default values can be overridden using keys:
  :appid  # default: George-DEV
  :uri    # default: https://dowload.george.andante.no/<appid>/launch/
  :ts     # default: now as strict ISO: YYYY-MM-DDThh:mm:ssZ

E.g. 'lein embed :appid <some-string> :ts <some-string> :uri <some-uri>'"
  [project & args]
  (binding [g/*project* project]
    (g/embed args)))
