;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.files-editors
  (:require
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.editor.core :as ed]
    [george.application.editors :as eds]
    [george.files.filetree :as filetree]
    [george.application.ui.styled :as styled]
    [george.util.singleton :as singleton])
  (:import
    [javafx.scene.control SplitPane ListCell ListView Label]
    [javafx.geometry Orientation]
    [javafx.scene Node]
    [javafx.scene.paint Color]
    [javafx.scene.shape Circle]
    [javafx.scene.input MouseEvent]
    (java.nio.file Path)))


(def listview_ (atom nil))

(def editor-pane_ (atom nil))


(defn- set-editor! [item]
  (.setCenter @editor-pane_ (:editor-root item)))


(defn rename-file! [^Path old ^Path new]
  (when-let [item (->> @listview_ .getItems (filter #(= old (-> % :file-info_ deref :path) )) first)]
    (prn 'rename-file item)
    (let [{:keys [file-info_]} item]
      (swap! file-info_ assoc :path new :swap-path nil :saved? true :saved-to-swap? true))
    true))


(defn close-file! [^Path path save?]
  (println (str *ns* "/close-file") path save?)
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
        (fx/event-handler-2
          [_ e]
          (when (close-file! (:path @file-info_) true)
            (.consume e))))))) 
          ;(let [lv @listview_
          ;      item (first (filter #(= (:file-info_ %) file-info_) (.getItems lv)))]
          ;  (println " Do some work before closing file (saving and such) ...")
          ;  (-> lv .getItems (.remove item))
          ;  (-> lv .getSelectionModel .clearSelection)
          ;  (set-editor! {:editor-root (fx/new-label "No file selected")})
          ;  (.consume e)))))))



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


(defn file-item [path & {:keys [ns] :or {ns "user"}}]
  (let [content    (slurp (.toFile path))
        editor     (ed/editor-view content :clj)
        file-info_ (eds/new-file-info_ path)
        save-chan  (eds/save-to-swap-channel)
        close-x    (new-close-x file-info_)
        label      (Label.)]

    (eds/state-listener editor label file-info_ save-chan)

    (add-watch file-info_ :indicator #(fx/later (listable-indicate label %4)))
    (listable-indicate label @file-info_)
               
    {;:path path
     :editor editor 
     :listable (fx/hbox label (fx/region :hgrow :always) close-x)
     :file-info_ file-info_
     :editor-root (eds/new-editor-root editor file-info_ ns)}))


(defn opens-listcell 
  [_]
  (proxy [ListCell] []
    (updateItem [item empty?]
      (proxy-super updateItem item empty?)
      (doto this
        (.setText  nil)
        (.setGraphic 
          (when (and (not empty?) (some? item))
            (:listable item)))))))


(defn- click-handler []
  (fx/event-handler-2 [_ e]
                      (prn 'consumed? (.isConsumed e))
                      (let [item (-> e .getSource .getSelectionModel .getSelectedItem)]
                        (user/pprint item)
                        (set-editor! item))))


(defn open-or-reveal
  "Opens content of file in editor if not already open, else reveals the editor displaying the file."
  [path]
  (println (str *ns* "open-or-reveal") path)
  (let [items (.getItems @listview_)
        item (->> items (filter #(= path (-> % :file-info_ deref :path)) ) first)]
    (if (nil? item) 
      (do 
        (.add items (file-item path))
        ;; recursion (quick-and easy)
        (open-or-reveal path))
      (do
        (.select (.getSelectionModel @listview_) item)
        (set-editor! item)))))      
        


(defn- left-root []
  (let [files 
        (filetree/file-nav)
   
        opens 
        (doto (ListView.)
              (.setCellFactory (fx/callback opens-listcell)))
              
        splitpane
        (doto (SplitPane. (fxj/vargs-t Node files opens))
              (.setOrientation Orientation/VERTICAL))]

    (.setOnMouseClicked opens (click-handler))

    (reset! listview_ opens)

    (future (Thread/sleep 200)
            (fx/later (.setDividerPosition splitpane 0 0.67)))
    splitpane))


(defn main-root []
  (let [lr (left-root)
        ;ed (editors/new-editors-root)

        container
        (reset! editor-pane_ (fx/borderpane :center (fx/new-label "No file selected")))

        splitpane
        (doto ;(SplitPane. (fxj/vargs-t Node lr ed))
          (SplitPane. (fxj/vargs-t Node lr container))
          (.setOrientation Orientation/HORIZONTAL))]
    
    (future (Thread/sleep 200)
            (fx/later (.setDividerPosition splitpane 0 0.3)))
    ;; set "services" for filetree
    (reset! filetree/open-or-reveal_ open-or-reveal)
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

(when (env :repl?) (println "WARNING: Running george.files-editors/get-or-create-stage" (get-or-create-stage)))
