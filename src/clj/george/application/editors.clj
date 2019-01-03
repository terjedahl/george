;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
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
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.ui.styled :as styled]
    [george.editor.core :as ed]
    [george.application.input :as input]
    [george.application.output :refer [oprint oprintln]]
    [george.application.ui.layout :as layout]
    [george.util :as u]
    [george.util.file :as guf :refer [->file ->path ->string filename]]
    [george.application.file :as gaf]
    [clojure.java.io :as cio])
  (:import
    [javafx.scene.control SplitMenuButton]
    [javafx.geometry Side]
    [javafx.scene.input KeyEvent]
    [javafx.scene.text TextFlow Text]
    [java.util List]))


(defn save-to-swap-channel []
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


(declare save-to-swap-maybe)


(defn save
  "If f
    then rename f# to f.  (making f# disappear and f be overwritten by f#)
    else (no f)  switch to save-as.
  Returns f if save was ok, else nil.
  "
  [editor file-info_]
  (save-to-swap-maybe editor file-info_)
  ;(prn 'save)
  (let [{:keys [saved? path swap-path]} @file-info_]
    (if saved?
      path
      (when 
        (try 
          (cio/copy (->file swap-path) (->file path))
          (guf/delete swap-path)
          true
          (catch Exception e (.printStackTrace e) 
            false))
        
        (swap! file-info_ assoc :saved? true :swap-path nil)
        path))))


(defn save-to-swap
  "Returns the content that was saved.
  Does the actual save-to-swap - both for 'queue-save-to-swap' and before eval/run.
  If no f#
      then make f#, set it on info and write to it."
  [editor file-info_]
  (let [{:keys [path swap-path]} @file-info_
        p (or swap-path
              (->path (gaf/create-swap (->file path) (alert-on-missing-dir file-info_))))         
        content (ed/text editor)]
    ;(prn 'save-to-swap)    
    (if-not (gaf/swap-file-exists-or-alert-print (->file p) (alert-on-missing-swap file-info_))
      (set-alert-on-missing-swap file-info_ false)
      (do
        (spit (->file p) content)
        (swap! file-info_ assoc :saved-to-swap? true :saved? false :swap-path p)
        (when-not (alert-on-missing-dir file-info_)
          (oprintln :out "Directory available:" (str (guf/parent p))))
        (when-not (alert-on-missing-swap file-info_)
          (oprintln :out "Swap file available:" (str p)))

        (set-alert-on-missing-dir file-info_ true)
        (set-alert-on-missing-swap file-info_ true)))

    (save editor file-info_)))
  

(defn save-to-swap-maybe [editor file-info_]
  (when-not (:saved-to-swap? @file-info_)
    (save-to-swap editor file-info_)))


(defn queue-save-to-swap
  [editor file-info_ save-chan]
  (>!! save-chan #(save-to-swap-maybe editor file-info_)))


(defn state-listener [editor labeled file-info_ save-chan]
  (add-watch (.getStateAtom editor) labeled
             (fn [_ _ {pbuffer :buffer} {buffer :buffer}]
               (when-not (identical? pbuffer buffer) ;; Comparing the buffers' identity is fastest
                 (if (:ignore-next-buffer-change @file-info_)
                   (swap! file-info_ dissoc :ignore-next-buffer-change)  ;; Got the signal  Now removing it.
                   (do (swap! file-info_ assoc :saved-to-swap? false :saved? false)
                       (queue-save-to-swap editor file-info_ save-chan)))))))


(defn new-file-info_ [path]
  (->
    (atom {:path path
           :swap-path nil
           :saved-to-swap? true ;; saved to swap-file? Set to false by state-listener, and then true by auto-save
           :saved? true})  ;; swapped to real file? Set to false by auto-save and then to true by save/save-as
    (set-alert-on-missing-swap true)
    (set-alert-on-missing-dir true)))


;; TODO: memoize
(defn- new-eval-bar [editor file-info_ ns-str]
  (let [
        ns-label
        (styled/ns-label)

        update-ns-fn
        (input/set-ns-label-fn ns-label)
        _ (update-ns-fn ns-str)

        interrupt-button
        (input/interrupt-button)

        eval-button
        (doto (SplitMenuButton.)
          (.setText "Load")
          (.setPrefWidth 130)
          (.setAlignment fx/Pos_CENTER)
          (.setPopupSide Side/TOP)
          (fx/set-tooltip
            (format "Load code: %s-L       (Silent)                     
 Run code: %s-ENTER   (Verbose)" u/SHORTCUT_KEY u/SHORTCUT_KEY)))
        
        bar
        (layout/menubar false
                        ns-label
                        (fx/region :hgrow :always)
                        interrupt-button
                        eval-button)
        focusable
        (.getFlow editor)
        
        eval-fn
        (fn [load?]
          (save editor file-info_)
          (input/do-eval
            (ed/text editor)
            eval-button
            interrupt-button
            #(.getText ns-label)
            update-ns-fn
            (str (.getFileName (:path @file-info_)))
            focusable
            nil
            load?))

        do-eval-fn #(eval-fn false)
        do-load-fn #(eval-fn true)]
        
    (doto eval-button
      (fx/set-onaction  do-load-fn)
      (-> .getItems (.addAll (fxj/vargs (layout/menu [:item "Run" do-eval-fn])))))

    {:eval-bar bar
     :do-eval-fn do-eval-fn
     :do-load-fn do-load-fn
     :do-interrupt-fn #(.fire interrupt-button)}))   


(defn- path-indicate [textflow file-info]
  (let [path      (:path file-info)
        path-str  (->string path)
        nam       (filename path)
        dir-txt   (doto (Text. (-> path str (subs 0 (- (count path-str) (count nam)))))
                        (.setStyle "-fx-font-size: 14; -fx-fill: gray;"))
        nam-txt   (doto (Text. nam)
                        (.setStyle "-fx-font-size: 14; -fx-fill: black;"))]
    (-> textflow .getChildren (.setAll ^List [dir-txt nam-txt]))))


(defn- new-editor-bar [editor file-info_ reveal-fn]
  (let [
        save-fn
        #(save editor file-info_)
        
        path-textflow
        (doto (TextFlow.)
              (fx/set-padding 3 6 0 6))
        save-button
        (styled/small-button "Save"
                             :onaction save-fn
                             :tooltip "Save unsaved changes to file.")
        
        reveal-button
        (styled/small-button "<-"
                             :onaction reveal-fn
                             :tooltip "Reveal in file tree.")
        bar
        (fx/hbox reveal-button path-textflow save-button
                 :spacing 5
                 :padding 10
                 :alignment fx/Pos_CENTER_LEFT)]

    (add-watch file-info_ :path-indicator 
               #(fx/later (path-indicate path-textflow %4)
                          (.setVisible save-button (not (:saved? %4)))))

    (path-indicate path-textflow @file-info_)
    (.setVisible save-button false)
                                                   
    {:editor-bar bar
     :do-save-fn save-fn}))


(defn new-editor-root 
  "The layout that gets inserted into the details-area when the button is selected."
  [editor file-info_ ns-str reveal-fn]
  (let [
        {:keys [editor-bar do-save-fn]}
        (new-editor-bar editor file-info_ reveal-fn)
        
        {:keys [eval-bar do-eval-fn do-load-fn do-interrupt-fn]}
        (new-eval-bar editor file-info_ ns-str)
        
        root
        (fx/borderpane
          :top    editor-bar
          :center editor
          :bottom eval-bar)]

    ;; TODO: pass-in Node which event-filter should be attached.
    ;; Alternatively, return event-filter which can be attached/un-attached ...
    ;; Or something else ...?
    (doto root
      (.addEventFilter KeyEvent/KEY_PRESSED
                       (fx/key-pressed-handler {
                                                ;#{:O :SHORTCUT}        open-fn
                                                #{:S :SHORTCUT}        do-save-fn
                                                #{:L :SHORTCUT}        do-load-fn
                                                #{:ENTER :SHORTCUT}    do-eval-fn
                                                #{:ESCAPE :SHORTCUT}   do-interrupt-fn})))
    
    root))


;;; DEV ;;;

;(when (env :repl?) (println "Warning: Running george/get-or-create-stage" (get-or-create-stage true)))
