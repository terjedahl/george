;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.editor.core
  (:require
    [clojure.pprint :refer [pprint]]
    [george.javafx :as fx]
    [george.util.java :as j]
    [george.editor.buffer :as b]
    [george.editor.state :as st]
    [george.editor.view :as v]
    [george.editor.input :as i]
    [george.editor.formatters.core :as formatters])
  (:import
    [org.fxmisc.flowless VirtualFlow VirtualizedScrollPane]
    [javafx.scene.input KeyEvent]
    [clojure.lang Atom]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(declare set-content-type)


(defn- text-size-handler [state_]
  (fx/new-eventhandler
    (let [ch (.getCharacter event)
          shortcut? (.isShortcutDown event)]
      (when (and (#{"+" "-"} ch) shortcut?)
        (let [inc? (= "+" ch)]
          ;(prn 'text-size-handler 'inc? inc?)
          (st/font-size-step state_ inc?))
        (.consume event)))))


(defn- editor
 ([s typ]
  (let [
        [buf nl-str] (b/new-buffer s)
        state_ (st/new-state-atom buf nl-str typ)
        _ (swap! state_ formatters/set-formatters_)
        scroll-offset_  (atom 0.0)

        ;; The cell needs access to it's containing flow - to communicate with other cells
        flow_ (atom nil)

        flow
        (VirtualFlow/createVertical
          (st/observable-list state_)
          (j/function (partial v/new-line-cell state_ scroll-offset_ flow_)))

        _ (reset! flow_ flow)

        ;;  Needs to get some information from 'flow'
        ;; (and from clicked-in 'cell') before determining appropriate action.
        mouse-event-handler 
        (i/mouse-event-handler flow (partial st/mouseaction state_))]
  
    (add-watch state_ :ensure-scrolled-to-caret
               #(when (or (not= (:caret %3) (:caret %4))
                          (not= (:anchor %3) (:anchor %4)))
                      (v/ensure-scrolled-to-caret flow %4)))

    (doto flow

      ;; Important! Otherwise the flow can not receive events.
      (.setFocusTraversable true)
      (fx/add-stylesheet "styles/editor.css")
      (fx/add-class "editor-area")
      (-> .breadthOffsetProperty (fx/add-changelistener (reset! scroll-offset_ new-value))) ;; offset

      (.addEventHandler KeyEvent/ANY (i/key-event-handler (partial st/keypressed state_) (partial st/keytyped state_)))
      
      (.setOnMousePressed mouse-event-handler)
      (.setOnMouseDragged mouse-event-handler)
      (.setOnMouseDragOver mouse-event-handler)
      (.setOnMouseReleased mouse-event-handler)
      (.setOnMouseDragReleased mouse-event-handler)

      ;; to re-layout so as to ensure-visible on caret after flow has been made visible.
      (-> .widthProperty
          (fx/add-changelistener  (when (and (zero? ^double old-value) (pos? ^double new-value))
                                    (swap! state_ assoc :triggering-hack :hacked))))
      (-> .focusedProperty 
          (fx/add-changelistener  (if new-value  (st/start-blink state_) (st/stop-blink state_))))

      (.addEventFilter KeyEvent/KEY_TYPED (text-size-handler state_)))
    
    [flow state_])))


(definterface IEditorPane
  ^Atom        (getStateAtom [])
  ^VirtualFlow (getFlow [])
  ^void        (focus []))


(defn editor-view
 "Returns a subclass of VirtualizedScrollPane.

 No args, or 'content-string' and optional 'content-type'.

 'content-type' can be 'nil' (for plain text)
 or (currently) one of: :clj or \"clj\".

 'content-type' will effect formatting and coloring."

 ([]
  (editor-view "" nil))
 ([^String content-string & [content-type]]
  (let [[flow state_] (editor content-string content-type)]
    (proxy [VirtualizedScrollPane IEditorPane] [flow]
      (getStateAtom [] state_)
      (getFlow [] flow)
      (focus [] (.requestFocus flow))))))

(defn text [editor-view]
  (-> editor-view .getStateAtom st/text))


(defn set-text [editor-view ^String txt]
  (-> editor-view .getStateAtom (st/set-text txt)))


(defn set-content-type [editor-view type-str-or-kw]
  (let [state_ (.getStateAtom editor-view)]
    (swap! state_
           #(-> %
                (st/set-content-type_ type-str-or-kw)
                formatters/set-formatters_))
    editor-view))