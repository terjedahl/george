;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george.core
  "Contains all task implementations as well as common task functionality."
  (:require
    [clojure
     [string :as cs]
     [pprint :refer [pprint]]]
    [clojure.java
     [io :as cio]
     [shell :refer [sh]]]
    [environ.core :refer [env]]
    [selmer.parser :refer [render]]
    ;; Leiningen
    [leiningen
     [clean :as lc]
     [run :as lr]
     [uberjar :as lu]]
    [leiningen.core
     [eval :as le]]
    ;; src_lein
    [leiningen.george.load-common]
    ;; src_common
    [common.george.config :as c]
    [common.george.util
     [time :refer [now-iso]]
     [cli :refer [*debug* debug info warn error exit]]
     [platform :as pl]
     [files :as f]
     [props :as up]])
  (:import
    [java.io File]))



;; bound in all tasks
(def ^:dynamic *project* nil)


;;;

(defn- java-home []
  (env :java-home))


(defn- java-version []
  (env :java-version))


(defn- java11? []
  (.startsWith (System/getProperty "java.version") "11"))


(defn- java11-message []
  (format "George requires Java 11. You are running on %s version %s.
  See docs/java11.md for more details." (System/getProperty "java.vm.name") (java-version)))


(defn- warn-java11 []
  (when-not (java11?)
    (warn (java11-message))))


(defn- assert-java11 []
  (when-not (java11?)
    (error (java11-message))))


(defn- asserted-project []
  (or *project* (error "'leiningen.george.core/*project*' is not bound!")))


(defn assert-project []
  (asserted-project)
  nil)


(defn- assert-not-windows []
  (when (pl/windows?)
    (error "This task will only run on *nix systems.")))


(defn- assert-aws []
  (when (-> (sh "which" "aws") :out empty?)
    (error "No command 'aws' found.  You need Amazon's aws tool installed to run this subtask.")))


(defn- asserted-file [f error-fstr]
    (if (.exists f) f (error (format error-fstr f))))


;;;;


(defn- rsc-dir []          (cio/file "src" "rsc"))
(defn- embed-file []       (cio/file (rsc-dir) c/PROP_NAME))

(defn- George []             (c/get-app (embed-file)))
(defn- George-version []     (str (George) "-" (:version *project*)))

(defn- uberjar-dir []        (cio/file "target" "uberjar"))
(defn- native-dir []         (cio/file "target" (pl/platform)))
(defn- site-dir []           (cio/file "target" "Site"))
(defn- site-platforms-dir [] (cio/file (site-dir) "apps" (George) "platforms"))

(defn- jar-dir []            (cio/file (native-dir) "jar"))
(defn- jre-dir []            (cio/file (native-dir) "jre"))

(defn- jar-name []           (format "%s-%s.jar" (George-version) (pl/platform)))
(defn- jar-file []           (cio/file (jar-dir) (jar-name)))

(defn- installer-name []     (if (pl/windows?)
                               (str (George-version) ".msi")
                               (str (George-version) ".pkg")))

(defn- installer-dir []      (cio/file (native-dir) "installer"))
(defn- installer-file []     (cio/file (installer-dir) (installer-name)))

(defn- installed-dir []      (c/installed-dir (George)))

(defn- icns-file []          (cio/file "src_macos" "rsc" "George.icns"))

(defn- asserted-jre-dir []
  (asserted-file (jre-dir) "Directory '%s' does not exist.\n  To build the JRE, do:  lein build jre"))


(defn asserted-site-dir []
  (asserted-file (site-dir) "Directory '%s' does not exist.\n  To build the Site, do:  lein build site"))


(defn- asserted-jar-dir []
  (asserted-file (jar-dir) "Directory '%s' does not exist.\n  To build the JAR, do:  lein build jar"))


(defn- asserted-jar-file []
  (asserted-file (jar-file) "File '%s' does not exist.\n  To build the JAR, do:  lein build jar"))


(defn- asserted-installed-dir []
  (asserted-file (installed-dir) "Dir '%s' does not exist.
  It is automatically create when George downloads a newer version from online, or if you do:  lein local install"))


(defn- ensured-site-platforms-dir []
  (f/ensured-dir (site-platforms-dir)))


(defn- splash-image []
  (-> *project* :build :splash-image))


(defn- splash-param []
  (str "-splash:" (splash-image)))

(defn- dock-params []
  (if (pl/macos?)
    (list (str "-Xdock:icon=" (icns-file)) (str "-Xdock:name=" (George)))
    '()))


(defn- graphic-param []
  "-Dprism.dirtyopts=false")


(defn- run
  "Does 'lein run -m <qualified-fn> & args'
  An easy way to run arbitrary functions within the project.
  Warning: This should only be used as a last call, as it call exit() at completion."
  ([qualified-fn]
   (run qualified-fn []))
  ([qualified-fn args]
   (apply lr/run (concat [*project* "-m" (str qualified-fn)] args))))


(defn- run-java-home-bin [cmd args]
  (let [exe (str (cio/file (java-home) "bin" (str cmd)))]
    ;(prn 'exe exe)
    (apply le/sh (cons exe args))))


(defn- run-jre-bin [cmd args]
  (let [exe (str (cio/file (asserted-jre-dir) "bin" (str cmd)))]
    (apply le/sh (cons exe args))))


(defn- clean [& [clean-target]]
  (lc/clean
    (if-let [t clean-target]
      (assoc *project* :clean-targets [(str t)])
      *project*)))


(defn javafx-modules []
  (->> *project* :modules :javafx (map name)))


(defn- jre-modules []
  (concat
    (->> *project* :modules :java (map name))
    (javafx-modules)))


(defn- asserted-fx-lib-dir []
  (let [p (pl/platform)
        path (get-in *project* [:modules :libs p])]
    (debug "JavaFX lib:" path)
    (asserted-file
      (cio/file path)
      (format "Directory '%%s' does not exist.
  Ensure you have a lib dir in place matching project.clj's [:modules :libs \"%s\"]" p))))


(defn- asserted-fx-mods-dir []
  (let [p (pl/platform)
        path (get-in *project* [:modules :mods p])]
    (debug "JavaFX mods:" path)
    (asserted-file
      (cio/file path)
      (format "Directory '%%s' does not exist.
  Ensure you have a jmod lib in place matching project.clj's [:modules :mods \"%s\"]" p))))


(defn- replace-with-jar-path [args]
  (map #(if (= ":jar" %) (str (asserted-jar-file)) %) args))


;; https://blog.codefx.org/java/five-command-line-options-hack-java-module-system
(defn- exports-opens []
  ;; https://github.com/FXMisc/RichTextFX/issues/776
  (let [exports ["javafx.graphics/com.sun.javafx.geom"
                 "javafx.graphics/com.sun.javafx.text"
                 "javafx.graphics/com.sun.javafx.scene.text"]
        opens   ["javafx.graphics/com.sun.javafx.text"
                 "javafx.graphics/javafx.scene.text"]]
    (flatten
      (list
        (map #(list "--add-exports" (str % "=ALL-UNNAMED")) exports)
        (map #(list "--add-opens" (str % "=ALL-UNNAMED")) opens)))))


;; https://jaxenter.com/jdk-9-replace-permit-illegal-access-134180.html
(defn- access []
  (if *debug* "--illegal-access=debug" "--illegal-access=warn"))
;"--illegal-access=permit"


(defn- replace-with-jar-params [args & [with-opens?]]
  (flatten
    (map #(if (= ":jar" %)
            (concat
              [(access)]
              (if with-opens? (exports-opens) [])
              (concat (dock-params) [(splash-param) (graphic-param) "-jar" (str (asserted-jar-file))]))
            %)
         args)))


(defn- module-args [& [with-opens?]]
  (let [lib-dir (asserted-fx-lib-dir)
        mods-str (->> (javafx-modules) (interpose ",") (apply str))]
    (debug "JavaFX mods:" mods-str)
    (concat
      [;"-verbose"
       "--module-path" (str lib-dir)
       (str "--add-modules=" mods-str)]
      (if with-opens? (exports-opens) []))))


(defn- proceed? [app]
  (if-not (#{"George-TEST" "George"} app)
    true
    (= "y" (do (printf "'app' is '%s'. Proceed? (y/n): " app) (flush) (read-line)))))


;;;; EMBED / PROPS


(defn- build-embed-props [version & {:strs [:app :uri :ts]}]
  (let [app- (or app (-> *project* :build :properties :app))]
    (when-not (proceed? app-)
      (exit))
    (let [p (assoc (c/default-props app- uri  ts)  :version version)
          f (embed-file)]
      (c/dump f p)
      (pprint p))))


(defn- build-jar-props [jar-f]
  (let [p (assoc (up/load (embed-file))
            :file     (c/get-file jar-f)
            :size     (c/get-size jar-f)
            :checksum (c/get-checksum jar-f))
        f  (cio/file (.getParentFile jar-f) c/PROP_NAME)]
    (c/dump f p)
    (pprint p)))



(defn- build-installer-props [installer-file]
  (let [p {:version (:version *project*)
           :file    (f/filename installer-file)
           :size    (f/size installer-file)
           :ts      (now-iso)}
        f (cio/file (.getParent installer-file) c/PROP_NAME)]
    (up/dump f p)
    (pprint p)))


;;; JAR


(defn- ^File uberjar-file
  "Returns the file representing the built uberjar, else nil"
  []
  (when-let  [name
              (->> (-> (uberjar-dir) cio/file .list seq)
                   (filter #(.contains % "standalone"))
                   first)]
    (cio/file (uberjar-dir) name)))


;;;; SIGN


(defn- ^File warned-signtool-dir
  "Returns the signtool dir, else nil"
  []
  (if-let [d (->> env  :user-dir  cio/file  .listFiles  seq  (filter #(re-matches #".*signtool" (str %)))  first)]
    d
    (warn "Could not find directory 'signtool' in project dir.
To sign, download Microsoft's 'signtool' from  http://cdn1.ksoftware.net/signtool_8.1.zip
and unpack in project dir.")))


(defn- ^File warned-pfx-file
  "Returns the pfx-file, else nil"
  []
  (let [f (cio/file "codesign-key.pfx")]
    (if (.exists f)
      f
      (warn (format "Could not find file '%s' in project dir." f)))))


(defn- ^File  asserted-password-file
  "Returns the password-file - containing a plaintext password for the pfx-file, else nil"
  []
  (let [f (cio/file "codesign-password.txt")]
    (if (.exists f)
      f
      (error (format "Could not find file '%s' in project dir." f)))))


(defn- sign-msi [msi-file]
  (when-let [st-d (warned-signtool-dir)]
    (when-let [pfx-f (warned-pfx-file)]
      (let [pw-f (asserted-password-file)]
        (le/sh (str (cio/file st-d "signtool.exe"))
            "sign"
            "/d" (-> (.getName msi-file) (cs/split #".") first (#(format "%s Installer" %)))
            "/f" (str pfx-f)
            "/p" (.trim (slurp pw-f))
            "/tr" "http://timestamp.comodoca.com/rfc3161"
            "/fd" "sha256"
            ;"/v"
            (str msi-file))))))


;;;; MSI


(defn- ^File asserted-wix-binaries-dir
  "Returns the wix3xx-binaries dir, else nil"
  []
  (if-let [d (->> env  :user-dir  cio/file  .listFiles  seq  (filter #(re-matches #".*wix311-binaries" (str %)))  first)]
    d
    (error "Could not find directory 'wix311-binaries' in project dir.
  Download latest 'wix311-binaries.zip' from  https://github.com/wixtoolset/wix3/releases
  and unpack in project dir.")))


(defn- strict
  "Converts project version to strict mm.mm.mm version"
  [version]
  (let [[maj min mic] (-> version (cs/split #"-") first (cs/split #"\."))
        maj           (subs maj 2)
        mic           (or mic "0")]
    (format "%s.%s.%s" maj min mic)))


(defn- exe-heat [binaries-dir wix-dir Dir]
  (apply le/sh
         [(str binaries-dir "heat.exe")
          "dir" (str (cio/file "target" (pl/platform) (.toLowerCase Dir)))
          "-nologo" "-ag" "-srd" "-sreg"
          "-cg" (str Dir "Group")
          "-dr" (str Dir "Dir")
          "-var" (format "wix.%sDirSource" Dir)
          "-out" (str wix-dir "\\" Dir "Group.wxs")]))


(defn- exe-candle [binaries-dir wix-dir wxs-names]
  (apply le/sh
         (concat
           [(str binaries-dir "candle.exe")
            "-nologo"
            "-arch" "x64"
            "-out" (str wix-dir "\\")]
           (map #(format "%s\\%s.wxs" wix-dir %) wxs-names))))


(defn- exe-light [binaries-dir wix-dir wix-Args obj-names msi-f]
  (apply le/sh
         (concat
           [(str binaries-dir "light.exe")
            "-nologo" "-spdb" "-sacl" "-sice:ICE61"
            "-ext" (str binaries-dir "WixUIExtension.dll")
            "-ext" (str binaries-dir "WixUtilExtension.dll")
            "-out" (str msi-f)]
           (map #(format "-d%sDirSource=target\\%s\\%s" % (pl/platform) (.toLowerCase %)) wix-Args)
           (map #(format "%s\\%s.wixobj" wix-dir %) obj-names))))


(defn- msi-upgrade-code []
  (let [codes (-> *project* :build :msi-upgrade-codes)]
    (codes (George) (codes :default))))


(defn- build-msi []
  (asserted-jre-dir)
  (asserted-jar-file)
  (let [binaries-dir     (str (asserted-wix-binaries-dir) "\\")

        wix-dir          (cio/file "target" "wix")

        tmpl-data        {:app          (George)
                          :jar-name     (jar-name)
                          :upgrade-code (msi-upgrade-code)
                          :splash-image (splash-image)}

        bat-tmpl         (slurp (cio/file "src_windows" "tmpl" "George.bat"))
        bat-rendered     (render bat-tmpl tmpl-data)
        bat-file         (cio/file wix-dir "George.bat")

        bat-cli-tmpl     (slurp (cio/file "src_windows" "tmpl" "GeorgeCLI.bat"))
        bat-cli-rendered (render bat-cli-tmpl tmpl-data)
        bat-cli-file     (cio/file wix-dir "GeorgeCLI.bat")

        wxs-tmpl         (slurp (cio/file "src_windows" "tmpl" "George.wxs"))
        wxs-rendered     (render wxs-tmpl (merge tmpl-data
                                                 {:app            (George)
                                                  :version        (:version *project*)
                                                  :strict-version (strict (:version *project*))
                                                  :bat-file       bat-file
                                                  :bat-cli-file   bat-cli-file}))
        wxs-file         (cio/file wix-dir (str (George-version) ".wxs"))

        msi-file         (installer-file)]

    (clean wix-dir)
    (clean (installer-dir))

    (.mkdirs wix-dir)

    (spit bat-file bat-rendered)
    (spit bat-cli-file bat-cli-rendered)
    (spit wxs-file wxs-rendered)

    (cio/copy (cio/file "src_windows" "custom" "ScopeDlg.wxs")
              (cio/file wix-dir "ScopeDlg.wxs"))

    (exe-heat binaries-dir wix-dir "Jar")
    (exe-heat binaries-dir wix-dir "Jre")

    (exe-candle binaries-dir wix-dir
                ["JarGroup" "JreGroup" "ScopeDlg" (George-version)])

    (.mkdirs (installer-dir))

    (exe-light binaries-dir wix-dir
               ["Jar" "Jre"]
               ["JarGroup" "JreGroup" "ScopeDlg" (George-version)]
               msi-file)

    (when (.exists msi-file)
      (sign-msi msi-file))

    (build-installer-props msi-file)

    (clean wix-dir)))


(defn- build-macos-app []
  (let [jre-dir       (asserted-jre-dir)
        jar-file      (asserted-jar-file)

        tmpl-data     {:jar-name        (jar-name)
                       :app             (George)
                       :ts              (now-iso)
                       :identifier (str "no.andante." (George))
                       :strict-version  (strict (:version *project*))
                       :splash-image (splash-image)}

        pkg-dir       (cio/file "target" "pkg")
        appl-dir      (cio/file pkg-dir "root" "Applications")
        the-app       (cio/file appl-dir (str (George) ".app"))

        contents-dir  (cio/file the-app "Contents")
        resources-dir (cio/file contents-dir "Resources")

        info-tmpl     (slurp (cio/file "src_macos" "tmpl" "Info.plist"))
        info-rendered (render info-tmpl tmpl-data)
        info-file     (cio/file  contents-dir "Info.plist")

        sh-tmpl       (slurp (cio/file "src_macos" "tmpl" "George.sh"))
        sh-rendered   (render sh-tmpl tmpl-data)
        sh-file       (cio/file  contents-dir "MacOS" (George))]

    (clean pkg-dir)

    (f/ensured-dir (.getParentFile sh-file))
    (spit sh-file sh-rendered)
    (le/sh "chmod" "755" (str sh-file))

    (spit info-file info-rendered)

    (cio/copy (icns-file)
              (cio/file (f/ensured-dir resources-dir) "George.icns"))

    (cio/copy (cio/file (splash-image))
              (cio/file resources-dir (splash-image)))

    (cio/copy jar-file
              (cio/file (f/ensured-dir (cio/file contents-dir "jar")) (.getName jar-file)))
    (le/sh "cp" "-a" (str jre-dir) (str (cio/file contents-dir "jre")))  ;; ensures correct "chmod"

    (if-let [ cert-id (env :apple-developer-application-cert-id)]
      (le/sh "codesign" "--verbose" "--sign" cert-id (str the-app))
      (warn "Environment variable 'apple-developer-application-cert-id' not found."))

    pkg-dir))


(defn- build-pkg []
  ;; https://stackoverflow.com/questions/11487596/making-os-x-installer-packages-like-a-pro-xcode-developer-id-ready-pkg
  (let [pkg-dir         (build-macos-app)
        scripts-dir     (cio/file pkg-dir "scripts")

        pkg-file        (cio/file pkg-dir (installer-name))
        product-file    (installer-file)

        version         (:version *project*)
        strict-version  (strict version)
        identifier      (str "no.andante." (George))

        tmpl-data       {:app           (George)
                         :version       version
                         :stict-version strict-version
                         :pkg-name      (installer-name)
                         :identifier    identifier}

        plist-tmpl      (slurp (cio/file "src_macos" "tmpl" "Pkg.plist"))
        plist-rendered  (render plist-tmpl tmpl-data)
        plist-file      (cio/file  pkg-dir "Pkg.plist")

        launch-tmpl     (slurp (cio/file "src_macos" "tmpl" "launch.sh"))
        launch-rendered (render launch-tmpl tmpl-data)
        launch-file     (cio/file  scripts-dir "launch.sh")

        xml-tmpl        (slurp (cio/file "src_macos" "tmpl" "Distribution.xml"))
        xml-rendered    (render xml-tmpl tmpl-data)
        xml-file        (cio/file  pkg-dir "Distribution.xml")]

    (clean (installer-dir))
    (f/ensured-dir (installer-dir))

    (spit plist-file plist-rendered)
    (spit xml-file xml-rendered)

    (f/ensured-dir scripts-dir)
    (spit launch-file launch-rendered)
    (le/sh "chmod" "755" (str launch-file))

    (le/sh "pkgbuild"
           "--root" (str (cio/file pkg-dir "root"))
           "--component-plist" (str plist-file)
           "--scripts" (str scripts-dir)
           "--identifier" identifier
           "--version"  strict-version
           "--install-location" "/"
           (str pkg-file))

    (le/sh "productbuild"
           "--distribution" (str xml-file)
           "--package-path" (str pkg-dir)
           "--resources"    (str (cio/file "src_macos" "rsc"))
           "--identifier" identifier
           "--version" strict-version
           (str product-file))

    (if-let [cert-id (env :apple-developer-installer-cert-id)]
      (let [signed-file-s (str product-file ".signed")]
        (le/sh "productsign" "--sign" cert-id (str product-file) signed-file-s)
        (le/sh "mv" signed-file-s (str product-file)))
      (warn "Environment variable 'apple-developer-installer-cert-id' not found."))

    (le/sh "chmod" "755" (str product-file))
    (build-installer-props product-file)

    (clean pkg-dir)))


;;;; IMPLEMENTATIONS


(defn run-java  [args]
  (if ((set args) ":jar") (assert-java11) (warn-java11))
  (run-java-home-bin 'java (concat (module-args) (replace-with-jar-params args true))))


(defn run-jre  [args]
    (run-jre-bin 'java (replace-with-jar-params args)))


(defn run-jmod [args]
  (assert-java11)
  (run-java-home-bin 'jmod args))


(defn run-jlink [args]
  (assert-java11)
  (run-java-home-bin 'jlink args))


(defn run-jdeps [args]
  (assert-java11)
  (run-java-home-bin 'jdeps (-> args replace-with-jar-path)))


(defn build-embed [args]
  (apply build-embed-props (cons (:version (asserted-project)) args)))


(defn build-jar [args]
  (assert-java11)
  (assert-project)
  (clean (uberjar-dir))
  (clean (jar-dir))
  (build-embed args)
  (debug ":javac-options" (:javac-options *project*))
  (lu/uberjar *project*)
  (-> (jar-dir) (f/ensured-dir) (f/cleaned-dir))
  (let [jar-f (jar-file)]
    (cio/copy (uberjar-file) jar-f)
    (build-jar-props jar-f)))


(defn build-jre []
  (assert-java11)
  (assert-project)
  (let [jre-d (jre-dir)
        mods-str (->> (jre-modules) (interpose ",") (apply str))]
    (debug "JRE mods:" mods-str)
    (clean jre-d)
    (run-jlink ["--module-path" (str (asserted-fx-mods-dir))
                "--output" (str jre-d)
                "--compress=2"
                "--no-header-files"
                "--add-modules" mods-str])
    (clean (cio/file jre-d "legal"))
    (run-jre ["--list-modules"])))


(defn build-installer []
  (if (pl/windows?)
    (build-msi)
    (build-pkg)))


(defn build-site []
  (let [site-d (ensured-site-platforms-dir)]
    (loop [platforms (pl/platforms) cnt 0]
      (if-let [platform (first platforms)]
        (let [platform-dir (cio/file "target" platform)]
          (if-not (.exists platform-dir)
            (recur (next platforms) cnt)
            (do
              (doseq [d-name ["jar" "installer"]]
                (let [source-d (cio/file platform-dir d-name)]
                  (when (.exists source-d)
                    (debug "  Copying to Site dir:" (str source-d))
                    (let [target-d (cio/file site-d platform d-name)]
                      (f/copy-dir source-d target-d)
                      (when (-> target-d .list seq count (> 2))
                           (warn "More than 2 files in:" (str target-d)))))))
              (recur (next platforms) (inc cnt)))))
        (when (zero? cnt)
          (error "No platform dirs copied to Site."))))))


(defn aws-invalidate []
  (le/sh "aws" "cloudfront" "create-invalidation" "--distribution-id" "E3QSHE6V41FUEZ" "--paths" "/*"  "--profile" "andante"))


(defn aws-deploy  []
  (assert-not-windows)
  (assert-aws)
  (let [site (asserted-site-dir)
        app (George)]
    (when-not (proceed? app)
      (exit))
    (info "Deploying Site Amazon S3 for ...")
    (.delete (cio/file site ".DS_Store"))
    (le/sh "aws" "s3" "cp" (str site) "s3://download.george.andante.no/" "--acl" "public-read" "--recursive" "--profile" "andante")

    (info "Invalidating CloudFront caches ...")
    (aws-invalidate)

    (info "Give it a second or two ...")
    (Thread/sleep 5000)

    (info "Verifying 'app.properties' ...\n")
    (doseq [p (butlast (pl/platforms))]
      (doseq [t ["jar" "installer"]]
        (try
          (println (slurp (format "https://download.george.andante.no/apps/%s/platforms/%s/%s/app.properties" app p t)))
          (catch Exception _ (warn (format "Not found: /apps/%s/platforms/%s/%s/app.properties" app p t))))))))


(defn installed-install []
  (f/copy-dir (asserted-jar-dir) (installed-dir)))


(defn installed-list []
  (let [local-d (asserted-installed-dir)]
    (println " " (str local-d) "")
    (doseq [name (.list local-d)]
      (println "   " (str name)))))


(defn installed-clean []
  (f/cleaned-dir (installed-dir)))


(defn inject-javafx-modules []
  (warn-java11)
  (-> *project*
      (update-in [:javac-options] concat (module-args))
      (update-in [:jvm-opts] concat
                 (let [args (module-args true)]
                   ( if (:with-splash *project*)
                     (concat [(splash-param)] (dock-params) args)
                     args)))))
