;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.files.filetree
  (:require
    [clojure.string :as cs]
    [clojure.pprint :refer [pprint]]
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [clj-diff.core :as diff]
    [george.util :refer [->Labeled labeled? timeout] :as gu] ;; This also loads defmethod diff/patch
    [george.util.text :refer [**]]
    [george.application.ui.styled :as styled]
    [hara.io.watch]
    [hara.common.watch :as watch])
  (:import
    [java.io IOException File]
    [java.nio.file Files Paths Path LinkOption StandardCopyOption NoSuchFileException]
    [javafx.geometry Pos]
    [javafx.scene Cursor SnapshotParameters Node]
    [javafx.scene.control TreeView TreeItem TreeCell MenuItem ContextMenu ComboBox Alert Button ListCell TextField]
    [javafx.scene.input TransferMode ClipboardContent KeyEvent MouseButton MouseEvent DragEvent]
    [javafx.scene.paint Color]
    [javafx.util Callback]
    [java.nio.file.attribute FileAttribute]
    [javafx.beans.value ChangeListener]
    [george.util Labeled]
    [java.util List]))

;(set! *warn-on-reflection* true)


(def ILLEGAL_CHARS
  (set "<>*?:`'\"/|\\"))


(def open-or-reveal_ (atom (fn [_] (println "WARNING! g.a.filetree/open-or-reveal_ not set!"))))
(def rename-file_    (atom (fn [_ _] (println "WARNING! g.a.filetree/rename-file_ not set!"))))
(def close-file_     (atom (fn [_ _] (println "WARNING! g.a.filetree/close-file_ not set!"))))


(declare
  lazy-filetreeitem
  populate-dirs-combo
  set-root)


(definterface IRefreshable
  (refresh []))


(defn- illegal-chars [s]
  (filter ILLEGAL_CHARS (seq s)))


(defn ^String filename [^Path path]
  (str (.getFileName path)))


(defn to-path [s & args]
  (Paths/get s (into-array String args)))


(defn to-string [^Path path]
  (-> path .toAbsolutePath str))


(defn is-dir [path]
  (Files/isDirectory path (make-array LinkOption 0)))


(defn- filename-lowercased [p]
  (cs/lower-case (filename p)))


(defn- alphabetized [paths]
  (sort-by filename-lowercased paths))


(defn- exists [path]
  (Files/exists path (into-array [LinkOption/NOFOLLOW_LINKS])))


