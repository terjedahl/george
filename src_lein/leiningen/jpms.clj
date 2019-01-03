;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.jpms
  (:require
    [leiningen.george :as g]))


(defn jpms 
  "Build the uberjar as a JPMS deployable

The JPMS can be run on standard java or on the custom JRE:
'lein java jpms'
'lein jre jpms' 

  (They will both fail, as JPMS is not supported by Clojure, 
   and because Clojures's 'compile'/'jar'/'uberjar' will not place the class files in a correct module directory.)
  (The second issue does not apply to 'javac' files.)
  
Do 'lein help embed' for info on optional args.

Modules to include are specified in project.clj -> :jre -> :modules

See documentation on building and deploying to learn more."
  [project & args]
  (binding [g/*project* project]
    (g/jpms args)))