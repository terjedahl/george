;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.build
  (:require
    [leiningen.george.core :as g]
    [leiningen.help :as lh]))



(defn embed
  "Embed a basic app.properties file in src/rsc.   ...

When the jar is built, app.properties is first re-embedded in src/rsc, and then copied to the jar directory and enhanced.

Default values can be overridden using optional keys-value pairs:
  :app   <name-of-app>        # default: George-DEV
  :uri   <path-to-props-file> # default: https://dowload.george.andante.no/apps/<app>/platforms/<platform>/jar/
  :ts    <current-datetime>   # default: now as strict ISO: YYYY-MM-DDThh:mm:ssZ

Examples:
  lein embed
  lein embed :app George-ME
  lein embed :ts aaa
  lein embed :app George-ME :ts 2019-07-12T04:15:00Z :uri http://localhost:4242/apps/George-DEV/platforms/Windows/jar/

More on the parameters:
'app' determines the environment, allowing for running multiple versions of George in isolation of each other, including native installs and datafiles.  You will want to use 'George-DEV' (or any other name you choose, such as 'George-YOUR_NAME') when developing and testing. 'George-TEST' will be used for shared testing with multiple developers.  'George' is the production version.

'uri' is the path to where the program can find 'app.properties' and the accompanying jar-file.

'ts' is used for determining the newest version of the program using string comparison. It can be any string.
It just so happens the ISO date-time is both human-readable and sorts correctly as a string."

  [& args]
  (g/build-embed args))


(defn jar
  "Build the complete jar-file.                    ...

The built jar-file is platform specific (due to bundled native graphics files).
An app.properties file is first embedded in src/rsc, and then
copied to the jar dir and enhanced after the jar-file is built.

The optional args are the same as for the embed task.

For details information on the args, do:
  lein help build embed"
  [& args]
  (g/build-jar args))


(defn jpms
  "Build the complete jar-file as a JPMS module.   ...
This is an experimental feature.
See docs/java11.md for more."
  [& args]
  (g/build-jpms args))


(defn jre
  "Build the custom Java runtime.                  ...

The runtime is of course platform-specific,
and includes the modules listen in [:module :jre] in project.clj."
  []
  (g/build-jre))


(defn installer "Build the native installer."
  []
  (g/build-installer))


(defn site "NO IMPL  TODO:docs" []
  (g/build-site))


(defn all
  "Do jre, jar, installer, site in one.            ...

The args are the same as for jar (or embed).

For details information on the args, do:
  lein help build embed"
  [& args]
  (jre)
  (apply jar args)
  (installer)
  (site))


(defn build
  "Build the various George artifacts.     ..."
  {:subtasks [#'embed #'jar (if g/*jpms* #'jpms #'jar) #'jre #'installer #'site #'all]}
  [project & [subtask & args]]
  (binding [g/*project* project]
    (case subtask
      "embed"     (apply embed args)
      "jar"       (apply jar args)
      "jpms"      (apply jpms args)
      "jre"       (jre)
      "installer" (installer)
      "site"      (site)
      "all"       (apply all args)
      (lh/help project "build"))))