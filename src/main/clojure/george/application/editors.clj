;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.editors
  ^{:doc
    "Creates/handles a pane contain multiple tabbed editors, 
    including supporting functionality for evaluation and file handling.
    (Not to be confused with 'george.editor.*')"}

  (:require
    [environ.core :refer [env]]
    [george.util.singleton :as singleton]
    [george.javafx :as fx]
    [george.application.ui.styled :as styled]
    [george.editor.core :as ed])

  (:import
    [javafx.scene.control ToggleGroup ToggleButton ScrollPane$ScrollBarPolicy ScrollPane ContentDisplay]
    (javafx.geometry Bounds)
    (javafx.scene.shape Circle)
    (javafx.scene.paint Color)
    (java.nio.file Path)))


(defonce editor-buttons_
         ^{:doc "Maps ^Path-s to editor-buttons"}
         (atom {}))


(defonce current-editor-group_
         ^{:doc "Holds map of relevant data for current-editor-group"}
         (atom nil))


(defn- editorbutton-action [editor container]
  (fx/later (.setCenter container editor)))


;; https://stackoverflow.com/questions/12837592/how-to-scroll-to-make-a-node-within-the-content-of-a-scrollpane-visible#12840519
(defn- ensure-scrolled-visible [^ScrollPane pane node]
  (let [spacing 10
        viewport-w (-> pane .getViewportBounds .getWidth)
        content (.getContent pane)
        content-w (->> content .getBoundsInLocal (.localToScene content) .getWidth)
        
        ^Bounds node-b-g (->> node .getBoundsInLocal (.localToScene node))
        node-min-x-g (- (.getMinX node-b-g) spacing)
        node-max-x-g (+ (.getMaxX node-b-g) spacing)
        
        delta 
        (if (< node-min-x-g  0) 
            ;(< node-max-x-g  0)  ;; scolls only if completely out of view
          (/ (- node-min-x-g viewport-w) content-w)
          (if (> node-max-x-g viewport-w) 
              ;(> node-min-x-g viewport-w)  ;; scolls only if completely out of view
            (/ (+ node-min-x-g viewport-w)  content-w)
            0))]

    (.setHvalue pane (+ (.getHvalue pane) delta))))


(definterface IEditorTab2
  (getPath [])
  (getEditor [])
  (rename [new_path])
  (close [allow_save]))
          


(defn- editor-tab 
  "editor-tab extends ToggleButton implments IEditorTab"
  [path togglegroup scrollpane container]
  
  ;(println "  /editor-tab")
  (let [x-circle
        (Circle. 8 8 8 Color/GAINSBORO)
        x-pane
        (fx/stackpane 
          x-circle 
          (fx/new-label "X" :size 10 :color Color/DIMGRAY :tooltip "Close editor"))
        
        path_ (atom path)
        
        fname
        (if (instance? Path path)
            (str (.getFileName path))
            path)
        
        content
        (if (instance? Path path)
            (slurp (.toFile path))
            path)
        
        editor
        (doto (ed/editor-view content :clj))
          
        button
        (proxy [ToggleButton IEditorTab2] [fname x-pane] 
          (fire []
            (when (or (not (.getToggleGroup this)) (not (.isSelected this)))
              (ensure-scrolled-visible  scrollpane this)
              (proxy-super fire)))
          (getPath [] @path_)
          (getEditor [] editor)
    
          (rename [new-path]
            (swap! editor-buttons_ assoc new-path this)
            (swap! editor-buttons_ dissoc @path_)
            (reset! path_ new-path)
            (let [fname 
                  (if (instance? Path new-path)
                      (str (.getFileName new-path))
                      new-path)]
              (.setText this fname)))
              
          (close [allow-save?]
            (println " Do some work before closing button (saving and such) ...")
            (let [box (.getParent this)]
              (fx/set-onaction this #(do))
              (.setCenter container (fx/new-label "No tab selected"))
              (-> togglegroup .getToggles (.remove this))
              (fx/remove box this)
              (swap! editor-buttons_ dissoc path))))]


    (swap! editor-buttons_ assoc path button)
    
    (doto x-pane
      (.setOnMouseExited (fx/event-handler (.setFill x-circle Color/GAINSBORO)))
      (.setOnMouseEntered (fx/event-handler (.setFill x-circle Color/ORANGERED)))
      (.setOnMousePressed  
        (fx/event-handler-2 
          [_ ev]
          (let [button (-> ev .getSource .getParent)]                  
            (.close button true)))))
    
    (doto button
      (.setToggleGroup togglegroup)
      (.setFocusTraversable false)
      (fx/set-onaction #(editorbutton-action editor container))
      (.setContentDisplay ContentDisplay/RIGHT)  
      (.setGraphicTextGap 14))))

  
(defn new-editors-root [& {:keys [ns with-one?]}]
  (let [
        togglegroup
        (ToggleGroup.)
      
        container 
        (fx/borderpane :center (fx/new-label "No tab selected"))
        
      
        buttonpane
        (fx/hbox :padding 10 :spacing 10)

        buttonscrollpane
        (doto (fx/scrollpane buttonpane)
          (.setHbarPolicy ScrollPane$ScrollBarPolicy/ALWAYS)  ;; TODO: Style away "arrows"
          (.setVbarPolicy ScrollPane$ScrollBarPolicy/NEVER))

        buttons
        (map #(editor-tab % togglegroup buttonscrollpane container)  (fx/names-list))


        root
        (fx/borderpane
          :top
          buttonscrollpane
          :center
          container)]

    (apply fx/add-all (cons buttonpane buttons))
    
    (reset! current-editor-group_ 
            {:buttonpane buttonpane
             :buttonscrollpane buttonscrollpane
             :togglegroup togglegroup
             :container container})
    
    root))


(defn- create-stage [& [with-one?]]
  (fx/now
    (->
      (fx/stage
        :title "Editor stage"
        :oncloserequest  
        #(do (singleton/remove ::editors-stage)
             (reset! current-editor-group_ nil)
             (reset! editor-buttons_ {}))
        :tofront true
        :alwaysontop true
        :sizetoscene false
        :scene (fx/scene (new-editors-root :with-one? with-one? :ns "user.turtle"))
        :size [600 400])
      styled/style-stage)))

(defn get-or-create-stage [& [with-one?]]
  (singleton/get-or-create ::editors-stage #(create-stage with-one?)))


;;; Service ;;;



(defn open-or-reveal
  "Opens content of file in editor if not already open, else reveals the editor displaying the file."
  [path]
  (println "g.a.editors/open-or-reveal" path)
  (if-let [button (@editor-buttons_ path)]
    (let []
      (println "editor found")
      (prn 'button button)
      (fx/later (.fire button)))
    
    (let [{:keys [togglegroup buttonpane buttonscrollpane container] :as bg} @current-editor-group_]
      (if-not bg
        (println "WARNING! No editors/@current-editor-group_")
        (let [button (editor-tab  path togglegroup buttonscrollpane container)]
          (fx/add buttonpane button)
          (future
            (Thread/sleep 200)
            (fx/later (.fire button))))))))
      

(defn rename [old-path new-path]
  (when-let [tab (@editor-buttons_ old-path)]
    (.rename tab new-path)))


(defn close [path save?]
  (when-let [tab (@editor-buttons_ path)]
    (.close tab save?)))


;;; DEV ;;;

; (when (env :repl?) (println "WARNING: Running george/get-or-create-stage" (get-or-create-stage true)))
