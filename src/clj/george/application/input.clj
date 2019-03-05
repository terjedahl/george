;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.input
  (:require
    [clojure.string :as cs]
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.application.history :as hist]
    [george.application.repl :as repl]
    [george.util :as gu]
    [george.application.output :refer [oprintln]]
    [george.application.eval :as eval]
    [george.editor.core :as ed]
    [common.george.util.platform :as pl]
    [george.application.ui.styled :as styled])
  (:import
    [javafx.scene.input KeyEvent MouseEvent]
    [java.net SocketException]
    [javafx.scene.control Button SplitPane ListView ListCell]
    [javafx.geometry Orientation]
    [javafx.util Callback]
    [javafx.scene Node]
    [javafx.scene.paint Color]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn- request-focus [focusable]
  (try
    (fx/future-sleep-later 300 (.requestFocus focusable))
    ;; The focusable may be gone as the interrupt being a result of closing it.
    (catch NullPointerException _ nil)))


(defn do-eval [code-str ^Button run-button ^Button interrupt-button ns-fn update-ns-fn file-name focusable post-success-fn & [load?]]
  (let [
        eval-id (gu/uuid)]
    (if(cs/blank? code-str)
      (do
        (println)
        (request-focus focusable))
      (do
        ;; update UI
        (fx/later
          (.setDisable run-button true)
          (doto interrupt-button
                (.setDisable  false)
                (fx/set-onaction
                  #(do (repl/interrupt-eval eval-id)
                       (oprintln :system-em "Interrupted!")))))
        ;; do execution
        (future
          (try
            (eval/read-eval-print-in-ns code-str (ns-fn) eval-id file-name update-ns-fn load?)
            (when post-success-fn (post-success-fn))
            ;; handle possible problems
            (catch SocketException e
              (oprintln :err (format "%s: %s" (.getClass e) (.getMessage e)))
              (oprintln :err "  ... possibly due to session or server restart."))
            (catch Exception e
              (println "ØØØØØØØØH")
              (.printStackTrace e))

            (finally
              ;; Update UI
              (fx/later
                (.setDisable interrupt-button true)
                (.setDisable run-button false)
                (request-focus focusable)))))))))


(defn history-wrapper
  "Handles history, then calls passed-in eval-fn"
  [repl-uuid current-history-index_ code-str eval-fn]
  (hist/append-history repl-uuid code-str)
  (reset! current-history-index_ -1)
  (eval-fn))


