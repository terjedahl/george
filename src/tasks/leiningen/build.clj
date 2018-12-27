; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns leiningen.build
  (:require
    [clojure.java.io :as cio]
    [leiningen.core.eval :refer [sh]]
    [environ.core :refer [env]]
    [selmer.parser :refer [render]]
    [leiningen.george :as g]
    [leiningen.jre :as jre]
    [clojure.string :as cs])

  (:import
    [java.io File]))


(defn- strict [version]
  (let [[maj min mic] (-> version  (cs/split #"-")  first  (cs/split #"\."))
        maj           (subs maj 2)
        mic           (or mic "0")]
    (format "%s.%s.%s" maj min mic)))


(defn- ^File wix-binaries
  "Returns the wix3xx-binaries dir, else nil"
  []
  (->> env  :user-dir  cio/file  .listFiles  seq  (filter #(re-matches #".*wix311-binaries" (str %)) )  first))


(defn- wix-executables []
  (when-let [dir (wix-binaries)]
    {:heat (str (cio/file dir "heat.exe"))
     :candle (str (cio/file dir "candle.exe"))
     :light (str (cio/file dir "light.exe"))}))


(defn- exe-heat [exe wix-dir Dir]
  (apply sh
         [exe
          "dir" (str "target\\" (.toLowerCase Dir))
          "-nologo" "-ag" "-srd" "-sreg"
          "-cg" (str Dir "Group")
          "-dr" (str Dir "Dir")
          "-var" (format "wix.%sDirSource" Dir)
          "-out" (str (cio/file wix-dir (str Dir "Group.wxs")))]))


(defn- exe-candle [exe wix-dir wxs-file-names]
  (apply sh
         (concat
           [exe  "-nologo"  "-arch" "x64"  "-out" (str wix-dir "\\")]
           (map #(str (cio/file wix-dir %)) wxs-file-names))))


(defn- exe-light [exe wix-dir windows-dir wix-Args obj-file-names msi-file-name]
  (apply sh
         (concat
           [exe  "-nologo" "-spdb" "-sacl"  "-out" (str (cio/file windows-dir msi-file-name))]
           (map #(format "-d%sDirSource=target\\%s" % (.toLowerCase %)) wix-Args)
           (map #(str (cio/file wix-dir %)) obj-file-names))))


(defn- build-msi [version]
  (if-let [{:keys [heat candle light]} (wix-executables)]

    (let [strict-version  (strict version)

          wix-dir         (cio/file "target" "wix")
          windows-dir     (cio/file "target" "windows")

          bat-tmp         (slurp (cio/file "src" "windows" "tmpl" "George.bat"))
          bat-rendered    (render bat-tmp {:version version})
          bat-file        (cio/file wix-dir "George.bat")

          wxs-tmpl        (slurp (cio/file "src" "windows" "tmpl" "George.wxs"))
          wxs-rendered    (render wxs-tmpl {:version version :strict-version strict-version :bat-file bat-file})
          wxs-file        (cio/file wix-dir (format "George-%s.wxs" version))]

      (g/clean wix-dir)
      (g/clean windows-dir)

      (.mkdirs wix-dir)
      (spit bat-file bat-rendered)
      (spit wxs-file wxs-rendered)

      (exe-heat heat wix-dir "Deployable")
      (exe-heat heat wix-dir "Jre")

      (exe-candle candle wix-dir ["DeployableGroup.wxs" "JreGroup.wxs" (format "George-%s.wxs" version)])

      (.mkdirs windows-dir)

      (exe-light light wix-dir windows-dir
                 ["Deployable" "Jre"]
                 ["DeployableGroup.wixobj" "JreGroup.wixobj" (format "George-%s.wixobj" version)]
                 (format "George-%s.msi" version)))

    (println "Error: Could not find directory 'wix311-binaries' in project dir
  Download latest 'wix311-binaries.zip' from  https://github.com/wixtoolset/wix3/releases
  and unpack in project dir.")))



(defn build
  [project & [subtask & args]]
  (binding [g/*project* project]
    (build-msi (:version project))))