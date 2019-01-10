;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.java
  (:require
    [leiningen.george.core :as g]))


(defn ^:pass-through-help java
  "Call 'java' (in JAVA_HOME) with args.   ...

If the first arg is ':jar' ':jpms', the built jar or jpms is run with the remaining args.

Examples:
 lein java -version
 lein java --list-modules
 lein java :jar
 lein java :jar :help
 lein java :jpms"

  [project & args]
  (binding [g/*project* project]
      (g/run-java args)))
