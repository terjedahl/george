;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.application.environment
  (:require
    [george.javafx :as fx]
    [george.util.singleton :as singleton]

    [george.turtle.help :as help]

    [george.application
      [output :as output]
      [input :as input]
      [files-editors :as f-eds]]

    [george.application.ui
       [layout :as layout]
       [styled :as styled]])

  (:import
    [javafx.scene Node]
    [javafx.scene.control SplitPane]
    [javafx.geometry Orientation]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def ide-types #{:ide :turtle})


(defn- toolbar-pane [turtle?]
  (fx/hbox
    (styled/new-heading "Turtle Geometry IDE" :size 20)
    (fx/region :hgrow :always)
    (styled/new-link "Turtle API" #(help/turtle-API-stage))
    :spacing 10
    :padding 10))


(defn output-input-root [& {:keys [ns]}]
  (let [[o-root clear-button] (output/output-root)
        inputs-root (input/new-tabbed-input-root :ns ns)
        split-pane
        (doto (SplitPane. (into-array Node (list o-root inputs-root)))
          (.setOrientation Orientation/VERTICAL))]

    (.fire clear-button)

    split-pane))


(defn- main-root
  "A horizontal split-view dividing the main area in two."
  [ide-type]
  (let [
        user-ns-str
        (if (= ide-type :turtle) "user.turtle" "user")

        left
        (f-eds/main-root)

        oi-root ^SplitPane
        (doto
          (output-input-root :ns user-ns-str)
          (.setStyle "-fx-box-border: transparent;"))

        root
        (doto
          (SplitPane. (into-array Node (list left oi-root)))
          (.setDividerPosition 0 0.6)
          (.setStyle "-fx-box-border: transparent;"))]

    ;; TODO: Implement general ratio function for height of total height
    ;; TODO: Calculate height based on application stage or passed-in dim.
    (.setDividerPosition oi-root 0 0.7)
    ;; TODO: Ensure input gets focus!
    ;; TODO: Nicer SplitPane dividers!  See
    root))


(defn- ide-root-create [ide-type]
  (assert (ide-types ide-type))
  (let [[root master-setter detail-setter] (layout/master-detail true)]
    (master-setter (doto (toolbar-pane ide-type)
                         (.setBorder (styled/new-border [0 0 1 0]))))
    (detail-setter (main-root ide-type))
    (doto root (.setBorder (styled/new-border [0 0 0 1])))))


(defn ide-root [ide-type]
  (singleton/get-or-create [::ide-root ide-type]
                           #(ide-root-create ide-type)))


(defn ide-root-dispose [ide-type]
  (assert (ide-types ide-type))
  (singleton/remove [::ide-root ide-type]))
