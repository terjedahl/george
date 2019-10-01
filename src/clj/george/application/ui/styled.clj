;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.ui.styled
  (:require
    [george.javafx :as fx]
    [clojure.java.browse :refer [browse-url]]
    [common.george.util.cli :refer [debug repl?]])
  (:import
    [javafx.scene.paint Color]
    [javafx.stage Stage]
    [javafx.scene.image Image]
    [javafx.scene.control Hyperlink ProgressIndicator]
    [java.util List]))


(defn ns-label []
  (fx/new-label nil
                :style "-fx-font: 14 'Source Code Pro'; -fx-text-fill: gray; -fx-padding: 3;"))

(defn small-button [& args]
  (apply fx/new-button (concat args (list :style "-fx-font-size: 12;-fx-padding: 3 6;"))))


(defn new-heading [s & {:keys [size] :or {size 16}}]
  (fx/text s :size size :color fx/GREY))


(defn new-label [s & {:keys [size] :or {size 16}}]
  (fx/new-label s :size size :color fx/GREY :font (fx/new-font fx/ROBOTO size)))


(defn scrolling-widget [& [txt vertical?]]
  (fx/box vertical?
    (doto (ProgressIndicator.) (.setMaxHeight 28.))
    (new-label (or txt "Refreshing ..."))
    :padding 10
    :spacing 5
    :alignment fx/Pos_CENTER))


(defn padding [h & [w]]
  (fx/line :x2 (or w h) :y2 h :color Color/TRANSPARENT))


(defn hr [w]
  (fx/line :x2 w :width 1 :color Color/GAINSBORO))


(defn new-border [w-or-trbl]
  (fx/new-border Color/GAINSBORO w-or-trbl))


(defn add-icon [^Stage stage]
  (let [images (map #(Image. (str "graphics/George_icon_" % ".png"))
                    [16 32 64 128 256])]
    ;(doseq [i images] (debug "URL:" (.getUrl i))
    (fx/later (-> stage .getIcons (.setAll ^List images)))
    stage))


(defn skin-application [scene-or-parent & [refresh?]]
  (doto scene-or-parent
    (fx/add-stylesheet "styles/application.css" refresh? 2000)))


(defn style-stage [^Stage stage]
  (fx/now
    (doto stage
          add-icon
          (-> .getScene (skin-application repl?)))))


(def LINK_COLOR "#337ab7")


(defn new-link
  "A standard styled web-like link. The action can be a function or an event-handler."
  [text action]
  (doto (Hyperlink. text)
    (.setStyle (format "-fx-padding: 10 0 10 0; -fx-text-fill: %s;" LINK_COLOR))
    (fx/set-onaction action)))