(defn- run-tooltip [clearable?]
  (if clearable?
    (format
      "Run code, then clear if checkbox ckecked.                  %s-ENTER
Run code, then do the inverse of checkbox selection. SHIFT-%s-ENTER" (pl/shortcut-key) (pl/shortcut-key))
    (format "Run code.  %s-ENTER."  (pl/shortcut-key))))


(defn run-button [& [clearable?]]
  (fx/button "Run" :width 130 :tooltip (run-tooltip clearable?)))


(defn interrupt-button []
  (doto
    (fx/button "X" :width 30 :tooltip "Interrupt current 'Run'")
    (.setDisable true)))


(defn set-ns-label-fn [label]
  (fn [ns]
    (fx/later
      (doto label
        (.setText ns)
        (fx/set-tooltip (str "*ns* " ns))))))


(defn new-input-root- [file-name & {:keys [ns]}]
  (let [repl-uuid (gu/uuid)
        current-history-index_ (atom -1)

        ns-label
        (styled/ns-label)

        update-ns-fn
        (set-ns-label-fn ns-label)
        _ (update-ns-fn (or ns "user"))

        editor
        (ed/editor-view "" "clj")

        focusable
        (.getFlow editor)

        do-history-fn
        (fn [direction global?]
          (hist/do-history editor repl-uuid current-history-index_ direction global?))

        interrupt-button
        (interrupt-button)

        run-button
        (run-button true)

        clear-checkbox
        (doto
          (fx/checkbox "Clear"
                       :tooltip
                       "Clear on 'Run'. Code is cleared after successful evaluation.")
          (.setStyle "-fx-padding: 3px;")
          (.setFocusTraversable false))

        do-clear-fn
        (fn [inverse-clear]  ;; do the opposite of clear-checkbox
          (let [clear-checked
                (.isSelected clear-checkbox)
                do-clear
                (if inverse-clear (not clear-checked) clear-checked)]
            (when do-clear
              (fx/later (ed/set-text editor "")))))

        do-eval-fn
        (fn [code-str inverse-clear]
            (history-wrapper
              repl-uuid
              current-history-index_
              code-str
              (fn []
                  (do-eval
                    code-str
                    run-button
                    interrupt-button
                    #(.getText ns-label)
                    update-ns-fn
                    file-name
                    focusable
                    #(do-clear-fn inverse-clear)))))

        on-closed-fn
        #(.fire interrupt-button)

        prev-button
        (doto (styled/small-button
                (str  \u25C0)  ;; up: \u25B2,  left: \u25C0
                :tooltip
                "Previous local history.         CLICK
Previous global history.  SHIFT-CLICK")
          (.setFocusTraversable false)
          (.setOnMouseClicked
              (fx/new-eventhandler
                (do-history-fn hist/PREV (.isShiftDown ^MouseEvent event))
                (.consume event))))

        next-button
        (doto (styled/small-button
                (str \u25B6)  ;; down: \u25BC,  right: \u25B6
                :tooltip
                "Next local history.         CLICK
Next global history.  SHIFT-CLICK")
          (.setFocusTraversable false)
         (.setOnMouseClicked
             (fx/new-eventhandler
                (do-history-fn hist/NEXT (.isShiftDown ^MouseEvent event))
                (.consume event))))

        bottom
        (doto
          (fx/hbox
            ns-label
            (fx/region :hgrow :always)
            prev-button next-button
            (fx/region :hgrow :sometimes)
            clear-checkbox interrupt-button run-button
            :spacing 3 :padding 5 :alignment fx/Pos_CENTER_LEFT)
          (.setBorder (styled/new-border [1 0 0 0])))

        border-pane
        (fx/borderpane
          :center editor :bottom bottom :insets [5 2 0 0])

        get-code-fn
        #(ed/text editor)

        key-pressed-handler
        (fx/key-pressed-handler
          {
           #{:SHORTCUT :ENTER}        #(.fire run-button)
           #{:SHIFT :SHORTCUT :ENTER} #(when-not (.isDisabled run-button)
                                         (do-eval-fn (get-code-fn) true))
           #{:SHORTCUT :ESCAPE}       #(.fire interrupt-button)})]

    (fx/set-onaction run-button #(do-eval-fn (get-code-fn) false))
    (.addEventFilter border-pane KeyEvent/KEY_PRESSED key-pressed-handler)

    [border-pane on-closed-fn editor]))


(defn- inputs-listcell [_]
  (eval
    `(proxy [ListCell] []
       (updateItem [item# empty?#]
         (proxy-super updateItem item# empty?#)
         (doto ~'this (.setText  nil) (.setGraphic (when (and (not empty?#) (some? item#)) (:listable item#))))))))


(defn- create [listview ns]
  (let  [[root on-closed-fn editor] (new-input-root- "some-file-name" :ns ns)]
    (-> listview .getItems
        (.add {:listable (fx/text (format " %2d " (hist/next-repl-nr))
                                  :font (fx/new-font fx/ROBOTO_MONO 14)
                                  :color Color/DARKSLATEGRAY)
               :root     root
               :editor   editor
               :onclosed on-closed-fn}))
    (-> listview .getSelectionModel .selectLast)))


(defn- item-selected [item input-pane]
  (.setCenter  input-pane (:root item))
  (when-let [e (:editor item)]
    (fx/future-sleep-later 200 (.focus e))))


(defn new-input-root [& {:keys [ns]}]
  (let [container
        (fx/borderpane :center (fx/new-label "No input selected"))

        listview
        (doto
          (ListView.)
          (.setCellFactory (reify Callback (call [_ param] (inputs-listcell param))))
          (.setFocusTraversable false))

        create-button
        (doto
          (fx/button "+" :tooltip "New Input editor" :onaction #(create listview ns))
          (.setFocusTraversable false))

        remove-button
        (doto
          (fx/button "-" :tooltip "Delete selected Input editor"
                         :onaction #(when-let [item (-> listview .getSelectionModel .getSelectedItem)]
                                      ((:onclosed item)) (-> listview .getItems (.remove item))))
          (.setFocusTraversable false))

        left
        (doto
          (fx/borderpane :center listview :bottom (fx/hbox create-button remove-button :padding 2 :spacing 2))
          (SplitPane/setResizableWithParent false))

        splitpane
        (doto
          (SplitPane. (into-array Node (list left container)))
          (.setOrientation Orientation/HORIZONTAL))]

    ;; Why do I have to set both a mouseclicklistener and a changelistener?  :-(
    (doto listview
      (-> (.setOnMouseClicked
            (fx/new-eventhandler (-> event .getSource .getSelectionModel .getSelectedItem (item-selected container)))))
      (-> .getSelectionModel .selectedItemProperty
          (fx/add-changelistener (item-selected new-value container))))

    (fx/future-sleep-later 200 (.setDividerPosition splitpane 0 0.1))
    (.fire create-button)

    splitpane))


(defn new-input-stage [& [ns]]
  (future (repl/session-ensure! true))
  (fx/now
    (doto (fx/stage
            :title "Inputs"
            :scene (doto (fx/scene (new-input-root  :ns ns) :size [600 300])
                     (fx/add-stylesheets "styles/codearea.css"))))))


;;; DEV ;;;


;(when (env :repl?) (println "Warning: Running george.application.input/new-input-stage") (new-input-stage))
