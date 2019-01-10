;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.jre
  (:require
    [leiningen.george.core :as g]))


(defn ^:pass-through-help jre
  "Call 'java' (in custom JRE) with args.  ...

If the first arg is ':jar' ':jpms', the built jar or jpms is run with the remaining args.

Examples:
lein jre -version
lein jre --list-modules
lein jre :jar
lein jre :jar :help
lein jre :jpms"

  [project & args]
  (binding [g/*project* project]
    (g/run-jre args)))
