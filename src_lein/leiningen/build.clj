;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

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


(defn- ^File wix-binaries-dir
  "Returns the wix3xx-binaries dir, else nil"
  []
  (if-let [d (->> env  :user-dir  cio/file  .listFiles  seq  (filter #(re-matches #".*wix311-binaries" (str %)))  first)]
    d
    (binding [*out* *err*]
      (println "Error: Could not find directory 'wix311-binaries' in project dir.
  Download latest 'wix311-binaries.zip' from  https://github.com/wixtoolset/wix3/releases
  and unpack in project dir."))))


(defn- ^File signtool-dir
  "Returns the signtool dir, else nil"
  []
  (if-let [d (->> env  :user-dir  cio/file  .listFiles  seq  (filter #(re-matches #".*signtool" (str %)))  first)]
    d
    (binding [*out* *err*]
      (println "Warning: Could not find directory 'signtool' in project dir.
  Download Microsoft's 'signtool' from  http://cdn1.ksoftware.net/signtool_8.1.zip
  and unpack in project dir."))))


(defn- ^File  pfx-file
  "Returns the pfx-file, else nil"
  []
  (let [f (cio/file "codesign-key.pfx")]
    (if (.exists f)
      f
      (binding [*out* *err*]
        (println (format "Error: Could not find file '%s' in project dir." f))))))


(defn- ^File  password-file
  "Returns the password-file - containing a plaintext password for the pfx-file, else nil"
  []
  (let [f (cio/file "codesign-password.txt")]
    (if (.exists f)
      f
      (binding [*out* *err*]
        (println (format "Error: Could not find file '%s' in project dir." f))))))


(defn- sign-msi [msi-file]
  (when-let [st-d (signtool-dir)]
    (when-let [pfx-f (pfx-file)]
      (when-let [pw-f (password-file)]
        (sh (str (cio/file st-d "signtool.exe"))
            "sign"
            "/d" (-> (.getName msi-file) (cs/split #".") first (#(format "%s Installer" %)))
            "/f" (str pfx-f)
            "/p" (.trim (slurp pw-f))
            "/tr" "http://timestamp.comodoca.com/rfc3161"
            "/fd" "sha256"
            ;"/v"
            (str msi-file))))))


(defn- exe-heat [binaries-dir wix-dir Dir]
  (apply sh
         [(str binaries-dir "heat.exe")
          "dir" (str "target\\" (.toLowerCase Dir))
          "-nologo" "-ag" "-srd" "-sreg"
          "-cg" (str Dir "Group")
          "-dr" (str Dir "Dir")
          "-var" (format "wix.%sDirSource" Dir)
          "-out" (str wix-dir "\\" Dir "Group.wxs")]))


(defn- exe-candle [binaries-dir wix-dir wxs-names]
  (apply sh
         (concat
           [(str binaries-dir "candle.exe")
            "-nologo"
            "-arch" "x64"
            "-out" (str wix-dir "\\")]
           (map #(format "%s\\%s.wxs" wix-dir %) wxs-names))))


(defn- exe-light [binaries-dir wix-dir windows-dir wix-Args obj-names msi-name]
  (apply sh
         (concat
           [(str binaries-dir "light.exe")
            "-nologo" "-spdb" "-sacl" "-sice:ICE61"
            "-ext" (str binaries-dir "WixUIExtension.dll")
            "-ext" (str binaries-dir "WixUtilExtension.dll")
            "-out" (str (cio/file windows-dir (str msi-name ".msi")))]
           (map #(format "-d%sDirSource=target\\%s" % (.toLowerCase %)) wix-Args)
           (map #(format "%s\\%s.wixobj" wix-dir %) obj-names))))


(defn- build-msi [version]
  (when-let [bdir (wix-binaries-dir)]
    (let [binaries-dir (str bdir "\\")

          strict-version  (strict version)

          wix-dir         (cio/file "target" "wix")
          windows-dir     (cio/file "target" "windows")
          George-version  (str "George-" version)

          bat-tmpl        (slurp (cio/file "src_windows" "tmpl" "George.bat"))
          bat-rendered    (render bat-tmpl {:version version})
          bat-file        (cio/file wix-dir "George.bat")

          bat-cli-tmpl         (slurp (cio/file "src_windows" "tmpl" "GeorgeCLI.bat"))
          bat-cli-rendered    (render bat-cli-tmpl {:version version})
          bat-cli-file        (cio/file wix-dir "GeorgeCLI.bat")

          wxs-tmpl        (slurp (cio/file "src_windows" "tmpl" "George.wxs"))
          wxs-rendered    (render wxs-tmpl {:version version :strict-version strict-version :bat-file bat-file :bat-cli-file bat-cli-file})
          wxs-file        (cio/file wix-dir (format "George-%s.wxs" version))]

      (g/clean wix-dir)
      (g/clean windows-dir)

      (.mkdirs wix-dir)
      (spit bat-file bat-rendered)
      (spit bat-cli-file bat-cli-rendered)
      (spit wxs-file wxs-rendered)
      (cio/copy (cio/file "src_windows" "custom" "MyInstallScopeDlg.wxs")
                (cio/file wix-dir "MyInstallScopeDlg.wxs"))

      (exe-heat binaries-dir wix-dir "Deployable")
      (exe-heat binaries-dir wix-dir "Jre")

      (exe-candle binaries-dir wix-dir
                  ["DeployableGroup" "JreGroup" "MyInstallScopeDlg" George-version])

      (.mkdirs windows-dir)

      (exe-light binaries-dir wix-dir windows-dir
                 ["Deployable" "Jre"]
                 ["DeployableGroup" "JreGroup" "MyInstallScopeDlg" George-version]
                 George-version)

      (let [msi-file (cio/file windows-dir (str George-version ".msi"))]
        (when (.exists msi-file)
          (sign-msi msi-file))))))


(defn build
  [project & [subtask & args]]
  (binding [g/*project* project]
    (build-msi (:version project))))