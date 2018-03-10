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
    [george.util :refer [->Labeled labeled?] :as gu] ;; This also loads defmethod diff/patch
    [george.application.ui.layout :as layout]
    [george.application.ui.styled :as styled]
    [hara.io.watch]
    [hara.common.watch :as watch])
  (:import
    [java.io IOException]
    [java.nio.file Files Paths Path LinkOption StandardCopyOption NoSuchFileException]
    [javafx.geometry Orientation Pos]
    [javafx.scene Cursor SnapshotParameters]
    [javafx.scene.control TreeView TreeItem TreeCell ScrollBar MenuItem ContextMenu]
    [javafx.scene.input TransferMode ClipboardContent]
    [javafx.scene.paint Color]
    [javafx.util Callback]
    [java.nio.file.attribute FileAttribute]))

(def ILLEGAL_CHARS
  (set "<>*?:`'\"/|\\"))


(defn illegal-chars [s]
  (filter ILLEGAL_CHARS (seq s)))


(defn ^String filename [path]
  (str (.getFileName path)))


(defn to-path [s & args]
  (Paths/get s (into-array String args)))


(defn to-string [path]
  (-> path .toAbsolutePath str))


(defn is-dir [path]
  (Files/isDirectory path (make-array LinkOption 0)))


(defn- filename-lowercased [p]
  (cs/lower-case (filename p)))


(defn alphabetized [paths]
  (sort-by filename-lowercased paths))


(defn exists [path]
  (Files/exists path (into-array [LinkOption/NOFOLLOW_LINKS])))


