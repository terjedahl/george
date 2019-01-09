;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.george.core
  "Contains all task implementations as well as common task functionality."
  (:require
    [clojure.string :as cs]
    [clojure.java.io :as cio]
    [clojure.java.shell :refer [sh]]
    [environ.core :refer [env]]
    [selmer.parser :refer [render]]
    ;; Leiningen
    [leiningen.clean :as lc]
    [leiningen.run :as lr]
    [leiningen.classpath :as lcp]
    [leiningen.uberjar :as lu]
    [leiningen.core.eval :as le]
    ;; src_common
    [leiningen.george.load-common]
    [common.george.launch.config :as c]
    [common.george.files :as f]
    [common.george.util.text :as t]
    [common.george.launch.properties :as p])
  (:import
    [java.io File]))



;; bound in all tasks
(def ^:dynamic *project* nil)


;;;; flags

;; allow debug print?
(def ^:dynamic *debug*  (System/getenv "DEBUG"))
;; allow info print?
(def ^:dynamic *info*   (not (or (System/getenv "LEIN_SILENT") (System/getenv "GEORGE_SILENT"))))
;; allow JPMS tasks?
(def ^:dynamic *jpms*   (System/getenv "JPMS"))


;; TODO: move to common-namespace
(defn errln [& args]
  (binding [*out* *err*]
    (apply println args)))


(defn exit [& [code]]
  (System/exit (or code 0)))


(defn debug [& args]
  (when *debug*
    (apply println args)))


(defn info [& args]
  (when *info* (apply println args)))


(defn warn
  "Prints to stderr if not LEIN_SILENT or GEORGE_SILENT"
  [& args]
  (when *info*
    (apply errln args)))


(defn error
  "Prints to stderr, THEN EXITS! with -1."
  [& args]
  (apply errln args)
  (exit -1))

;;;

(defn- java-home []
  (System/getenv "JAVA_HOME"))


(defn- java-version []
  (System/getProperty "java.version"))


(defn- java11? []
  (.startsWith (System/getProperty "java.version") "11"))


