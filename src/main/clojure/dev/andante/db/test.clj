(ns dev.andante.db.test
    (:require
        [clojure.repl :refer [doc]]
        [george.java :as j]
        :reload
        [george.javafx :as fx]
        :reload)

    (:import [javafx.scene Node Scene]
             [javafx.scene.shape Rectangle]
             [javafx.scene.layout StackPane Pane]
             [javafx.scene.paint Color]
             [javafx.scene.text Text]))




;(defn clicked [node f]
;    (. node setOnMouseClicked (fx/event-handler (f))))





(defn ->tile [^Node content loc-x loc-y]
    (let [
             rectangle
             (doto (Rectangle. 48 48 Color/LIGHTSTEELBLUE))

            pane
             (doto (StackPane. (j/vargs-t Node rectangle content))
                 (.setBorder (fx/make-border Color/TRANSPARENT 2.))
                 (. setStyle "-fx-background-color: cornflowerblue;")
                 (. relocate loc-x loc-y))]


        pane))



(defn ->swirl [loc-x loc-y]
    (let [
            tile
            (doto (->tile (Text. "swirl") loc-x loc-y)
                (. setOnMouseClicked
                    (fx/event-handler-2 [_ event]
                        (. (. event getSource) setVisible false))))]


        tile))


(defn- scene []
    (let [
             pane
             (doto (Pane.)
                 (. setStyle "-fx-background-color: #eee;")
                 (. setPrefSize 200 200))

             swirl1
             (->swirl 100 80)

             swirl2
             (->swirl 200 120)]


        (-> pane .getChildren (. addAll (j/vargs swirl1 swirl2)))
        (Scene. pane 800 600)))





(defn -main [& args]
    (fx/later
        (doto (fx/stage)
            (. setTitle "DB test")
            (. sizeToScene)
            (. setScene (scene))
            (. centerOnScreen)
            (. toFront)
            (. show))))






;(println "WARNING: Running dev.andante.db.test/-main" (-main))