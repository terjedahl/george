(ns
    ^{:author "Terje Dahl"}
    george.turtle.environment
    (:require
        [clojure.java.io :refer [file] :as cio]
        [george.javafx.core :as fx]
        :reload
        [george.turtle.core :as tr]
        :reload
        [george.core.core :as gcc]
        :reload
        [george.util.singleton :as singleton]
        :reload
        [george.util.prefs :as prf]
        :reload
        [george.editor :as editor])
    (:import (java.util.prefs Preferences)
             (java.io File)))


(defonce ^Preferences USER_PREFS (prf/user-node "no.andante.george.turtle"))
(defonce ^String USER_HOME_STR (System/getProperty "user.home"))
(defonce ^File DEFAULT_LIBRARY (file USER_HOME_STR "George" "Turtle" "Library"))


(defn- get-library []
    (file (prf/get USER_PREFS ::turtle-library-path DEFAULT_LIBRARY)))

(defn- put-library [p]
    (prf/put USER_PREFS ::turtle-library-path p))



(defn- poll-library [lib-dir listview]
    (println "poll library ...")
    (let [
          files (file-seq lib-dir)
          files (.listFiles (file (System/getProperty "user.home")))
          files (.listFiles lib-dir)]


;        (doseq [f files] (println "f:"f))
        (.setItems listview (apply fx/observablearraylist files))))



(defn- library-pane []
    (let [
          lib-dir (get-library)

          listview
          (doto (fx/listview)
              (.setPlaceholder (fx/text "(no files found)")))

          refresh-button
          (fx/button "R"
                     :onaction #(poll-library lib-dir listview)
                     :tooltip "Refresh list of files")


          top(fx/hbox
                   (fx/label
                       (str lib-dir))
                   (fx/region :hgrow :always)
                   refresh-button
                   :spacing 3
                   :alignment fx/Pos_TOP_RIGHT
                   :insets [0 0 5 0])


          pane (fx/borderpane :top top :center listview :insets 5)]

        (.fire refresh-button)

        pane))



(defn- create-library-stage []
    (let []

        (fx/now
            (fx/stage
                :style :utility
                :location [70 80]
                :title "Turtle :: library"
                :scene (fx/scene (library-pane) :size [300 300])
                :sizetoscene true
                :onhidden #(singleton/remove ::library-stage)))))




(defn library-stage []
    (singleton/put-or-create ::library-stage create-library-stage))


(defn- prep-user-namespace []
    (let []


        "
        ;; prepair the user.turtle-namespace
        (ns user.turtle (:require [george.turtle.core :refer :all] :reload))
        ;; switch back to this namespace
        (ns george.turtle.environment)
        "
        (binding [*ns* nil]
            (ns user.turtle
                (:require [george.turtle.core :refer :all]
                          :reload))
            (ns george.turtle.environment))))




(defn- toolbar-pane []
    (prep-user-namespace)
    (let [button-width
          150]

        pane (fx/hbox
                 ;(fx/imageview "graphics/George_logo.png")

               (fx/button "Library"
                          :width button-width
                          :onaction #(library-stage)
                          :tooltip "Open/show the library navigator (your files)")


               (fx/button "Editor"
                          :width button-width
                          :onaction #(editor/new-code-stage :namespace "user.turtle")
                          :tooltip "Open a new code editor")

               (fx/button
                 "Input"
                 :width button-width
                 :onaction gcc/input-stage
                 :tooltip "Open a new input window / REPL")

               (fx/button
                 "Output"
                 :width button-width
                 :onaction gcc/show-or-create-output-stage
                 :tooltip "Open/show output-window")

               (fx/button "Commands"
                          :width button-width
                          :onaction #(println "missing IMPL (Commands)")
                          :tooltip "Open/show a panel with useful turtle commands")

               (fx/button "Screen"
                          :width button-width
                          :onaction #(tr/screen)
                          :tooltip "Open/show a new Turtle screen")

               :spacing 10
               :padding 10))



    pane)


(defn- create-toolbar-stage []
  (let []

    (fx/now
      (fx/stage
        :style :utility
        :ontop true
        :location [70 0]
        :title "Turtle :: toolbar"
        :scene (fx/scene (toolbar-pane))
        :sizetoscene true
        :onhidden #(singleton/remove ::toolbar-stage)))))



(defn toolbar-stage []
    (singleton/put-or-create ::toolbar-stage create-toolbar-stage))


;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone app."
  [& args]
  (fx/later (toolbar-stage)))


;;; DEV ;;;

;(println "WARNING: Running george.turtle.environment/-main" (-main)))
