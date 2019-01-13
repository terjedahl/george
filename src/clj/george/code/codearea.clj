;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.code.codearea
  (:import
    [org.fxmisc.richtext StyleClassedTextArea]
    [javafx.beans.property SimpleObjectProperty]))

;(set! *warn-on-reflection* true)


(definterface IErrorLines
  (errorlines ^SimpleObjectProperty []))


;; Similar to CodeArea, but with some different settings - including my own CSS
(defn new-codearea []
 (let [lines (SimpleObjectProperty. #{})]
   (doto
     (proxy [StyleClassedTextArea IErrorLines] [false]
       (errorlines [] lines))
     (-> .getStylesheets (.add "styles/codearea.css"))
     (-> .getStyleClass (.add "codearea"))
     (.setStyle "-fx-font-size: 16;")
     (.setUseInitialStyleForInsertion true))));


(defn ^String text [^StyleClassedTextArea codearea]
    (.getText codearea  0 (.getLength codearea)))


(defn set-text [^StyleClassedTextArea codearea ^String text]
    (.replaceText codearea text))