(defn- java11-message []
  (format "George requires Java 11. You are running on %s version %s.
  See docs/java11.md for more details." (System/getProperty "java.vm.name") (java-version)))


(defn- warn-java11 []
  (when-not (java11?)
    (warn (str "Warning: " (java11-message)))))


(defn- assert-java11 []
  (when-not (java11?)
    (error (str "Error: " (java11-message)))))


(defn assert-jpms-active []
  (when-not *jpms*
    (error (str "Error: JPMS functionality is not active.
  See docs/java11.md for more details."))))


(defn- asserted-project []
  (or *project* (error "'leiningen.george.core/*project*' is not bound!")))


(defn assert-project []
  (asserted-project)
  nil)


(defn- assert-not-windows []
  (when (c/windows?)
    (error "Error: This task will only run on *nix systems.")))


(defn- assert-aws []
  (when (-> (sh "which" "aws") :out empty?)
    (error "Error: No command 'aws' found.  You need Amazon's aws tool installed to run this subtask.")))


(defn- asserted-file [f error-fstr]
    (if (.exists f) f (error (format error-fstr f))))


;;;;


(defn- rsc-dir []        (cio/file "src" "rsc"))
(defn- embed-file []     (cio/file (rsc-dir) p/PROP_N))

(defn- George []         (p/get-app (embed-file)))
(defn- George-version [] (str (George) "-" (:version *project*)))

(defn- uberjar-dir []    (cio/file "target" "uberjar"))
(defn- native-dir []     (cio/file "target" (c/platform)))
(defn- site-dir []       (cio/file "target" "Site"))
(defn- site-platforms-dir []  (cio/file (site-dir) "apps" (George) "platforms"))

(defn- jar-dir []        (cio/file (native-dir) "jar"))
(defn- jpms-dir []       (cio/file (native-dir) "jpms"))
(defn- jre-dir []        (cio/file (native-dir) "jre"))

(defn- jar-name []       (format "%s-%s.jar" (George-version) (c/platform)))
(defn- jar-file []       (cio/file (jar-dir) (jar-name)))
(defn- jpms-file []      (cio/file (jpms-dir) (jar-name)))

(defn- installer-name [] (if (c/windows?)
                           (str (George-version) ".msi")
                           (str (George-version) ".pkg")))

(defn- installer-dir []  (cio/file (native-dir) "installer"))
(defn- installer-file [] (cio/file (installer-dir) (installer-name)))

(defn- local-dir []      (c/install-dir (George)))


(defn- asserted-jre-dir []
  (asserted-file (jre-dir) "Error: Directory '%s' does not exist.\n  To build the JRE, do:  lein build jre"))


(defn asserted-site-dir []
  (asserted-file (site-dir) "Error: Directory '%s' does not exist.\n  To build the Site, do:  lein build site"))


(defn- asserted-jar-dir []
  (asserted-file (jar-dir) "Error: Directory '%s' does not exist.\n  To build the JAR, do:  lein build jar"))


(defn- asserted-jar-file []
  (asserted-file (jar-file) "Error: File '%s' does not exist.\n  To build the JAR, do:  lein build jar"))


(defn- asserted-jpms-file []
  (asserted-file (jpms-file) "Error: File '%s' does not exist.\n  To build the JPMS, do:  lein build jpms"))


(defn- asserted-installer-file []
  (asserted-file (installer-file) (format "Error: File '%s' does not exist.\n  To build the installer, do:  lein build installer")))


(defn- asserted-local-dir []
  (asserted-file (local-dir) "Error: Dir '%s' does not exist.
  It is automatically create when George downloads a newer version from online, or if you do:  lein local install"))


(defn- ensured-site-platforms-dir []
  (f/ensure-dir (site-platforms-dir)))


(defn- copy-file-to-dir [dir file & [verbose?]]
  (let [f0 (.getAbsoluteFile (cio/file file))
        f1 (cio/file dir (.getName f0))]
    (when verbose?
      (println (format "Copying file: %s\n          to: %s" (str f0) (str f1))))
    (cio/copy f0 f1)
    f1))


(defn- copy-files-to-dir [dir files & [verbose?]]
  (mapv #(copy-file-to-dir dir % verbose?) files))


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


(defn- now
  "Prints ISO-timestamp"
  []
  (p/print-now))


(defn- clean [& [clean-target]]
  (lc/clean
    (if-let [t clean-target]
      (assoc *project* :clean-targets [(str t)])
      *project*)))


(defn- ^File ensured-dir [dir]
  (let [d (cio/file dir)]
    (when-not (.exists d) (.mkdirs d))
    d))


(defn- modules [& [jpms?]]
  (concat
    (-> *project* :modules :jre)
    (if jpms? (-> *project* :modules :jpms) [])))


;;;; EMBED / PROPS


(defn- build-embed- [version & {:strs [:app :uri :ts]}]
  (let [props (assoc (p/default app uri ts) :version version)]
    (p/dump  (embed-file) props)
    (info (t/pformat props))))


(defn- build-prop- [jar-f]
  (let [p (assoc (p/load (embed-file))
               :file     (p/get-file jar-f)
               :size     (p/get-size jar-f)
               :checksum (p/get-checksum jar-f))]
     (info (t/pformat p))
     (p/dump (cio/file (.getParentFile jar-f) p/PROP_N) p)))


;;; JAR

(defn- ^File uberjar-file
  "Returns the file representing the built uberjar, else nil"
  []
  (when-let  [name
              (->> (-> (uberjar-dir) cio/file .list seq)
                   (filter #(.contains % "standalone"))
                   first)]
    (cio/file (uberjar-dir) name)))


(defn- build-jar- [jpms?]
  (-> (if jpms? (jpms-dir) (jar-dir)) (f/ensure-dir) (f/clean-dir))
  (let [jar-f (if jpms? (jpms-file) (jar-file))]
    (cio/copy (uberjar-file) jar-f)
    (build-prop- jar-f)))


(defn- write-module-info []
  (let [reqs (apply str  (map #(str "    requires " (name %) ";\n")  (modules true)))
        java (format "module george {\n    exports no.andante.george;\n%s}" reqs)]
    (println java)
    (spit "src/java/module-info.java" java)))


(defn- delete-module-info []
  (cio/delete-file (cio/file "src" "java" "module-info.java") true))


;;;; SIGN


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
    (error "Error: Could not find directory 'wix311-binaries' in project dir.
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
          "dir" (str (cio/file "target" (c/platform) (.toLowerCase Dir)))
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
           (map #(format "-d%sDirSource=target\\%s\\%s" % (c/platform) (.toLowerCase %)) wix-Args)
           (map #(format "%s\\%s.wixobj" wix-dir %) obj-names))))


(defn- msi-upgrade-code []
  (let [codes (-> *project* :build :msi-upgrade-codes)]
    (codes (George) (codes :default))))


(defn- build-msi []
  (asserted-jre-dir)
  (asserted-jar-file)
  (let [binaries-dir     (str (asserted-wix-binaries-dir) "\\")

        wix-dir          (cio/file "target" "wix")

        tmpl-data        {:jar-name     (jar-name)
                          :upgrade-code (msi-upgrade-code)}

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

    (spit
      (cio/file (.getParent msi-file) p/PROP_N)
      (->
        {:version (:version *project*)
         :file    (.getName msi-file)
         :size    (p/get-size msi-file)
         :ts      (p/now-ts)}
        p/map->properties
        p/properties->str))

    (when (.exists msi-file)
      (sign-msi msi-file))))


;;;; IMPLEMENTATIONS


(defn run-java  [args]
  (let [arg1 (first args)]
    (if (#{":jar" ":jpms"} arg1) (assert-java11) (warn-java11))
    (case arg1
      ":jar"
      (run-java-home-bin
        'java
        (concat [;; https://jaxenter.com/jdk-9-replace-permit-illegal-access-134180.html
                 ;"--illegal-access=debug"
                 "--illegal-access=warn"
                 ;"--illegal-access=permit"
                 "-jar" (str (asserted-jar-file))]
                (rest args)))
      ":jpms"
      (do
        (assert-jpms-active)
        (run-java-home-bin
          'java
          (concat [;"--module-path" "javafx-jmods-11.0.1"
                   ;"-jar" (str (asserted-jpms-file))]
                   "--module-path" (str (asserted-jpms-file))
                   "--module"  "george/no.andante.george.Launch"]
                  (rest args))))
      ;; default
      (run-java-home-bin 'java args))))


(defn run-jre  [args]
  (case (first args)
    ":jar"
    (run-jre-bin
      'java
      (concat [;; https://jaxenter.com/jdk-9-replace-permit-illegal-access-134180.html
               ;"--illegal-access=debug"
               "--illegal-access=warn"
               ;"--illegal-access=permit"
               "-jar" (str (asserted-jar-file))]
              (rest args)))
    ":jpms"
    (do
      (assert-jpms-active)
      (run-jre-bin
        'java
        (concat [;"--module-path" "javafx-jmods-11.0.1"
                 ;"-jar" (str (asserted-jpms-file))
                 "--module-path" (str (asserted-jpms-file))
                 "--module"  "george/no.andante.george.Launch"]
                (rest args))))
    ;; default
    (run-jre-bin 'java args)))


(defn run-jmod [args]
  (assert-java11)
  (run-java-home-bin 'jmod args))


(defn run-jlink [args]
  (assert-java11)
  (run-java-home-bin 'jlink args))


(defn- replace-jar [args]
  (map #(if (= ":jar" %) (str (asserted-jar-file)) %) args))


(defn- replace-jpms [args]
  (map
    #(if (= ":jpms" %)
       (do
         (assert-jpms-active)
         (str (asserted-jpms-file)))
       %)
    args))


(defn run-jdeps [args]
  (assert-java11)
  (run-java-home-bin 'jdeps (-> args replace-jar replace-jpms)))


(defn build-embed [args]
  (apply build-embed- (cons (:version (asserted-project)) args)))


(defn build-jar
  [args & [jpms?]]
  (delete-module-info)  ;; Ensure that it isn't there from a failed build-jpms
  (assert-project)
  (clean (uberjar-dir))
  (clean (jar-dir))
  (build-embed args)
  (println ":javac-options" (:javac-options *project*))
  (lu/uberjar *project*)
  (build-jar- jpms?))


(defn build-jpms [args]
  (assert-jpms-active)
  (write-module-info)
  (binding [*project*
            (update-in *project* [:javac-options] conj
                       "-verbose"
                       "--module-path"
                       "javafx-jmods-11.0.1"
                       ;(str (cio/file "javafx-sdk-11.0.1" "lib"))
                       ;(lcp/get-classpath-string *project*)
                       "--add-modules=javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing,javafx.web,javafx.media")]
                       ;"--add-exports" "george/no.andante.george=A‌​LL-UNNAMED")]

    (build-jar args true)
    (delete-module-info)))


(defn build-jre []
  (assert-java11)
  (assert-project)
  (let [jre-d (jre-dir)
        modules-str (apply str (interpose "," (map name (modules))))]
    ;(prn modules-str)
    (clean jre-d)
    (run-jlink [;"--module-path" "javafx-jmods-11.0.1" ;(str (cio/file "javafx-sdk-11.0.1" "lib"))
                "--output" (str jre-d)
                "--compress=2"
                "--no-header-files"
                "--add-modules" modules-str])
    (clean (cio/file jre-d "legal"))
    (run-jre ["--list-modules"])))


(defn build-installer []
  (if (c/windows?)
    (build-msi)
    (error "Error: build-pkg NOT IMPLEMENTED")))


(defn build-site []
  (let [site-d (ensured-site-platforms-dir)]
    (loop [platforms (c/platforms) cnt 0]
      (if-let [platform (first platforms)]
        (let [platform-dir (cio/file "target" platform)]
          (if-not (.exists platform-dir)
            (recur (next platforms) cnt)
            (do
              (doseq [d-name ["jar" "installer"]]
                (let [d (cio/file platform-dir d-name)]
                  (when (.exists d)
                    (debug "  Copying to Site dir:" (str d))
                    (f/copy-dir d (cio/file site-d platform d-name)))))
              (recur (next platforms) (inc cnt)))))
        (when (zero? cnt)
          (error "Error: No platform dirs copied to Site."))))))


(defn push-aws-invalidate []
  (le/sh "aws" "cloudfront" "create-invalidation" "--distribution-id" "E3QSHE6V41FUEZ" "--paths" "/*"))


(defn push-aws []
  (assert-not-windows)
  (assert-aws)
  (let [site (asserted-site-dir)
        app (George)]
    (info "Deploying Site Amazon S3 for ...")
    (.delete (cio/file site ".DS_Store"))
    ;(cio/copy (cio/file "src_lein" "leiningen" "george" "rsc" "aws-s3-bucket-listing.html") (cio/file site "index.html"))
    (le/sh "aws" "s3" "cp" (str site) "s3://download.george.andante.no/" "--acl" "public-read" "--recursive" "--region" "eu-central-1")

    (info "Invalidating CloudFront caches ...")
    (push-aws-invalidate)

    (info "Give it a second or two ...")
    (Thread/sleep 5000)

    (info "Verifying 'app.properties' ...\n")
    (doseq [p (c/platforms)]
      (doseq [t ["jar" "installer"]]
        (try
          (info (slurp (format "https://download.george.andante.no/apps/%s/platforms/%s/%s/app.properties" app p t)))
          (catch Exception _ (warn (format "Warning: Not found: /apps/%s/platforms/%s/%s/app.properties" app p t))))))))


(defn do-local-install []
  (f/copy-dir (asserted-jar-dir) (local-dir)))


(defn do-local-list []
  (let [local-d (asserted-local-dir)]
    (println " " (str local-d) "")
    (doseq [name (.list local-d)]
      (println "   " (str name)))))


(defn do-local-clean []
  (f/clean-dir (local-dir)))


;(defn push-scp [user-AT-host-COL-dir]
;  (assert-not-windows)
;  (let [site (asserted-site-dir)]
;    (info "Copying ...")
;    (le/sh "scp" "-r" "-v" (str site) user-AT-host-COL-dir)
;    (info "... done")))