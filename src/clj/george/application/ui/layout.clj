;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.ui.layout
  (:require
    [george.javafx :as fx]
    [george.application.ui.styled :as styled])
  (:import
    [javafx.scene Node]
    [javafx.scene.control SeparatorMenuItem MenuItem MenuButton]))


(defn- nil-or-node? [n] (or (nil? n) (instance? Node n)))


(defn master-detail
  "Returns 3-item vector: [layout-root set-master-fn set-detail-fn]"
  [& [vertical?]]
  (let [detail-pane
        (fx/borderpane)
        root
        (fx/borderpane :center detail-pane)
        master-setter
        #(when (nil-or-node? %)
               (if vertical? (.setTop root %) (.setLeft root %)))
        detail-setter
        #(when (nil-or-node? %) (.setCenter detail-pane %))]
    [root master-setter detail-setter]))


(defn menu
  "[:button label side children]
  [:separator]
  [:item label action]"
  [root]
  (let [typ (first root)]
    (condp = typ
      :separator (SeparatorMenuItem.)
      :item      (let [[_ label action] root] (doto (MenuItem. label) (fx/set-onaction action)))
      :button    (let [[_ label side children] root]
                   (doto (MenuButton. label  nil (into-array (map menu (filter some? children))))
                         (.setStyle "-fx-box-border: -fx-text-box-border;")
                         (.setPopupSide (fx/side side))
                         (.setFocusTraversable false))))))


(defn menubar [top? & items]
  (doto
    (apply fx/hbox
           (concat items
                   [:spacing 3
                    :insets (if top? [0 0 5 0] [5 0 0 0])
                    :padding 5
                    :alignment fx/Pos_TOP_LEFT]))
                    ;:background fx/GREEN]))
    (.setBorder (styled/new-border (if top? [0 0 1 0] [1 0 0 0])))))