(defn not-hidden [paths]
  (filter #(not (Files/isHidden %)) paths))


(defn not-special-hidden [paths]
  (filter #(not= ".DS_Store" (filename %) ) paths))


(defn- get-child-paths [parent-path & [include-hidden? include-special?]]
  (->  parent-path
       Files/newDirectoryStream
       vec
       (#(if include-hidden? 
           (if include-special? 
             (identity %)
             (not-special-hidden %)) 
           (not-hidden %)))
       alphabetized))


(defn- item->path->string [item]
  (-> item .getValue to-string))


(defn- treecell->path [treecell]
  (-> treecell .getTreeItem .getValue))


;; TODO: better icons?
(defn file-image [] 
  (fx/imageview "graphics/file-16.png"))
(defn file-clj-image []
  (fx/imageview "graphics/file-clj-16.png"))
(defn  folder-image [] 
  (fx/imageview "graphics/folder-16.png"))


(declare lazy-filetreeitem)


(defn- move-file [source-path target-path]
  (try
    (Files/move source-path
                target-path
                (fxj/vargs StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE))
    (catch IOException e (.printStackTrace e))))


(defn- get-those-paths [event]
  ;(to-path (.getString (.getDragboard event))))
  (map #(.toPath %) 
       (-> event .getDragboard .getFiles)))

(defn- will-receive? [this-path that-path]
  (let [dir? (is-dir this-path)
        that-parent (.getParent that-path)
        same-parent? (try (Files/isSameFile this-path that-parent) (catch NoSuchFileException _ false))
        same? (try (Files/isSameFile this-path that-path) (catch NoSuchFileException _ false))]
    ;(println "  ##    this-path:" (to-string this-path))
    ;(println "  ##         dir?:" dir?)
    ;(println "  ##    that-path:" (to-string that-path))
    ;(println "  ##  that-parent:" (to-string that-parent))
    ;(println "  ## same-parent?:" same-parent?)
    ;(println "  ##        same?:" same?)
    (and dir? (not same-parent?) (not same?))))


(defn- colliding-path 
  "Returns the child-path that collides with that-path by filename, else nil"
  [that-path this-path]
  (let [child-paths (get-child-paths this-path)]
    (when-let [p (get (set (map filename child-paths)) (filename that-path))]
      (to-path (to-string this-path) p))))


(defn warn-of-existing [path]
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


(defn mark-dropspot [treecell receiving?]
  (let [w [1 0 1 0]]
    (doto treecell
      (.setBorder 
        (fx/new-border (if receiving? Color/BLUE Color/TRANSPARENT) w)))))


(defn make-dropspot [treecell treeitem]
  ;; TODO: Ensure that children are also "ghosted" when dragging
  (mark-dropspot treecell false)

  (.setOnDragOver treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
      (let [this-path (.getValue treeitem)
            ;that-path (get-that-path event)
            those-paths (get-those-paths event)]
        (when (will-receive-all? this-path those-paths)
          (.acceptTransferModes event (fxj/vargs TransferMode/MOVE))))))
          ;(.consume event)))))
  
  (.setOnDragEntered treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
      
      ;(prn 'db-ct (-> event .getDragboard .getContentTypes (.contains DataFormat/FILES)))
      ;(doseq [f (-> event .getDragboard .getFiles)]
      ;  (prn 'f f))
      ;(println " drag-entered:" (to-string (.getValue treeitem)))
      (when (will-receive-all? (.getValue treeitem) (get-those-paths event))
            ;(println "marking dir" (.getValue treeitem))
            (mark-dropspot treecell true))
      (.consume event)))

  (.setOnDragExited treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
       ;(println "un-marking dir" (.getValue treeitem))
       (mark-dropspot treecell false)
       (.consume event)))

  ;; TODO: make receiving folder selected in view
  (.setOnDragDropped treecell
    (fx/event-handler-2 [_ event]
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
        (fx/event-handler-2 [_ me] (reset! press-XY (fx/XY me))))

      ;(.setOnMouseDragged
      ;  (fx/event-handler-2 [_ me] (.consume me)))

      (.setOnDragDetected
        (fx/event-handler-2 [_ me]
           ;(println "starting drag: " treecell)
           (let [db
                 (.startDragAndDrop treecell (fxj/vargs TransferMode/MOVE))
                 cc
                 (doto (ClipboardContent.) 
                       ;(.putString (to-string path))
                       (.putFiles [(.toFile path)]))

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
             (.consume me))))

      (.setOnDragDone
        (fx/event-handler-2 
          [_ me]
          (.setOpacity treecell 1.0)
          (.setCursor treecell Cursor/DEFAULT)
          ;(prn 'onDragDone me)
          ;(prn 'mode (.getTransferMode me))
          (when (.getTransferMode me)
            (try (.refresh (.getParent treeitem)) 
                 (catch NullPointerException _)))
          (.consume me))))))


(defn- make-doubleclickable [treecell]
  (.setOnMouseClicked 
    treecell
    (fx/event-handler-2 
      [_ e]
      (let [path (treecell->path treecell)]
        (when (and  (not (is-dir path)) 
                    (= (.getClickCount e) 2))
              (println "FILE double-click:" (filename path)))))))


(defn- set-context-menu [treecell]
  (let [cm-dir
        (ContextMenu.
          (into-array
            [(doto (MenuItem. "Refresh")
                   (fx/set-onaction
                     #(-> treecell .getTreeItem .refresh)))]))             
        
        cm-handler
        (fx/event-handler-2 [_ e]
                            (.show cm-dir treecell
                                   (.getScreenX e)
                                   (.getScreenY e)))]
    
    (if (is-dir (-> treecell .getTreeItem .getValue))
        (.setOnContextMenuRequested treecell cm-handler))))


(defn path-treecell
  "Returns a custom TreeCell.
  '->str' is optional 1-arg function which takes at item and returns a String."
  []
  (proxy [TreeCell] []
    (updateItem [^Path path empty?]
      (proxy-super updateItem path empty?)
      (if (or (nil? path) empty?)
        (doto this
          (.setGraphic nil)
          (.setText nil)
          (fx/set-tooltip nil))
        ;; else
        (let [dir? (is-dir path)]
          (doto this
            (.setGraphic (if dir? (folder-image) 
                                  (if (.endsWith (filename path) ".clj")
                                      (file-clj-image)
                                      (file-image))))
            (.setText (filename path))
            (fx/set-tooltip (to-string path))
            (make-doubleclickable)
            (make-draggable)
            (make-dropspot (.getTreeItem this))
            (set-context-menu)))))))

(defn path-treecell-factory
  "Returns a custom Callback."
  []
  (reify Callback
    (call [_ _]
      (path-treecell))))


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
    (.setAll  children (fxj/vargs* (map #(lazy-filetreeitem %) paths)))))


(definterface IRefreshable
  (refresh []))


(defn lazy-filetreeitem [path]
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
            ;(prn 'refresh (to-string path))
            (when (and dir? @get-children-called_)
                  (refresh-item this))))]
    item))

;; TODO: implement tooltip for files (path mod-date, etc.)
;; TODO: implement context-menu on items (open/open in tab (for folders), edit, delete.
;; TODO: detect action - i.e. double-click or CTRL-O or CTRL-ENTER


(defn tree-root [^Path path]
  (doto (lazy-filetreeitem path)
        (.setExpanded true)))


(defn- tree-listener [current-item_]
  (fx/changelistener [_ _ _ item]
     (when item
       (let [path (-> item .getValue)]
         (reset! current-item_ item)
         (if (is-dir path)
           (println "DIR:" (filename path))
           (println "FILE:" (filename path))))))) 


(defn- get-scrollbar [treeview]
  (let [nodes (.lookupAll treeview ".scroll-bar")]
    (first (filter #(and (instance? ScrollBar %)
                         (= (.getOrientation %) Orientation/VERTICAL))
                    nodes))))


(defn- make-autoscrolling [treeview]
  (.setOnDragOver treeview
    (fx/event-handler-2 [_ me]
      (let [y (.getY me)
            bounds (.getBoundsInLocal treeview)
            h (.getHeight bounds)
            want-to-scroll? (not (< 50 y (- h 50)))
            up? (< y 50)]
        (when want-to-scroll?
          (when-let [scrollbar (get-scrollbar treeview)]
            (.setUnitIncrement scrollbar 1)
            (if up? (.decrement scrollbar)
                    (.increment scrollbar))))))))


(defn- set-watch [path current-watch_ root]
  (when-let [f @current-watch_]
    (watch/remove f f))
  (reset! current-watch_ (.toFile path))
  (watch/add
    @current-watch_ ;; file
    current-watch_ ;; use the atom itself as watch-key
    (fn [_ _ _ v]
      ;(prn 'v v)
      (.refresh root))
    {:types #{:create :modify :delete}
     :recursive true
     :exclude [".DS_Store"]}))


(defn- file-tree [^Path path current-item_ current-watch_]
  (let [root (tree-root path)]
    (reset! current-item_ root)
    (set-watch path current-watch_ root)
    
    (doto (TreeView. root)
          (.setCellFactory (path-treecell-factory))
          (make-autoscrolling)
          (-> .getSelectionModel 
              .selectedItemProperty 
              (.addListener (tree-listener current-item_))))))


(defn- set-filetree [borderpane path current-item_ current-watch_]
  (.setCenter borderpane (file-tree path current-item_ current-watch_)))


(defn- panel-folder [{:keys [label value]} borderpane current-item_ current-watch_]
  ;(reset! current-item_ value)
  (fx/new-label
    label
    :graphic (folder-image)
    :mouseclicked #(set-filetree borderpane value current-item_ current-watch_)))


(defn delete-path 
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


(defn- delete-dialog [current-item_]
  (let [
        path (.getValue @current-item_)
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
            
    (println res)    
    (when (= res 1)
      (delete-path path)
      ;(Thread/sleep 200)  ;; Allow current-item_ to be updated
      (-> @current-item_ .getParent .refresh))))
      ;; TODO: In Java 9, we would prefer to use: moveToTrash(File f)
      ;; https://docs.oracle.com/javase/9/docs/api/java/awt/Desktop.html#moveToTrash-java.io.File-


(defn new-rename-dialog [current-item_ new?]
  (let [path
        (.getValue @current-item_)
        
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

        alert
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
    
    (doto name-field   
      (.requestFocus)
      (.selectAll)
      (-> .textProperty (.addListener (fx/changelistener [_ _ _ v] (do-checks)))))

    (when (fx/option-index (.showAndWait alert) options)
      (println "new-rename-dialog res:" (.getText name-field))
      (let [p (to-path parent-str (.trim (.getText name-field)))]
        (if new?
          (if (.isSelected folder-checkbox)
            (Files/createDirectory p (make-array FileAttribute 0))
            (Files/createFile p (make-array FileAttribute 0)))
          ;; TODO: move file if not new?
          (move-file path p))
        ;; TODO: refresh folder in tree-view!
        (-> @current-item_ .getParent .refresh)))))


(defn- file-nav []
  (let [
        current-item_ 
        (atom nil)
        current-watch_
        (atom nil)

        root
        (fx/borderpane)
        
        specials 
        [
         (->Labeled "George" (to-path (env :user-home) "Documents" "George"))
         (->Labeled "Desktop" (to-path (env :user-home) "Desktop"))
         (->Labeled "Documents" (to-path (env :user-home) "Documents"))
         (->Labeled "Home" (to-path (env :user-home)))]

        panel
        (apply 
          fx/vbox
          (concat
            (map #(panel-folder % root current-item_ current-watch_) specials)
            [:spacing 5          
             :insets [5 20 5 20]]))    
        
        new-button
        (fx/button "New ..."
                   :tooltip "Create a new file or folder in current folder"
                   :onaction #(new-rename-dialog current-item_ true))

        rename-button
        (fx/button "Rename ..."
               :tooltip "Rename selected file or folder"
               :onaction #(new-rename-dialog current-item_ false))
        
        delete-button
        (fx/button "Delete ..."
                   :tooltip "Delete selected file or folder"
                   :onaction #(delete-dialog current-item_))
        menubar
        (layout/menubar 
          true
          new-button rename-button delete-button)

        paths-set (set (map :value specials))]

    (add-watch current-item_ :file-nav 
               #(do 
                  ;(prn 'current-item_ (to-string (.getValue %4)))
                  (doseq [b [rename-button delete-button]] 
                    (.setDisable b (boolean (paths-set (.getValue %4)))))))
                                    
    (doto root
      (.setTop menubar)
      (.setLeft panel)
      (set-filetree (-> specials first :value) current-item_ current-watch_))))
     

(defn stage [root]
  (let []
    (fx/later
      (fx/stage
        :title "File navigator"
        :scene (fx/scene root)
        :tofront true
        :size [500 500]
        :sizetoscene false))))

;;; 

;(when (env :repl?) (stage (file-nav))) 


