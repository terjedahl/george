;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.files.filetree
  (:require
    [clojure
     [string :as cs]
     [pprint :refer [pprint]]]
    [environ.core :refer [env]]
    [clj-diff.core :as diff]
    [hara.io.watch]
    [hara.common.watch :as watch]
    [george.javafx :as fx]
    [george.application.ui
     [styled :as styled]
     [layout :as layout]]
    [george.util :refer [->Labeled labeled? timeout]]
    [george.util.colls :as uc]                              ;; This loads defmethod diff/patch
    [common.george.config :as c]
    [common.george.launch.props :as p]
    [common.george.util
     [text :refer [**]]
     [files :as f :refer [filename ->path ->file ->string hidden? exists? same? parent dir? ensured-dir ensured-file move delete]]
     [cli :refer [warn]]
     [platform :as pl]])
  (:import
    [java.io IOException File]
    [java.nio.file Files Path LinkOption]
    [javafx.geometry Pos]
    [javafx.scene Cursor SnapshotParameters]
    [javafx.scene.control TreeView TreeItem TreeCell TextField MultipleSelectionModel]
    [javafx.scene.input TransferMode ClipboardContent KeyEvent MouseButton MouseEvent DragEvent]
    [javafx.scene.paint Color]
    [javafx.util Callback]
    [javafx.beans.value ChangeListener]))


;(set! *warn-on-reflection* true)

(def ILLEGAL_CHARS
  (set "<>*?:`'\"/|\\"))


(def open-or-reveal_ (atom (fn [_] (warn "g.a.filetree/open-or-reveal_ not set!"))))
(def rename-file_    (atom (fn [_ _] (warn "g.a.filetree/rename-file_ not set!"))))
(def close-file_     (atom (fn [_ _] (warn "g.a.filetree/close-file_ not set!"))))


(declare
  lazy-filetreeitem
  set-root
  new-rename-dialog
  delete-dialog
  reveal)


;; Used for delayed proxy-macro eval
(def ^:dynamic *state_*)


(definterface IRefreshable
  (refresh []))


(defn- illegal-chars [s]
  (filter ILLEGAL_CHARS (seq s)))


(defn- filename-lowercased [p]
  (cs/lower-case (filename p)))


(defn- alphabetized [paths]
  (sort-by filename-lowercased paths))


