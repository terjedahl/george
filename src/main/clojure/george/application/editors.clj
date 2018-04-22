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
    [clojure.core.async :refer [>!! <! chan timeout sliding-buffer thread go go-loop close!]]
    [environ.core :refer [env]]
    [george.util.singleton :as singleton]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.styled :as styled]
    [george.editor.core :as ed]
    [george.application.input :as input]
    [george.application.output :refer [oprint oprintln]]
    [george.application.ui.layout :as layout]
    [george.util :as u]
    [george.util.file :as guf]
    [george.application.file :as gaf])

  (:import
    [javafx.scene.control ToggleGroup ToggleButton ScrollPane$ScrollBarPolicy ScrollPane ContentDisplay SplitMenuButton]
    [javafx.geometry Bounds Side]
    [javafx.scene.shape Circle]
    [javafx.scene.paint Color]
    [java.nio.file Path]
    (javafx.scene Node)))


(defonce editor-buttons_
         ^{:doc "Maps ^Path-s to editor-buttons"}
         (atom {}))


(defonce current-editor-group_
         ^{:doc "Holds map of relevant data for current-editor-group"}
         (atom nil))


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


(defn- save-to-swap-channel []
  (let [c (chan (sliding-buffer 1))]  ;; we only need to run latest queued save-fn
    (go-loop []
             (<! (timeout 5000))  ;; wait 5 seconds before running next save-fn
             (let [f (<! c)]
               (when (and f (not= :halt f))
                 (f)
                 (recur))))
    c))


(defn alert-on-missing-dir [file-info_]
  (:alert-on-missing-dir? @file-info_))

(defn set-alert-on-missing-dir [file-info_ alert?]
  (swap! file-info_ assoc :alert-on-missing-dir? alert?)
  file-info_)

(defn alert-on-missing-swap [file-info_]
  (:alert-on-missing-swap? @file-info_))

(defn set-alert-on-missing-swap [file-info_ alert?]
  (swap! file-info_ assoc :alert-on-missing-swap? alert?)
  file-info_)


(defn save-to-swap
  "Returns the content that was saved.
  Does the actual save-to-swap - both for 'queue-save-to-swap' and before eval/run.
  If no f#
      then make f#, set it on info and write to it."
  [editor file-info_]
  (let [{:keys [path swap-path]} @file-info_
        p (or swap-path
              (.toPath (gaf/create-swap (.toFile path) (alert-on-missing-dir file-info_))))         
        content (ed/text editor)]
    
    (if-not (gaf/swap-file-exists-or-alert-print (.toFile p) (alert-on-missing-swap file-info_))
      (set-alert-on-missing-swap file-info_ false)
      (do
        (spit (.toFile p) content)
        (swap! file-info_ assoc :saved-to-swap? true :saved? false :swap-path p)
        (when-not (alert-on-missing-dir file-info_)
          (oprintln :out "Directory available:" (str (guf/parent-dir (.toFile p)))))
        (when-not (alert-on-missing-swap file-info_)
          (oprintln :out "Swap file available:" (str p)))

        (set-alert-on-missing-dir file-info_ true)
        (set-alert-on-missing-swap file-info_ true)))))
  
  
(defn save-to-swap-maybe [editor file-info_]
  (when-not (:saved-to-swap? @file-info_)
    (save-to-swap editor file-info_)))


