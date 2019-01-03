;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.files-editors
  (:require
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.core.history :as hist]
    [george.editor.core :as ed]
    [george.application.editors :as eds]
    [george.files.filetree :as filetree]
    [george.application.ui.styled :as styled]
    [george.util.singleton :as singleton]
    [george.util.file :refer [ ->path ->file ->string exists? filename]])
  (:import
    [javafx.scene.control SplitPane ListCell ListView]
    [javafx.geometry Orientation]
    [javafx.scene Node]
    [javafx.scene.paint Color]
    [javafx.scene.shape Circle]
    [javafx.scene.input MouseEvent]
    [java.nio.file Path]
    [javafx.util Callback]
    [java.util List]
    [javafx.scene.layout BorderPane]))


(def listview_ (atom nil))
(def editor-pane_ (atom nil))


(defn- set-editor! [item]
  (.setCenter ^BorderPane @editor-pane_ (:editor-root item)))


(defn- set-open-files [items]
  (let [open-files
        (mapv #(-> % :file-info_ deref :path ->string) items)]
    ;(doseq [f open-files] (prn '-- f))
    (hist/set-open-files open-files)))


(defn rename-file! [^Path old ^Path new]
  (when-let [item (->> @listview_ .getItems (filter #(= old (-> % :file-info_ deref :path) )) first)]
    ;(prn 'rename-file item)
    (let [{:keys [file-info_]} item]
      (swap! file-info_ assoc :path new :swap-path nil :saved? true :saved-to-swap? true))
    (set-open-files (.getItems @listview_))
    true))


(defn close-file! [^Path path save?]
  ;(println "close-file" path save?)
  (let [lv @listview_
        item (->> lv .getItems (filter #(= path (-> % :file-info_ deref :path) )) first)
        {:keys [editor file-info_]} item]
    (when item
      (when save? 
        (eds/save editor file-info_))
      (doto lv
        (-> .getItems (.remove item))
        (-> .getSelectionModel .clearSelection))
      (set-editor! {:editor-root (fx/new-label "No file selected")})
      true)))    


(defn ^Node new-close-x [file-info_]
  (let [x-circle
        (Circle. 8 8 8 Color/GAINSBORO)
        x-pane
        (fx/stackpane
          x-circle
          (fx/new-label "X" :size 10 :color Color/DIMGRAY :tooltip "Close editor"))]

    (doto x-pane
      (.setOnMouseExited (fx/event-handler (.setFill x-circle Color/GAINSBORO)))
      (.setOnMouseEntered (fx/event-handler (.setFill x-circle Color/ORANGERED)))
      (.addEventFilter MouseEvent/MOUSE_CLICKED
        (fx/new-eventhandler
          (when (close-file! (:path @file-info_) true)
            (.consume event))))))) 


(def tooltipf
  "      path:  %s
     saved:  %s
 swap-path:  %s
swap-saved:  %s")


(defn- listable-indicate
  "Assembles the string shown in the editor tab.
  Filename or '<no file>.  Appends '*' if not saved to named file.  Appends '#' if content not yet saved to swap-file."
  [labeled {:keys [path swap-path saved-to-swap? saved?] :as info}]
  (let [indication
        (format "%s %s%s"
                (.getFileName path)
                (if-not saved? "*" "")
                (if-not saved-to-swap? "#" ""))
        tooltip-str
        (format tooltipf
                path
                (when (or path swap-path) saved?)
                swap-path
                (when swap-path saved-to-swap?))]

    (.setText labeled indication)
    (fx/set-tooltip labeled tooltip-str)))


(defn- not-found-pane [path]
  (fx/borderpane 
    :center 
    (fx/new-label 
      (format "Unable to locate file: 

        %s


Has it been renamed, moved, or deleted?

(Remove file from list by clicking the 'x')" (->string path))
      :color fx/RED
      :font 16))) 
                             

(defn file-item [filenav-state_ path & {:keys [ns] :or {ns "user.turtle"}}]
  (let [found?     (exists? path)
        
        content    (when found? (-> path ->file slurp))
        editor     (if found? (ed/editor-view content :clj) :not-found)
        file-info_ (eds/new-file-info_ path)
        save-chan  (when found? (eds/save-to-swap-channel))
        close-x    (new-close-x file-info_)
        label      (fx/new-label (filename path) 
                                 :font 14
                                 :color (if found? fx/BLACK fx/RED))
        reveal-fn  #(fx/future-later (->> @file-info_ :path (filetree/reveal filenav-state_)))]
  
    (when found? 
      (eds/state-listener editor label file-info_ save-chan)
      (add-watch file-info_ :indicator #(fx/later (listable-indicate label %4)))
      (listable-indicate label @file-info_))
               
    {:editor editor 
     :listable (fx/hbox label (fx/region :hgrow :always) close-x)
     :file-info_ file-info_
     :editor-root (if found? 
                    (eds/new-editor-root editor file-info_ ns reveal-fn)
                    (not-found-pane path))}))


(defn opens-listcell 
  [_]
  (eval
    `(proxy [ListCell] []
       (updateItem [item# empty?#]
         (proxy-super updateItem item# empty?#)
         (doto ~'this
           (.setText  nil)
           (.setGraphic 
             (when (and (not empty?#) (some? item#))
               (:listable item#))))))))


(defn- click-handler []
  (fx/new-eventhandler 
     (let [item (-> event .getSource .getSelectionModel .getSelectedItem)]
       (set-editor! item))))


(defn open-or-reveal
  "Opens content of file in editor if not already open, else reveals the editor displaying the file."
  [filenav-state_ path]
  ;(println "open-or-reveal" (->string path))
  (let [items (.getItems @listview_)
        ;items (filter #(-> % :file-info_ some?) items)
        item (->> items 
                  (filter #(when-let [info (:file-info_ %)] (= path (:path @info)))) 
                  first)]
    (if (nil? item) 
      (do 
        (.add items (file-item filenav-state_ path))
        ;; recursion (quick-and easy)
        (open-or-reveal filenav-state_ path))
      (do
        (.select (.getSelectionModel @listview_) item)
        (set-editor! item)))))      


(defn- left-root [filenav-state_]
  (let [files (:rootpane @filenav-state_)
        
        opens 
        (doto (ListView.)
          (.setCellFactory (reify Callback (call [_ param] (opens-listcell param)))))
              
        splitpane
        (doto (SplitPane. (fxj/vargs-t Node files opens))
              (.setOrientation Orientation/VERTICAL))]

    (.setOnMouseClicked opens (click-handler))

    (reset! listview_ opens)
    
    (-> opens .getItems 
        (.addAll ^List (map #(file-item filenav-state_ (->path %)) (hist/get-open-files))))
    
    (-> opens .getItems 
        (.addListener  (fx/new-listchangelistener (set-open-files (.getList change))))) 
    
    (future (Thread/sleep 200)
            (fx/later (.setDividerPosition splitpane 0 0.6)))

    splitpane))


(defn main-root []
  (let [filenav-state_ (filetree/file-nav)

        lr (left-root filenav-state_) 

        container
        (reset! editor-pane_ (fx/borderpane :center (fx/new-label "No file selected")))

        splitpane
        (doto (SplitPane. (fxj/vargs-t Node lr container))
              (.setOrientation Orientation/HORIZONTAL))]
    
    (future  (Thread/sleep 333) 
             (fx/later (.setDividerPosition splitpane 0 0.4)))

    ;; set "services" for filetree
    (reset! filetree/open-or-reveal_ (fn [path] (open-or-reveal filenav-state_ path)))
    (reset! filetree/rename-file_ rename-file!)
    (reset! filetree/close-file_ close-file!)

    splitpane))


(defn- new-stage []
  (fx/now
    (->
      (fx/stage
        :title "Files Editors"
        :oncloserequest
        #(do (singleton/remove ::files-editors-stage))
        :tofront true
        :alwaysontop true
        :sizetoscene false
        :scene (fx/scene (main-root))
        :size [700 650])
      styled/style-stage)))
    

(defn get-or-create-stage []
  (singleton/get-or-create ::files-editors-stage #(new-stage)))


;;; DEV ;;;

;(when (env :repl?) (println "Warning: Running george.files-editors/get-or-create-stage") (fx/init) (get-or-create-stage))
;(when (env :repl?) (println "Warning: Running george.files-editors/new-stage") (fx/init) (new-stage))


;; TODO: When file selected, editor should get focus

;; TODO: Handle case where file has moved and #swap#/file cannot save but cannot close.

;; TODO: Better tooltip in open-list,
;; TODO: Save state of last selected file in editor.
;; TODO: Save state of open folder in filetree maybe?
;; TODO: DnD re-ordering of open files.  And also alphabetical ordering (non-destructive).
;; TODO: Implement more sophisticated calculation for width of left pane (and height of bottom pane.)