(defn- not-hidden [paths]
  (filter #(not (hidden? %)) paths))


(defn- not-special-hidden [paths]
  (filter #(not= ".DS_Store" (filename %) ) paths))


(defn- get-child-paths [parent-path & [include-hidden? include-special?]]
  (if-not (dir? parent-path)
    []
    (->  parent-path
         Files/newDirectoryStream
         vec
         (#(if include-hidden? 
             (if include-special? 
               (identity %)
               (not-special-hidden %)) 
             (not-hidden %)))
         alphabetized)))


(defn- ^Path item->path [^TreeItem item]
  (.getValue item))


(defn- item->path->string [item]
  (-> item item->path ->string))


(defn- treecell->path [^TreeCell treecell]
  (-> treecell .getTreeItem .getValue))


(defn- is-root [^TreeItem item]
  ;; root has no parent
  (-> item .getParent not))

(defn-  <-treeview [state_]
  (-> @state_ :treeview))


(defn- <-root [state_]
  (-> state_ <-treeview .getRoot))


(defn ^MultipleSelectionModel <-selection-model [state_]
  (-> state_ <-treeview .getSelectionModel))


(defn- <-selected [state_]
  (-> state_ <-selection-model .getSelectedItem))


(defn- clear-selection [state_]
  (-> state_ <-selection-model .clearSelection))
  

(defn select-item [state_ item]
  ;(prn 'select-item item)
  (-> state_ <-selection-model (.select item)))


(defn- file-image [] 
  (fx/imageview "graphics/file-16.png"))
(defn- file-clj-image []
  (fx/imageview "graphics/file-clj-16.png"))
(defn-  folder-image [] 
  (fx/imageview "graphics/folder-16.png"))


(defn disclosure-node
 ([expanded?]
  (disclosure-node true expanded?))
 ([dir? expanded? & [extra-padding-top]]
  (when dir?
    (let [n (fx/polygon 0 0  8 5  0 10 :fill Color/DARKSLATEGRAY :strokewidth 0)]
      (when expanded? (.setRotate n 90))
      (doto (fx/stackpane n) 
        (fx/set-padding (+ 0 (or extra-padding-top 0)) 5 0 5))))))


(defn new-graphic [path]
  (let [dir? (dir? path)
        clj? (.endsWith (filename path) ".clj")]
    (cond
      dir? (folder-image)
      clj? (file-clj-image)
      :default
      (file-image))))


(defn- move-file [source-path target-path]
  (try
    (move source-path target-path)
    (@rename-file_ source-path target-path)
    (catch IOException e (.printStackTrace e))))


(defn- get-those-paths [^DragEvent event]
  (map ->path  (-> event .getDragboard .getFiles)))


(defn- is-ancestor
  "Returns true it 'path2' is an ancestor of 'path1'"
  [path1 path2]
  (loop [path path1]
    (if-let [parent (.getParent path)]
      (if (same? parent path2)
          true 
          (recur parent))
      false)))


(defn- will-receive? 
  "Returns true if 'path1' will receive 'path2'"
  [path1 path2]
  (and (dir? path1) 
       (not (same? path1 path2)) 
       (not (same? path1 (.getParent path2)))  ;; prevent dropping into direct parent directory
       (not (is-ancestor path1 path2))))  ;; prevent dropping into child directory


(defn- colliding-path 
  "Returns the child-path that collides with that-path by filename, else nil"
  [that-path this-path]
  (let [child-paths (get-child-paths this-path)]
    (when-let [p (get (set (map filename child-paths)) (filename that-path))]
      (->path this-path p))))


(defn- warn-of-existing [path]
  (let [nam (filename path)
        par (->string (parent path))
        dir? (dir? path)]
    (fx/alert 
      :type :error
      :title "File/folder name collision"
      :header "Move aborted"
      :text 
      (format "A %s '%s' already exists in %s" 
              (if dir? "folder" "file")
              nam
              par))))


(defn- will-receive-or-warn? [this-path that-path]
  (if (will-receive? this-path that-path)
      (if-let [p (colliding-path that-path this-path)]
        (do (warn-of-existing p) false)
        true)
      false))


(defn- will-receive-all? [this-path those-paths]
    (every? true? (map #(will-receive? this-path %) those-paths)))


(defn- will-receive-all-or-warn? [this-path those-paths]
  (every? true? (map #(will-receive-or-warn? this-path %) those-paths)))


(defn- mark-dropspot [treecell receiving?]
  (let [w [1 1 1 1]]
    (doto treecell
      (.setBorder (fx/new-border (if receiving? Color/GREEN Color/TRANSPARENT) w)))))


(defn make-dropspot [treecell treeitem]
  (mark-dropspot treecell false)

  (.setOnDragOver treecell
    (fx/new-eventhandler ;; DragEvent
      (let [this-path (.getValue treeitem)
            ;that-path (get-that-path event)
            those-paths (get-those-paths event)]
        (when (will-receive-all? this-path those-paths)
          (.acceptTransferModes event (into-array TransferMode/MOVE))))))
          ;(.consume event)))))
  
  (.setOnDragEntered treecell
    (fx/new-eventhandler
      ;(prn 'db-ct (-> event .getDragboard .getContentTypes (.contains DataFormat/FILES)))
      ;(doseq [f (-> event .getDragboard .getFiles)]
      ;  (prn 'f f))
      ;(println " drag-entered:" (->string (.getValue treeitem)))
      (when (will-receive-all? (.getValue treeitem) (get-those-paths event))
            ;(println "marking dir" (.getValue treeitem))
            (mark-dropspot treecell true))
      (.consume event)))

  (.setOnDragExited treecell
    (fx/new-eventhandler ;; DragEvent
       ;(println "un-marking dir" (.getValue treeitem))
       (mark-dropspot treecell false)
       (.consume event)))

  (.setOnDragDropped treecell
    (fx/new-eventhandler
       (let [this-path (.getValue treeitem)
             those-paths (get-those-paths event)]
         (when (will-receive-all-or-warn? this-path those-paths)
           (doseq [that-path those-paths]
             (move-file that-path (->path this-path (filename that-path))))
           (.refresh treeitem)
           (.setDropCompleted event true)
           (-> treecell .getTreeView .getSelectionModel 
               (.selectIndices (.getIndex treecell) (int-array 0))))
         (.consume event))))
  
  treecell)


(defn make-draggable [treecell]
  (let [press-XY (atom nil)
        treeitem (.getTreeItem treecell)
        path (.getValue treeitem)]
    (doto treecell
      (.setOnMousePressed
        (fx/new-eventhandler (reset! press-XY (fx/XY event))))

      ;(.setOnMouseDragged
      ;  (fx/new-eventhandler (.consume event)))

      (.setOnDragDetected
        (fx/new-eventhandler
           ;(println "starting drag: " treecell)
           (let [db
                 (.startDragAndDrop treecell (into-array (list TransferMode/MOVE)))
                 cc
                 (doto (ClipboardContent.) 
                       ;(.putString (->string path))
                       (.putFiles [(->file path)]))

                 [x y] @press-XY
                 [w h] (fx/WH treecell)
                 hoff (- (/ w 2) x)
                 voff (- y (/ h 2))
                 ;_ (println "[x y]:" [x y])
                 ;_ (println "[w h]:" [w h])
                 ;_ (println "hoff:" hoff)
                 ;_ (println "voff:" voff)

                 ghost
                 (doto (SnapshotParameters.)
                       (.setFill Color/TRANSPARENT))]
             
             (.setCursor treecell Cursor/MOVE)
             (.setOpacity treecell 0.8)
             (.setDragView db (.snapshot treecell ghost nil) hoff voff)
             ;(.setOpacity treecell 0.2)
             (.setOpacity treecell 1.0)

             (.setContent db cc)
             (.consume event))))

      (.setOnDragDone
        (fx/new-eventhandler 
          (.setOpacity treecell 1.0)
          (.setCursor treecell Cursor/DEFAULT)
          ;(prn 'onDragDone me)
          ;(prn 'mode (.getTransferMode me))
          (when (.getTransferMode event)
            (try (-> treeitem ^IRefreshable .getParent .refresh) 
                 (catch NullPointerException _)))
          (.consume event))))))


(defn- set-dir 
  "Sets the directory combo to the selected dir, and mounts the tree for the dir"
  [state_ path]
  (set-root state_ path))


(defn set-dir-future 
  "For a more responsive GUI."
  [state_ path]
  (fx/future-later (set-dir state_ path)))


(defn- open [state_]
  (let [p (-> state_ <-selected item->path)]
    (if (dir? p)
      (set-dir-future state_ p)
      (@open-or-reveal_ p))))


(defn info-str [^Path path]
  (let [{:strs [size creationTime lastModifiedTime] :as attrs} 
        (Files/readAttributes path "*" (make-array LinkOption 0))]
    ;(prn attrs)
    (format "path:      %s  
size:      %s bytes  
created:   %s  
modified:  %s  " (->string path) size creationTime lastModifiedTime)))


(defn- path-treecell
  "Returns a custom TreeCell.
  '->str' is optional 1-arg function which takes at item and returns a String."
  [state_]
  ;; https://dev.clojure.org/jira/browse/CLJ-1743
  (binding [*state_* state_]
    (eval
      `(let [state_# *state_*]
         (proxy [TreeCell] []
           ;; Override
           (startEdit [])
      
           ;; Override
           (updateItem [^Path path# empty?#]
             (proxy-super updateItem path# empty?#)
             (if (or (nil? path#) empty?#)
               (doto ~'this
                 (.setGraphic nil)
                 (.setText nil))
               ;; else
               (let [item# (.getTreeItem ~'this)
                     dir?# (dir? path#)]
                 (doto ~'this
                   (.setDisclosureNode (disclosure-node dir?# (.isExpanded item#) 5))
                   (.setGraphic 
                     (fx/hbox 
                       (new-graphic path#) 
                       (fx/new-label (filename path#) :font 14) 
                       (fx/region :hgrow :always) 
                       (doto
                         (layout/menu
                           [:button " " :bottom [(when dir?# [:item "New file ..." #(new-rename-dialog state_# item# true true)])
                                                 (when dir?# [:item "New folder ..." #(new-rename-dialog state_# item# true false)])
                                                 (when dir?# [:separator])
                                                 [:item "Rename ..."  #(new-rename-dialog state_# item# false nil)]
                                                 [:separator]
                                                 ;[:item "Copy" #(println "Copy    NO IMPL")]
                                                 ;(when dir?# [:item "Paste ..." #(println "Paste    NO IMPL")])
                                                 [:item "Delete ..."  #(delete-dialog state_# item#)]
                                                 [:separator]
                                                 (when dir?# [:item "Refresh"  #(.refresh item#)])                 
                                                 [:item "Info"  #(fx/alert :content (fx/new-label (info-str path#) :font (fx/new-font "Source Code Pro" 14)))]
                                                 [:separator]
                                                 [:item (format "Reveal in %s" (cond (pl/macos?) "Finder" (pl/windows?) "Explorer" :else "File manager"))
                                                        #(future (f/open (if dir?# path# (parent path#))))]]])
                         (.addEventFilter MouseEvent/MOUSE_CLICKED  (fx/new-eventhandler (select-item state_# item#)))
                         (fx/add-class "g-no-button"))
 
                       :padding [0 10 0 0]
                       :spacing 3
                       :alignment fx/Pos_CENTER_LEFT))
                            
                   (make-draggable)
                   (make-dropspot item#))))))))))
            

(defn- path-treecell-factory
  "Returns a custom Callback."
  [state_]
  (reify Callback
    (call [_ _]
      (path-treecell state_))))


(defn- refresh-item
  "Can be called directly. It is also called from lazy-filetreeitem, which implements Refreshable.
  Extracts the path from the filetreeitem, and if it is a dir, 
  then first the child items are syncronizes with the underlying paths, and 'refresh' is called on each of them, 
  making it in effect recursive (depth first)."
  [filetreeitem]
  (let [path (.getValue filetreeitem)
        dir? (dir? path)]
    (when dir?
      ;(prn 'refresh-item filetreeitem)
      (let [old-paths-str (map item->path->string (.getChildren filetreeitem))
            new-paths-str (map ->string (get-child-paths path))
            ;; diff/diff only works on seqs of Strings
            edit-script (diff/diff old-paths-str new-paths-str)
            ;; We need to replace "add" strings with filetreeitems before passing it to diff/patch
            edit-script1 
            (update-in edit-script [:+] 
                       (fn [v] 
                         (mapv (fn [[i s]] [i (lazy-filetreeitem (->path s))])
                               v)))]
        ;; The "del-object" needs to be compatible with TreeView, as it will be temporarily inserted before being removed.
        (binding [uc/*DEL_OBJ* (lazy-filetreeitem (->path "DEL_OBJ"))]
          (diff/patch (.getChildren filetreeitem) edit-script1))
        ;; Call refresh on all children. They will sort out if they need to do the same on theirs.
        (doseq [c (.getChildren filetreeitem)]
          (.refresh c))))))


(defn- set-children [treeitem path]
  (fx/children-set-all treeitem
                       (map #(lazy-filetreeitem %)
                            (get-child-paths path))))


(defn- lazy-filetreeitem [path]
  (let [dir?  (dir? path)
        get-children-called_  (atom false)     

        item
        (proxy [TreeItem IRefreshable] [path (if dir? (folder-image) (file-image))]
          ;; Override
          (isLeaf [] 
            (not dir?))
          ;; Override
          (getChildren []
            (when-not @get-children-called_
              (reset! get-children-called_ true)
              (set-children this path))
            (proxy-super getChildren))
          ;; implement IRefreshable
          (refresh []
            (when (and dir? @get-children-called_)
                  (refresh-item this))))]
    item))


(defn- new-root [^Path path]
  (doto (lazy-filetreeitem path)
        (.setExpanded true)))


(defn- tree-mousehandler [state_]
  (fx/new-eventhandler
     (when (and (-> event .getButton (.equals MouseButton/PRIMARY))
                (-> event .getClickCount (= 2)))
         (open state_)
         (.consume event))))


(defn- open-parent-folder
  "If current tree-root has parent, then steps up to parent and returns it, else nil"
  [state_]
  (when-let [root-parent (-> state_ <-root .getValue .getParent)]
      (set-dir-future state_ root-parent)
      root-parent))


(defn- tree-keyhandler [state_]
  (fx/key-pressed-handler
    {
     #{:SHORTCUT :ENTER}
     (fx/new-eventhandler
       (when (<-selected state_)                            
         (open state_)
         (.consume event)))

     #{:SHORTCUT :UP}
     (fx/new-eventhandler
        (when-let [item (<-selected state_)]
          (when (-> item is-root)
            (when (open-parent-folder state_)
              (.consume event)))))}))


(defn- ^ChangeListener tree-focuslistener [state_]
  (fx/new-changelistener 
     (when (and new-value ;; boolean
                (not (<-selected state_)))
       (-> state_ <-treeview .getSelectionModel .selectFirst))))


(defn- make-autoscrolling [treeview]
  (.setOnDragOver treeview
    (fx/new-eventhandler 
      (let [y (.getY event)
            bounds (.getBoundsInLocal treeview)
            h (.getHeight bounds)
            want-to-scroll? (not (< 50 y (- h 50)))
            up? (< y 50)]
        (when want-to-scroll?
          (when-let [scrollbar (fx/find-scrollbar treeview)]
            (.setUnitIncrement scrollbar 1)
            (if up? (.decrement scrollbar)
                    (.increment scrollbar))))))))


(defn- set-watch [state_]
  (let [{:keys [watched watched-label]} @state_]
    (future
      (when watched
        (watch/clear watched))
      (fx/later (.setText watched-label "w?"))
      
      (let [root (<-root state_)
            file ^File (-> root item->path ->file)
            res    
            (timeout 5000 ::timed-out
                     (watch/add file :tree-root #(.refresh root) 
                                {:types #{:create :modify :delete}
                                 :recursive true
                                 :exclude [".DS_Store"]
                                 :supress false}))]
        
        (swap! state_ assoc :watched (when (not= res ::timed-out) res))
        (fx/later (.setText watched-label (if (some? (:watched @state_)) "w" "!w"))))
      
      state_)))


(defn- new-treeview [state_]
  (let [treeview (TreeView.)]
    (doto treeview
      ;(.setShowRoot false)
      (.setCellFactory (path-treecell-factory state_))
      (make-autoscrolling)
      (.setEditable true)
      ;(-> .getSelectionModel .selectedItemProperty (.addListener (tree-listener current-item_)))
      (.addEventFilter MouseEvent/MOUSE_CLICKED (tree-mousehandler state_))
      (.addEventFilter KeyEvent/KEY_PRESSED (tree-keyhandler state_))
      (-> .focusedProperty (.addListener (tree-focuslistener state_))))    

    treeview))


(defn- set-root [state_ path]
  (let [{:keys [empty-label treeview]} @state_
        root (new-root path)]
    
    ;(set-watch state_)
    
    (doto treeview
      (.setRoot root)
      (.scrollTo 0))))
    ;(.setVisible empty-label (-> root .getChildren empty?))))               


(defn- delete-path 
  "Deletes files and folders recursively."
  [path]
  (when (dir? path)  
    (doseq [p (get-child-paths path true true)]
      (delete-path p)))
  ;(prn 'deleting-path (->string path))
  (delete path))


(defn- count-paths-recursively 
  "Counts files and folders, including the passed-in path itself"
  [path]
  (if (dir? path)
      (reduce + 1 (map count-paths-recursively (get-child-paths path true)))
      1))


(defn delete-dialog [state_ item]
  (let [path  (item->path item)
        dir? (dir? path)
        cnt (if dir? (dec (count-paths-recursively path)) 0)
        xff (if (= cnt 1) 
              "1 file or folder" 
              (format "%s files and folders" cnt))
        res
        (fx/alert 
          :type :confirmation 
          :title (format "Delete %s?" (if dir? "folder" "file"))
          :options ["No. Don't delete." "Yes. Delete!"]
          ;:cancel-option? true
          :text 
          (format "Do you want to delete %s:  \n\n  \"%s\"%s  \n\nDeleting can not be reversed!\n\n"
            (if dir? "folder" "file")
            (filename path)
            (if-not dir? "" (format "\n\n  It contains %s." xff))))]
            
    (when (= res 1)
      (@close-file_ path false)
      (when-not (f/trash path)
        (delete-path path))
      (-> item .getParent .refresh)
      (clear-selection state_))))


(defn new-rename-dialog 
  "'file?' is only used in conjunction with 'new?'"
  [state_ item new? file?]
  (let [item (or item (<-selected state_) (<-root state_))
        path (item->path item)
        
        file? (if new? file? 
                       (if (some? file?) file? 
                                         (not (dir? path))))
        parent-path
        (if new?
          ;; The path may be from a selected path, and if it is a file, then use its parent.
          (if (dir? path) path (parent path))  
          (parent path))
 
        parent-str
        (str (->string parent-path) (pl/file-sep))
        
        newnamef  
        (when new? (if file? "file%s.clj" "folder%s"))

        new-name 
        (when new?
          (loop [name (format newnamef "") n 1]
            (if (exists? (->path (str parent-str name)))            
              (recur (format newnamef n) (inc n))
              name)))
        
        name
        (if new? new-name (filename path))
        
        field-len (min 15 (if new? 15 (.length name)))
              
        name-field
        (doto (fx/textfield :text name) 
              (.setPrefColumnCount field-len))
        
        error-label
        (fx/new-label " " 
           :font (fx/new-font "Roboto" 14) 
           :color Color/RED)

        form
        (fx/vbox
          (fx/hbox
            (if file? (file-image) (folder-image))
            (styled/new-label parent-str :size 14)
            name-field
            ;(when new? folder-checkbox)
            :alignment Pos/CENTER_LEFT
            :spacing 10)
          error-label
          :spacing 10
          :padding 10)
        options [(if new? "Create" "Rename")]

        alert
        (fx/alert
          :type :none
          :title (if new? (if file? "New file" "New folder") (if file? "Rename file" "Rename folder"))
          :content form
          :options options
          :cancel-option? true
          :mode nil)

        save-button
        (doto
          (-> alert .getDialogPane (.lookupButton (-> alert .getButtonTypes first)))
          (.setDisable (not new?)))

        do-checks
        (fn []
          (let [n (.trim (.getText name-field))
                changed? (not= n name)
                has-content? (not (empty? n))]
            (if-not (and changed? has-content?)
              (fx/set-enable save-button false)
              (let [p (->path parent-str n)
                    ;; Does an existing file/folder exist?
                    new? (not (exists? p))
                    ;; Is the path legal?  (no bad chars etc)
                    illegals (illegal-chars n)
                    legal? (nil? (first illegals))]
                (if-not new?
                  (.setText error-label "An object with this name already exists!")
                  (if-not legal?
                    (.setText error-label 
                              (str "Name may not contain " (apply str (interpose " or " (map #(str \' % \') illegals)))))
                    (.setText error-label " ")))

                (fx/set-enable save-button (and new? legal?))))))]
    
    (doto ^TextField name-field
      (.requestFocus)
      (.selectRange 0 
                    (- (count name) (if file? 4 0)))
      (-> .textProperty (fx/add-changelistener (do-checks))))
    
    ;; process the return-value from the alert    
    (when (fx/option-index (.showAndWait alert) options)
      (let [p (->path parent-str (.trim (.getText name-field)))]
        (if new?
          (if file?
            (@open-or-reveal_ (ensured-file p))
            (ensured-dir p))
          ;; else
          (move-file path p))
        ;; finally
        (.refresh (if-let [itemp (.getParent item)] itemp item))
        (doto state_
          (clear-selection)
          (reveal  p))))))


(def default-state
  {:rootpane nil
   :treeview nil
   :empty-label nil
   :watched nil
   :watched-label nil})


(defn file-nav 
  "Entry point to module. Returns an state-atom containing all relevant objects."
  [& [inital-path]]
  (let [
        state_ (atom default-state)

        initial-path (or inital-path (->path (c/documents-dir)))

        to-parent-button (styled/small-button "Up"
                                    :tooltip "Open parent folder"
                                    :onaction #(open-parent-folder state_))

        watched-label (doto (fx/new-label "." :color fx/ANTHRECITE :font 16)
                            (fx/set-padding 5))
        
        treeview     (new-treeview state_)

        empty-label  (doto (fx/new-label "Empty" :font 24 :color Color/LIGHTGRAY) (.setVisible false))

        treeview-layers (fx/stackpane treeview empty-label)

        location-bar
        (fx/hbox to-parent-button
                 ;dirs-combo ;watched-label refresh-button
                 (fx/region :hgrow :always)
                 ;new-button
                 :padding 5 :spacing 5 :alignment fx/Pos_CENTER_LEFT)


        root-pane
        (doto (fx/borderpane)
              ;; Set children in specific order to ensure correct focus traversal order
              (.setTop location-bar)
              (.setCenter treeview-layers))]

    (swap! state_ assoc
      :rootpane root-pane
      :treeview treeview
      :empty-label empty-label
      :watched nil
      :watched-label watched-label)

    (set-dir-future state_ initial-path)

    state_))


(defn- subpath 
  "Differs from Path.subpath in that it returns an absolute path (from root to 'end')."
  [^Path path end]
  (let [sub (.subpath path 0 end)
        root (.getRoot path)]
    (.resolve root sub)))


(defn reveal
  "Makes file visible and marked in tree-view if possible. 
  Returns truth-y value on success."
  [state_ path]
  ;(prn 'reveal path)
  
  ;; Ensure tree is set to a common ancestor
  (when-let [common-path
             (loop [common (-> state_ <-root item->path)]
               (if (.startsWith path common)
                 common
                 (when-let [par (parent common)]
                   (recur par))))]
    ;(prn 'common-path common-path)
    (when-not (same? common-path (-> state_ <-root item->path))
      ;(prn 'set-dir common-path)
      (set-dir state_ common-path))
    ;; On a future-later to allow tree to render before traversing and opening as we go
    (fx/future-later
      ;; Search for item matching path
      (let [common-cnt (.getNameCount common-path)
            path-cnt (.getNameCount path)
            root-item (<-root state_)]
        ;(prn 'root-item root-item)
        (when-let [found-item
                   (loop [cnt common-cnt item root-item]
                     ;(prn 'loop cnt item)
                     (if (< cnt path-cnt)
                       (do
                         (.setExpanded item true)
                         (let [child-path (subpath path (inc cnt))
                               child-item (->> item .getChildren
                                               (filter #(same? child-path (item->path %)))
                                               first)]
                           ;(prn 'child-path child-path)
                           ;(prn 'child-item child-item)
                           (recur (inc cnt) child-item)))
                       item))]
          ;(prn 'found-item found-item)

          ;; Set tree to display item
          (select-item state_ found-item)
          (let [tv ^TreeView (-> state_ <-treeview)
                i (.getRow tv found-item)]
            (.requestFocus tv)
            (.scrollTo tv (- i 2))))))))


(defn stage [root]
  (fx/later
     (->
       (fx/stage
         :title "File navigator"
         :scene (fx/scene root)
         :tofront true
         ;:alwaysontop true
         :size [500 500]
         :sizetoscene false)
       styled/style-stage)))


;;;; DEV


;(when (env :repl?) (fx/init) (-> (file-nav) deref :rootpane stage))

;; TODO: Ensure that long filenames compress rather than activating horizontal scrolling - both in filetree and openlist.

;;;; FUTURE RELEASE

;; TODO: Update files when they are moved with DnD

;; TODO: Make "shortcut" back to "George" folder.  (Also note that it was confusing that double-clicking stepped pupils inn to sub-folder.)

;; TODO: Ensure that there is always an ending on the file. ('.clj' or similar)

;; TODO: Implement cut/copy/paste
;; TODO: Insert cut/copy/paste into item-menu

;; TODO: Handle security-exception when attempting to reveal a restricted dir.  Alter the cellrenderer also.

;; TODO: When an item is moved, the wrong item is marked with an outline afterwards

;; TODO: Ensure that children are also "ghosted" when dragging
;; TODO: No triangle if folder is empty

;; TODO: Better graphics !!!
;; TODO: Custom folder/file icons for locked folders/files
;; TODO: Use graphic for watch-indicator (with tooltip)
;; TODO: Replace refresh-button with click on watch-indicator
;; TODO: Clickable "breadcrums" in stead of "Up" button
;; TODO: memoize icon functions

;; TODO: Re-implement watch-mechanism
;; TODO: Refresh-button should only appear if adding watch fails.

;; TODO: Make drag be able to drop in top-level (hidden root) Complicated?!  Re-organize to allow TreeView itself to handle all DnD ...?

;; TODO: See:  https://www.youtube.com/watch?v=VW--oMA62Ok