(defn- not-hidden [paths]
  (filter #(not (Files/isHidden %)) paths))


(defn- not-special-hidden [paths]
  (filter #(not= ".DS_Store" (filename %) ) paths))


(defn- get-child-paths [parent-path & [include-hidden? include-special?]]
  ;(prn 'parent-path parent-path)
  (if-not (is-dir parent-path)
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
  (-> item item->path to-string))


(defn- treecell->path [^TreeCell treecell]
  (-> treecell .getTreeItem .getValue))


(defn- is-root [^TreeItem item]
  ;; root has no parent
  (-> item .getParent not))


(defn- ^TreeView <-treeview 
  "Helper function - for save extraction and type-hinting"
  [state_]
  (-> @state_ :treeview))


(defn- <-root [state_]
  (-> state_ <-treeview .getRoot))


(defn- <-selected [state_]
  (-> state_ <-treeview .getSelectionModel .getSelectedItem))



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
  (let [dir? (is-dir path)
        clj? (.endsWith (filename path) ".clj")]
    (cond
      dir? (folder-image)
      clj? (file-clj-image)
      :default
      (file-image))))



(defn- move-file [source-path target-path]
  (try
    (Files/move source-path
                target-path
                (fxj/vargs StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE))
    (@rename-file_ source-path target-path)
    (catch IOException e (.printStackTrace e))))


(defn- get-those-paths [^DragEvent event]
  (map #(.toPath %) 
       (-> event .getDragboard .getFiles)))


(defn- is-same [path1 path2]
  (try (Files/isSameFile path1 path2) (catch NoSuchFileException _ false)))


(defn- is-ancestor
  "Returns true it 'path2' is an ancestor of 'path1'"
  [path1 path2]
  (loop [path path1]
    (if-let [parent (.getParent path)]
      (if (is-same parent path2)
          true 
          (recur parent))
      false)))


(defn- will-receive? 
  "Returns true if 'path1' will receive 'path2'"
  [path1 path2]
  (and (is-dir path1) 
       (not (is-same path1 path2)) 
       (not (is-same path1 (.getParent path2)))  ;; prevent dropping into direct parent directory
       (not (is-ancestor path1 path2))))  ;; prevent dropping into child directory


(defn- colliding-path 
  "Returns the child-path that collides with that-path by filename, else nil"
  [that-path this-path]
  (let [child-paths (get-child-paths this-path)]
    (when-let [p (get (set (map filename child-paths)) (filename that-path))]
      (to-path (to-string this-path) p))))


(defn- warn-of-existing [path]
  (let [nam (filename path)
        par (to-string (.getParent path))
        dir? (is-dir path)]
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
          (.acceptTransferModes event (fxj/vargs TransferMode/MOVE))))))
          ;(.consume event)))))
  
  (.setOnDragEntered treecell
    (fx/new-eventhandler
      ;(prn 'db-ct (-> event .getDragboard .getContentTypes (.contains DataFormat/FILES)))
      ;(doseq [f (-> event .getDragboard .getFiles)]
      ;  (prn 'f f))
      ;(println " drag-entered:" (to-string (.getValue treeitem)))
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
             (move-file that-path (to-path (to-string this-path) (filename that-path))))
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
      ;  (fx/event-handler-2 [_ me] (.consume me)))

      (.setOnDragDetected
        (fx/new-eventhandler
           ;(println "starting drag: " treecell)
           (let [db
                 (.startDragAndDrop treecell (fxj/vargs TransferMode/MOVE))
                 cc
                 (doto (ClipboardContent.) 
                       ;(.putString (to-string path))
                       (.putFiles [(.toFile ^Path path)]))

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


(defn set-context-menu [treecell]
  (let [cm-dir
        (ContextMenu.
          (into-array
            [(doto (MenuItem. "Refresh")
                   (fx/set-onaction
                     #(-> treecell ^IRefreshable .getTreeItem .refresh)))]))             
        
        cm-handler
        (fx/new-eventhandler
           (.show  cm-dir 
                   ^Node treecell 
                   ^double (.getScreenX event) 
                   ^double (.getScreenY event)))]
    
    (when  (is-dir (treecell->path treecell))
        (.setOnContextMenuRequested treecell cm-handler))))


(defn- set-dir 
  "Sets the directory combo to the selected dir, and mounts the tree for the dir"
  [state_ combo path watched_ watch-label]
  (future
    (fx/later
      (populate-dirs-combo combo path)
      (set-root state_ path watched_ watch-label)
      (.requestFocus combo))))


(defn- open [state_ combo watched_ watch-label]
  (let [p (-> state_ <-selected item->path)]
    (if (is-dir p)
      (set-dir state_ combo p watched_ watch-label)
      (do
        (prn 'calling-open-on-file p)
        (@open-or-reveal_ p)))))


(defn tooltip-str [^Path path]
  (let [{:strs [size creationTime lastModifiedTime] :as attrs} 
        (Files/readAttributes path "*" (make-array LinkOption 0))]
    ;(prn attrs)
    (format "path:     %s
size:     %s bytes
created:  %s
modified: %s 
    " (to-string path) size creationTime lastModifiedTime)))


(defn- path-treecell
  "Returns a custom TreeCell.
  '->str' is optional 1-arg function which takes at item and returns a String."
  []
  (eval
    `(proxy [TreeCell] []
       ;; Override
       (startEdit [])
    
       ;; Override
       (updateItem [^Path path# empty?#]
         (proxy-super updateItem path# empty?#)
         (if (or (nil? path#) empty?#)
           (doto ~'this
             (.setGraphic nil)
             (.setText nil)
             (fx/set-tooltip nil))
           ;; else
           (doto ~'this
             (.setDisclosureNode (disclosure-node (is-dir path#) (-> ~'this .getTreeItem .isExpanded) 5))
             (.setGraphic (new-graphic path#))
             (.setText (filename path#))
             (fx/set-tooltip (tooltip-str path#))
             (make-draggable)
             (make-dropspot (.getTreeItem ~'this))
             (set-context-menu)))))))
            

(defn- path-treecell-factory
  "Returns a custom Callback."
  []
  (reify Callback
    (call [_ _]
      (path-treecell))))


(defn- dir-combocell []
  (eval
    `(let [;indent 15
           indent# 5]
       (proxy [ListCell] []
         ;; Override
         (updateItem [^Labeled item# is-empty#]
           (proxy-super updateItem item# is-empty#)
           (if (or (nil? item#) is-empty#)
             (doto ~'this
               (.setGraphic nil)
               (.setText nil))
             (let [{:keys [~'value ~'ind]} item#
                   label# (filename ~'value)
                   hbox# (doto (fx/new-label nil :graphic (fx/hbox (disclosure-node true) (new-graphic ~'value) :spacing 3)) 
                               (fx/set-padding [0 0 0 (* ~'ind indent#)]))]
               (doto ~'this
                 (.setGraphic hbox#)
                 (.setText (if (empty? label#) "/" label#))))))))))


;; https://docs.oracle.com/javase/8/javafx/user-interface-tutorial/combo-box.htm
(defn- ^Callback dir-combo-factory
  []
  (reify Callback
    (call [_ _]
      (dir-combocell))))


(defn- refresh-item 
  "Can be called directly. It is also called from lazy-filetreeitem, which implements Refreshable.
  Extracts the path from the filetreeitem, and if it is a dir, 
  then first the child items are syncronizes with the underlying paths, and 'refresh' is called on each of them, 
  making it in effect recursive (depth first)."
  [filetreeitem]
  (let [path (.getValue filetreeitem)
        dir? (is-dir path)]
    (when dir?
      ;(prn 'refresh-item filetreeitem)
      (let [old-paths-str (map item->path->string (.getChildren filetreeitem))
            new-paths-str (map to-string (get-child-paths path))
            ;; diff/diff only works on seqs of Strings
            edit-script (diff/diff old-paths-str new-paths-str)
            ;; We need to replace "add" strings with filetreeitems before passing it to diff/patch
            edit-script1 
            (update-in edit-script [:+] 
                       (fn [v] 
                         (mapv (fn [[i s]] [i (lazy-filetreeitem (to-path s))])
                               v)))]
        ;; The "del-object" needs to be compatible with TreeView, as it will be temporarily inserted before being removed.
        (binding [gu/*DEL_OBJ* (lazy-filetreeitem (to-path "DEL_OBJ"))]
          (diff/patch (.getChildren filetreeitem) edit-script1))
        ;; Call refresh on all children. They will sort out if they need to do the same on theirs.
        (doseq [c (.getChildren filetreeitem)]
          (.refresh c))))))


(defn- set-children [treeitem path]
  (let [children (.getChildren treeitem)
        paths (get-child-paths path)]
    ;(.setAll  children (fxj/vargs* (map #(lazy-filetreeitem %) paths)))
    (.setAll children ^List (map #(lazy-filetreeitem %) paths))))


(defn- lazy-filetreeitem [path]
  (let [dir?  (is-dir path)
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


(defn- tree-mousehandler [state_ combo watched_ watch-label]
  (fx/new-eventhandler
     (when (and (-> event .getButton (.equals MouseButton/PRIMARY))
                (-> event .getClickCount (= 2)))
         (open state_ combo watched_ watch-label)
         (.consume event))))


(defn- tree-keyhandler [state_ combo watched_ watch-label]
  (fx/key-pressed-handler
    {
     #{:SHORTCUT :ENTER}
     (fx/new-eventhandler
       (when (<-selected state_)                            
         (open state_ combo watched_ watch-label)
         (.consume event)))

     #{:SHORTCUT :LEFT}
     (fx/new-eventhandler
        (when-let [item (<-selected state_)]
          (when (-> item .getParent is-root)
            (when-let [root-parent (-> item .getValue .getParent .getParent)]
              (set-dir state_ combo root-parent watched_ watch-label)
              (.consume event)))))}))


(defn- ^ChangeListener tree-focuslistener [state_]
  (fx/new-changelistener 
     (when (and new-value ;; boolean
                (not (<-selected state_)))
       (-> state_ <-treeview .getSelectionModel .selectFirst))))


(defn- make-autoscrolling [^TreeView treeview]
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


(defn- set-watch [state_ watched_ watch-label]
  (future
    (when-let [f @watched_]
      (watch/clear f))
    (fx/later (.setText watch-label "w?"))
    
    (let [root (<-root state_)
          file ^File (-> root item->path .toFile)
          res    
          (timeout 5000 ::timed-out
                   (watch/add file :tree-root #(.refresh root) 
                              {:types #{:create :modify :delete}
                               :recursive true
                               :exclude [".DS_Store"]
                               :supress false}))]
      
      (reset! watched_ (when (not= res ::timed-out) res))
      (fx/later (.setText watch-label (if (some? @watched_) "w" "!w"))))
    
    state_))


(defn- ^TreeView new-treeview [state_ combo watched_ watch-label]
  (let [treeview (TreeView.)]
    (doto treeview
      ;(.setShowRoot false)
      (.setCellFactory (path-treecell-factory))
      (make-autoscrolling)
      (.setEditable true)
      ;(-> .getSelectionModel .selectedItemProperty (.addListener (tree-listener current-item_)))
      (.addEventFilter MouseEvent/MOUSE_CLICKED (tree-mousehandler state_ combo watched_ watch-label))
      (.addEventFilter KeyEvent/KEY_PRESSED (tree-keyhandler state_ combo watched_ watch-label))
      (-> .focusedProperty (.addListener (tree-focuslistener state_))))    

    ;(set-watch state_ watched_ watch-label)
    treeview))


(defn- set-root [state_ path watched_ watch-label]
  (let [{:keys [empty-label treeview]} @state_
        
        root (new-root path)]
    
    (set-watch state_ watched_ watch-label)
    
    (doto treeview
      (.setRoot root)
      (.scrollTo 0))
      ;(make-dropspot root))

    (.setVisible empty-label (-> root .getChildren empty?))))               


(defn- delete-path 
  "Deletes files and folders recursively."
  [path]
  (when (is-dir path)  
    (doseq [p (get-child-paths path true true)]
      (delete-path p)))
  (prn 'deleting-path (to-string path))
  (Files/deleteIfExists path))


(defn- count-paths-recursively 
  "Counts files and folders, including the passed-in path itself"
  [path]
  (if (is-dir path)
      (reduce + 1 (map count-paths-recursively (get-child-paths path true)))
      1))


(defn- delete-dialog [state_]
  (let [item (<-selected state_)
        path  (item->path item)
        dir? (is-dir path)
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
            
    ;(println res)    
    (when (= res 1)
      (@close-file_ path false)
      (delete-path path)
      ;; TODO: In Java 9+, we would prefer to use: moveToTrash(File f)
      ;; https://docs.oracle.com/javase/9/docs/api/java/awt/Desktop.html#moveToTrash-java.io.File-
      ;(Thread/sleep 200)  ;; Allow current-item_ to be updated
      (-> item .getParent .refresh))))


(defn- new-rename-dialog [state_ new?]
  (let [item  (or (<-selected state_)
                  (<-root state_))
        
        path (item->path item)
        
        name
        (if new? "" (filename path))
        
        parent-path
        (if new?
            (if (is-dir path) path (.getParent path))
            (.getParent path))
        
        parent-str
        (str (to-string parent-path) "/")

        len (if new? 15 (.length name))
        len (if (< len 15) 15 len)
              
        name-field
        (doto (fx/textfield :text name) 
              (.setPrefColumnCount len))
        
        error-label
        (fx/new-label " " 
           :font (fx/new-font "Roboto" 14) 
           :color Color/RED)

        folder-checkbox
        (doto
          (fx/checkbox "Folder")
          (.setGraphic (folder-image))
          (fx/set-tooltip "If checked, a folder is created.\nOtherwise a file is created."))

        form
        (fx/vbox
          (fx/hbox
            (styled/new-label parent-str :size 14)
            name-field
            (when new? folder-checkbox)
            :alignment Pos/CENTER_LEFT
            :spacing 10)
          error-label
          :spacing 10
          :padding 10)
        options [(if new? "Create" "Rename")]

        alert ^Alert
        (fx/alert
          :type :none
          :title (if new? "New file or folder" "Rename file or folder")
          :content form
          :options options
          :cancel-option? true
          :mode nil)

        save-button
        (doto
          (-> alert .getDialogPane (.lookupButton (-> alert .getButtonTypes first)))
          (.setDisable true))

        do-checks
        (fn []
          (let [n (.trim (.getText name-field))
                changed? (not= n name)
                has-content? (not (empty? n))]
            (if-not (and changed? has-content?)
              (fx/set-enable save-button false)
              (let [p (to-path parent-str n)
                    ;; Does an existing file/folder exist?
                    new? (not (exists p))
                    ;; Is the path legal?  (no bad chars etc)
                    illegals (illegal-chars n)
                    legal? (nil? (first illegals))]
                (if-not new?
                  (.setText error-label "File or folder with this name already exists!")
                  (if-not legal?
                    (.setText error-label 
                              (str "Name may not contain " (apply str (interpose " or " (map #(str \' % \') illegals)))))
                    (.setText error-label " ")))

                (fx/set-enable save-button (and new? legal?))))))]
    
    (doto ^TextField name-field
      (.requestFocus)
      (.selectAll)
      (-> .textProperty (.addListener ^ChangeListener (fx/new-changelistener (do-checks)))))

    (when (fx/option-index (.showAndWait alert) options)
      ;(println "new-rename-dialog res:" (.getText name-field))
      (let [p (to-path parent-str (.trim (.getText name-field)))]
        (if new?
          (if (.isSelected folder-checkbox)
            (Files/createDirectory p (make-array FileAttribute 0))
            (do
              (Files/createFile p (make-array FileAttribute 0))
              (@open-or-reveal_ p)))
          ;; TODO: move file if not new?
          (move-file path p))
        ;; TODO: refresh folder in tree-view!
        (-> item .getParent .refresh)))))


(defn- path-label [i path]
  (let [n (filename path)
        n (if (empty? n) 
            "/"             
            n)]
    (str (** i "    ") n)))


(defn- populate-dirs-combo [^ComboBox combo ^Path path]
  (let [paths
        (->
          (loop [res [path] path path]
            (if-let [parent (.getParent path)]
              (recur (conj res parent) parent)
              res))
          reverse)
        labeled-paths 
        (map-indexed #(assoc (->Labeled (path-label %1 %2) %2) :ind %1) paths)]
    ;(pprint parents)
    
    (doto combo
      ;(-> .getItems (.setAll (list (->Labeled (filename path) path))))
      (-> .getItems (.setAll ^List labeled-paths))
      (-> .getSelectionModel .selectLast))))



(def default-state
  {:treeview nil
   :empty-label nil})


(defn file-nav []
  (let [
        state_ (atom default-state)
        
        watched_     (atom nil)
        
        initial-path (to-path (env :user-home) "Documents" "George")
        
        dirs-combo   (ComboBox.)
        
        watch-label (doto (fx/new-label "." :color fx/ANTHRECITE :font 16)
                          (fx/set-padding 5))
        
        treeview     (new-treeview state_ dirs-combo watched_ watch-label)
        empty-label (fx/new-label "Empty" :font 24 :color Color/LIGHTGRAY)
        treeview-layers (fx/stackpane treeview empty-label)

        new-button
        (styled/small-button "New"
                             :tooltip "Create a new file or folder in current folder"
                             :onaction #(new-rename-dialog state_ true))

        rename-button
        (styled/small-button "Rename"
                             :tooltip "Rename selected file or folder"
                             :onaction #(new-rename-dialog state_ false))

        delete-button
        (styled/small-button "Delete"
                             :tooltip "Delete selected file or folder"
                             :onaction #(delete-dialog state_))

        refresh-button
        (styled/small-button "R"
                             :tooltip "Manually refresh file-tree"
                             :onaction #(future (fx/later (-> state_  <-root .refresh))))

        location-bar
        (fx/hbox dirs-combo watch-label refresh-button
                 :padding 5 :spacing 5 :alignment fx/Pos_CENTER_LEFT)

        button-bar
        (fx/hbox new-button rename-button delete-button
                 :padding 5 :spacing 5)
        
        dc-factory (dir-combo-factory)]

    (swap! state_ assoc
       :treeview treeview
       :empty-label empty-label)

    (doto dirs-combo
      (.setButtonCell (.call dc-factory nil))
      (.setCellFactory dc-factory)
      (fx/set-tooltip "Open a parent folder")
      (fx/set-onaction2
        #(let [combo ^ComboBox (.getSource %2)
               path (-> combo .valueProperty .getValue :value)]
           ;(prn 'combo-event %2)
           (.hide combo)
           (set-dir state_ combo path watched_ watch-label)))
      (.addEventFilter KeyEvent/KEY_PRESSED 
                       (fx/key-pressed-handler 
                         {#{:SPACE} 
                          #(if (.isShowing dirs-combo) (.hide dirs-combo) (.show dirs-combo))}))
      (populate-dirs-combo initial-path))
    
    (-> treeview .getSelectionModel .selectedItemProperty 
        (.addListener ^ChangeListener
          (fx/new-changelistener
             (doseq [^Button button [rename-button delete-button]]
               (.setDisable button (nil? new-value))))))

    (set-root state_ initial-path watched_ watch-label)
    
    (doto (fx/borderpane)
      ;; Set children in specific order to ensure correct focus traversal order
      (.setTop location-bar)
      (.setCenter treeview-layers)
      (.setBottom button-bar))))


(defn reveal
  "Makes file visible and marked in  tree-view"
  [path]
  (println "NO IMPL: g.f.filetree/reveal" path))


(defn stage [root]
  (fx/init)
  (let []
    (fx/later
      (->
        (fx/stage
          :title "File navigator"
          :scene (fx/scene root)
          :tofront true
          ;:alwaysontop true
          :size [500 500]
          :sizetoscene false)
        styled/style-stage))))


;;;; DEV


;(when (env :repl?) (stage (file-nav)))

;; TODO: Re-implement watch-mechanism

;; TODO: Pass more args around in "state_"

;; TODO: Attach local "edit" [...] menu to each file item (copy/cut/paste/rename/delete)
;; TODO: Maybe implement context-menu on items (open/open in tab (for folders), edit, delete.

;; TODO: Ensure that children are also "ghosted" when dragging

;; TODO: Use graphic for watch-indicator (with tooltip)
;; TODO: Make dirs-combo "small"
;; TODO: memoize icon functions
;; TODO: Better icons in tree and for buttons

;; TODO: Make drag be able to drop in top-level (hidden root) Complicated?!  Re-organize to allow TreeView itself to handle all DnD ...?

;; TODO: In Java 9+, we would prefer to use: moveToTrash(File f)