(defn queue-save-to-swap
  [editor file-info_ save-chan]
  (>!! save-chan #(save-to-swap-maybe editor file-info_)))


(defn state-listener [editor labeled file-info_ save-chan]
  (let []
    (add-watch (.getStateAtom editor) labeled
               (fn [_ _ {pbuffer :buffer} {buffer :buffer}]
                 (when-not (identical? pbuffer buffer) ;; Comparing the buffers' identity is fastest
                   (if (:ignore-next-buffer-change @file-info_)
                     (swap! file-info_ dissoc :ignore-next-buffer-change)  ;; Got the signal  Now removing it.
                     (do (swap! file-info_ assoc :saved-to-swap? false :saved? false)
                         (queue-save-to-swap editor file-info_ save-chan))))))))


(def tooltipf 
  "      file:  %s
     saved:  %s
 swap-file:  %s
swap-saved:  %s")


(defn- indicate
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


(defn- indicator
  "Updates the file-name and status in the tab whenever file-info_ changes."
  [labeled file-info_]
  (indicate labeled @file-info_)
  (add-watch file-info_ :indicator
             (fn [_ _ _ file-info]
               (fx/later (indicate labeled file-info)))))


(defn- ^Node new-close-x []
  (let [x-circle
        (Circle. 8 8 8 Color/GAINSBORO)
        x-pane
        (fx/stackpane
          x-circle
          (fx/new-label "X" :size 10 :color Color/DIMGRAY :tooltip "Close editor"))]

    (doto x-pane
      (.setOnMouseExited (fx/event-handler (.setFill x-circle Color/GAINSBORO)))
      (.setOnMouseEntered (fx/event-handler (.setFill x-circle Color/ORANGERED)))
      (.setOnMousePressed
        (fx/event-handler-2
          [_ ev]
          (let [button (-> ev .getSource .getParent)]
            (.close button true)))))))


(defn- new-file-info_ [path]
  (->
    (atom {:path path
           :swap-path nil
           :saved-to-swap? true ;; saved to swap-file? Set to false by state-listener, and then true by auto-save
           :saved? true})  ;; swapped to real file? Set to false by auto-save and then to true by save/save-as
    (set-alert-on-missing-swap true)
    (set-alert-on-missing-dir true)))


(defn- new-editor-root 
  "The layout that gets inserted into the details-area when the button is selected."
  [editor file-info_ ns-str]
  (let [
        ns-label
        (input/ns-label)
        
        update-ns-fn
        (input/set-ns-label-fn ns-label)
        _ (update-ns-fn ns-str)

        interrupt-button
        (input/interrupt-button)

        eval-button
        (doto (SplitMenuButton.)
          (.setText "Run")
          (.setPrefWidth 130)
          (.setAlignment fx/Pos_CENTER)
          (.setPopupSide Side/TOP)
          (fx/set-tooltip
            (format "Run code.   %s-R                  
Load code.  %s-L (Similar to \"Run\", but silent.)" u/SHORTCUT_KEY u/SHORTCUT_KEY)))

        bottom-menubar
        (layout/menubar false
                        ns-label
                        (fx/region :hgrow :always)
                        interrupt-button
                        eval-button)

        focusable
        (.getFlow editor)

        do-eval-fn
        (fn [load?]
          (save-to-swap-maybe editor file-info_)
          (input/do-eval
            (ed/text editor)
            eval-button
            interrupt-button
            #(.getText ns-label)
            update-ns-fn
            (.getFileName (:path @file-info_))
            focusable
            nil
            load?))

        root
        (fx/borderpane
          :center editor
          :bottom bottom-menubar)]

    (doto eval-button
      (fx/set-onaction  #(do-eval-fn nil))
      (-> .getItems (.addAll (fxj/vargs (layout/menu [:item "Load" #(do-eval-fn true)])))))


    root))


(definterface IEditorButton3
  (getFileInfoAtom [])
  (getEditor [])
  (rename [new_path])
  (close [allow_save]))


(defn- new-editor-button 
  "new-editor-button extends ToggleButton implments IEditorButton"
  [path togglegroup scrollpane container & {:keys [ns] :or {ns "user"}}]
  
  (let [
        content
        (slurp (.toFile path))
        
        editor
        (ed/editor-view content :clj)
        
        file-info_
        (new-file-info_ path)

        save-chan 
        (save-to-swap-channel)

        button
        (proxy [ToggleButton IEditorButton3] [(str (.getFileName path)) (new-close-x)] 
          (fire []
            (when (or (not (.getToggleGroup this)) (not (.isSelected this)))
              (ensure-scrolled-visible  scrollpane this)
              (proxy-super fire)))
          (getFileInfoAtom [] file-info_)
          (getEditor [] editor)
          (rename [new-path]
            (prn 'rename (str new-path))
            (swap! editor-buttons_ assoc new-path this)
            (swap! editor-buttons_ dissoc (:path @file-info_))
            (swap! file-info_ assoc :path new-path :swap-path nil :saved? true :saved-to-swap? true)
            (fx/later (.setText this (str (.getFileName new-path)))))
              
          (close [allow-save?]
            (println " Do some work before closing button (saving and such) ...")
            (let [box (.getParent this)]
              (fx/set-onaction this #(do))
              (.setCenter container (fx/new-label "No tab selected"))
              (-> togglegroup .getToggles (.remove this))
              (fx/remove box this)
              (swap! editor-buttons_ dissoc (:path @file-info_)))))

        editor-root
        (new-editor-root editor file-info_ ns)]
    
    (state-listener editor button file-info_ save-chan)
    (indicator button file-info_)

    (doto button
      (.setContentDisplay ContentDisplay/RIGHT)  
      (.setGraphicTextGap 14)
      (.setFocusTraversable false)
      (.setToggleGroup togglegroup)
      (fx/set-onaction #(.setCenter container editor-root)))

    (swap! editor-buttons_ assoc path button)

    button))


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

        ;buttons
        ;(map #(new-editor-button % togglegroup buttonscrollpane container)  (fx/names-list))
        
        root
        (fx/borderpane
          :top
          buttonscrollpane
          :center
          container)]

    ;(apply fx/add-all (cons buttonpane buttons))
    
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
        ;:alwaysontop true
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
      ;(prn 'button button)
      (fx/later (.fire button)))
    
    (let [{:keys [togglegroup buttonpane buttonscrollpane container] :as bg} @current-editor-group_]
      (if-not bg
        (println "WARNING! No editors/@current-editor-group_")
        (let [button (new-editor-button path togglegroup buttonscrollpane container)]
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

;(when (env :repl?) (println "WARNING: Running george/get-or-create-stage" (get-or-create-stage true)))
