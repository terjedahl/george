(ns george.launcher

    (require
        [clojure.repl :refer [doc]]
        [clojure.string :as s]

        [george.java :as j] :reload
        [george.javafx :as fx] :reload
        [george.javafx-classes :as fxc] :reload

        [george.input :as input] :reload
        [george.output :as output] :reload
        ))

(fx/init)
(fxc/import-classes)



(defn- launcher-scene []
    (let [
            output-button
            (doto
                (Button. "Output")
                (. setOnAction
                    (fx/event-handler
                    (output/show-output-stage))))

            input-button
            (doto
                (Button. "Input")
                (. setOnAction
                    (fx/event-handler
                        (. output-button fire)
                        (input/show-new-input-stage))))

            hbox
            (doto
                (HBox. 5.0 (j/vargs output-button input-button))
                (. setAlignment Pos/TOP_CENTER)
                (. setStyle "
                    -fx-padding: 5 5;
                "))

             scene
            (Scene. hbox 200 50)
         ]

        scene ))


(defn- launcher-close-handler []
    (fx/event-handler-2 [_ e]
        (if
            (= -1
                (fx/show-actions-dialog
                    "Quit confirmation"
                    nil
                    "Do you want to quit George? \n(TODO: handle open windows on exit!)"
                    ["Quit"]
                    true))
            (. e consume)
            ;; else TODO: maybe do some exit-actions ...?
        )))


(defn show-launcher-stage []
    (let [
             scene
             (launcher-scene)

             stage
             (doto (Stage.)
                 (. setScene scene)
                 (. sizeToScene)
                 (. setX (-> (Screen/getPrimary) .getVisualBounds .getWidth (/ 2) (- (/ (. scene getWidth) 2))))
                 (. setY 50)
                 (. setTitle "George")
                 (. show)
                 (. setAlwaysOnTop true)
                 (. setResizable false)
                 ;; TODO: prevent fullscreen.  Where does the window go after fullscreen?!?
                 (. setOnCloseRequest (launcher-close-handler))
                 )
             ]
        nil))




;;;; dev ;;;;


(defn -main
    "Launches George (launcher) as a stand-alone app."
    [& args]
    (println "george.input-stage/-main")
    (fx/dont-exit!)
    (fx/thread (show-launcher-stage)))


(-main)