;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns labs.overlayed
  (:require
    [george.javafx :as fx])
  (:import
    [javafx.scene.layout StackPane]
    [javafx.scene Node]
    [javafx.scene.control TextField]))


(defn- the-root []
  (let [some-text
        "Hello Word!
This is a printout.
It covers a textfield and a button.
Bla bla bla bla bla
asølkdfja sfd"

        a-button
        (doto
          (fx/button "Click" :onaction #(println "Click"))
          ;(.setFont (fx/SourceCodePro "BOLD" 20)))
          (fx/set-font "Source Code Pro Medium" 20))

        a-textfield
        (doto (TextField. "")
          ;(.setFont(fx/SourceCodePro "BOLD" 20))
          (fx/set-font "Source Code Pro Medium" 20))

        a-textarea
        (doto
          (fx/textarea :text some-text
                       :font (fx/new-font "Source Code Pro" 14)) ;(fx/SourceCodePro "Regular" 14))
          (.setStyle "my-text-area-background: transparent;")
          (.setMouseTransparent true))

        pane
        (StackPane.
          (into-array Node (list
                             (fx/vbox (fx/new-label "") a-textfield a-button)
                             a-textarea)))]
    pane))


(defn- the-stage []
  (let [
        root (the-root)
        scene (doto
                (fx/scene root)
                (fx/add-stylesheet "styles/textarea.css"))

        stage
        (fx/now
          (fx/stage
            :title "Overlay test"
            :scene scene))]

    stage))


(defn -main [& _]
  (the-stage))


;;; DEV ;;;

;(println "Warning: running george.core.overlayed/-main")  (-main)