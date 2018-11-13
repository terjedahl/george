;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.editor.view
  (:require
    [clojure.pprint :refer [pprint]]
    [george.javafx.java :as fxj]
    [george.javafx :as fx]
    [george.editor.state :as st]
    [george.util :as u]
    [george.util.text :as ut])
  (:import
    [org.fxmisc.flowless Cell VirtualFlow]
    [javafx.scene.text Text]
    [javafx.scene.layout Region StackPane Pane]
    [javafx.geometry Pos Insets BoundingBox Bounds]
    [javafx.scene Node Group]
    [javafx.scene.shape Ellipse Rectangle ArcType StrokeLineCap]
    [javafx.scene.control Label]
    [java.util List]
    [javafx.collections ObservableList]
    [javafx.scene.canvas Canvas] 
    [javafx.scene.paint Color]))
  

;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(def gutter-font (memoize #(fx/new-font "Source Code Pro" %)))
(def font (memoize #(fx/new-font "Source Code Pro Medium" %)))
(def LINE_INSETS (fx/insets 0 24 0 12))

(def derived-line-height (memoize (fn [^double font-size] (int (+ (*  font-size 1.5) 2)))))
(def derived-tab-width (memoize (fn [^double font-size] (/ font-size 2.0))))


(defn- ^Node selection-background-factory [^double w ^double h c]
  (let [rect (fx/rectangle :size [(inc w) h])]
    (-> rect .getStyleClass (.add "selection"))
    (cond
      (= c \newline)
      (doto
        ^StackPane
        (fx/stackpane
           (doto (Ellipse. w (/ h 2))
             (-> .getStyleClass (.add "selection")))
           rect)
        (.setAlignment Pos/CENTER_LEFT))

      :default
      rect)))


(defn- ^Rectangle anchor-factory [height]
  (doto (fx/rectangle :size [0.5 height])
        (fx/add-class "caret")))
        

(defn- caret-factory [height]
  (doto (fx/rectangle :size [3 height])
        (fx/add-class "caret")))


(definterface IRowCell
  ^int    (getCellColumn [^double offset-x])
  ;; returns "absolute" offset - compensating for scrolling
  ^double (getCellOffsetX [^int col])
  ;; offset directly from IScrollableText. Used by blocks.
  ^double (getCellMaxOffsetXforBlocks [])
  ^double (getCellGutterWidth [])
  ^int    (getCellIndex []))


(definterface IScrollableText
  ^int    (getTextColumn [^double offset-x])
  ;; Returns offsets relative to itself.
  ^double (getTextOffsetX [^int col])
  ^double (getTextMaxOffsetXforBlocks []))


(definterface IGutter
  ^double (getGutterWidth [])
          (setGutterText [^String s]))


(defn- new-row-gutter
  "AKA (left) margin, graphic, number-row.
  Let's use this as a general data-carrier for the line"
  [line-height]
  (let [nr-label
        (doto (Label.)
          (fx/add-class "nr-label")
          (.setPrefHeight (+ 2.0 ^double line-height))
          (.setFont (gutter-font (/ ^double line-height 2))))
        root
        (doto
          (proxy [Group IGutter] [(fxj/vargs nr-label)]
            ;; Implements IGutter
            (getGutterWidth []
              (.layout ^Group this)
              (.getWidth nr-label))
            ;; Implements IGutter
            (setGutterText [s]
              (.setText nr-label s)))
          (fx/add-class "gutter"))]
    root))


(def paren-chars #{\( \) \[ \] \{ \}})


(defn- new-text [char font-size]
  (case char
    \newline
    (doto (Text. (str " " \u21A9))  ;\u23CE
      (fx/add-class "hidden"))

    \tab
    (doto (Text. (str \u21E5))
      (.setWrappingWidth (derived-tab-width font-size)))
    
    ;;default
    (doto (Text. (str char))
      (.setFont (font font-size))
      (fx/add-class (if (paren-chars char) "delim" "default")))))


(defn- layout-texts
  "Inserts chars into Text-nodes, and lays them out in parent"
  [^StackPane pane chars font-size]
  ;(prn 'layout-texts 'font-size font-size)
  (let [texts (mapv #(new-text % font-size) chars)]
    (-> pane .getChildren (.setAll ^List texts))
    (loop [i 0 x 0.0]
      (when-let [text ^Text (get texts i)]
        (recur (inc i) (-> (doto text (.setTranslateX x)) .getBoundsInParent .getMaxX))))
    texts))


(defn- calculate-offset
  "Returns the offset-x of where the mark (anchor/caret) should be inserted."
  [texts ^long col]
  (if (zero? col)
    0.0
    (try
      (-> ^Text (get texts (dec col))
          .getBoundsInParent
          .getMaxX)
      (catch NullPointerException _ 0.0))))


(defn- calculate-max-offset-for-block
  "Returns the offset-x of where the block should end.
  Should not include newline-end"
  [chars texts]
  (if (empty? chars)
    0.0
    (let [col (- (count chars) (if (ut/newline-end? chars) 3 2))]
      (try
        (-> ^Text (get texts col)
            .getBoundsInParent
            .getMaxX)
        (catch NullPointerException _ 0.0)))))


(defn- max-offset-x
  "Called only from 'max-offset-x-mem'"
  [^VirtualFlow flow first-row last-row]
  (try
    (let [cells
          (map #(.getCell flow %)  ;; May throw exception
               (range first-row (inc ^int last-row)))
          lengths
          (mapv #(.getCellMaxOffsetXforBlocks ^IRowCell %) cells)]
      (apply max lengths))
    (catch IndexOutOfBoundsException _ 0.0)
    (catch ClassCastException _ 0.0)))


(defn max-offset-x-mem
  "Implements a memoize functionality, but using an atom from state, which gets reset whenever blocks reset."
  [^VirtualFlow flow first-row last-row mem_]
  (let [k [first-row last-row]
        x (@mem_ k)]
    (if x
        x
        (let [x (max-offset-x flow first-row last-row)]
          (swap! mem_ assoc k x)
          x))))


(defn- delim-type [ch]
  (case ch
    \( :paren
    \[ :square
    \{ :curly
    nil))
    

(defn- find-block-spans [ranges mem_ row flow texts]
  (let [;; Get the x-offsets for start and end.
        spans
        (map 
          #(let [{[frow fcol] :first [lrow lcol] :last [fch _] :chars } %
                 first?  (= row frow)
                 last?   (= row lrow)
                 start-x (calculate-offset texts fcol)
                 end-x   (if last?
                           (calculate-offset texts (inc ^int lcol))
                           (max-offset-x-mem flow frow lrow mem_))]
             [start-x end-x first? last? (delim-type fch)])
           ranges)]
    spans))


(defn snap
  "Ensure that a number is a n.5 of the int base.  
  Used to avoid fuzzy odd-width lines in canvas.
  Alternatively use .setStrokeType StrokeType/INSIDE or StrokeType/OUTSIDE"
  [value]
  (+ (int value) 0.5))


(defn- ^Canvas block-line [w h first? last? typ]
  ;(prn 'block-line w h first? last? typ)
  (let [w (int w)
        h (int h)
        square? (= typ :square)
        paren? (= typ :paren)
        w (int w)  ;; ensure a clean width
        c (Canvas. w h)
        gc (.getGraphicsContext2D c)
        r (if square? 0 4)  ;; arc radius - for lines
        d (* 2 r)  ;; arc diameter - used in the arc method
        x1 (if paren? 4 5)
        y1 0
        y1f (snap (if paren? (* h 0.75) (* h 0.85)))
        x2 (- w (if paren? 3 4))
        y2  h
        y2l (- y2 1)]
    (doto gc
      ;(.setStroke Color/PINK)
      ;(.strokeRect 0 0 w h)
      (.setLineWidth 2)
      (.setLineCap StrokeLineCap/ROUND)
      (.setStroke Color/LIGHTSTEELBLUE))
    (when last?
      (when-not square?
        (doto gc
          (.strokeArc x1 (- y2l d) d d 180 90 ArcType/OPEN)
          (.strokeArc  (- x2 d) (- y2l d) d d 270 90 ArcType/OPEN)))
      (doto gc
        (.strokeLine (+ x1 r) y2l (- x2 r) y2l)  ;; horizontal line
        (.strokeLine x2 y1f x2 (- y2l r))))  ;; end-line
    (when first?
      (doto gc
        (.strokeLine  x1 y1f x1 (if last? (- y2l r) y2))))
    (when-not first?
      (doto gc
        (.strokeLine x1 y1 x1 (if last? (- y2l r) y2))))
    c))


(defn set-blocks [^StackPane blocks-pane all-ranges mem_ row flow texts line-height]
  (->  blocks-pane .getChildren .clear)
  (let [ranges (get all-ranges row)]
    (when-not (empty? ranges)
      (let [spans (find-block-spans ranges mem_ row flow texts)]
        (doseq [[x1 x2 first? last? typ] spans]
          (-> blocks-pane .getChildren 
              (.add 
                (doto (block-line (- x2 x1) line-height first? last? typ)
                  (fx/set-translate-XY [(int x1) 0])))))))))


(defn- set-marks
  "Inserts and lays out markings (caret, anchor, select) if any, on the passed-in pane."
  [^StackPane pane {:keys [caret anchor caret-pos anchor-pos lines caret-visible]} row chars texts line-height]
  (let [
        [crow ccol] caret-pos
        [arow acol] anchor-pos

        [low ^int high] (sort [caret anchor])
        do-mark? (partial u/in-range? low (dec high))

        ^int row-index (st/location->index-- lines [row 0])]

    (loop [i 0 x 0.0]
      (when-let [text ^Text (get texts i)]
        (let [w (-> text .getBoundsInParent .getWidth)]
          (when (do-mark? (+ row-index i))
            (let [marking (selection-background-factory w line-height (chars i))]
              (.setTranslateX ^Node marking (- x 0.5)) ;; offset half pixel to left
              (-> pane .getChildren (.add marking))))
          (recur (inc i) (+ x w)))))

    (when (and (= arow row) caret-visible)
      (let [anchor (anchor-factory line-height)]
        (.setTranslateX anchor (- ^double (calculate-offset texts acol) 0.25))
        (-> pane .getChildren (.add anchor))))

    (when (and (= crow row) caret-visible)
      (let [caret ^Node (caret-factory line-height)]
        (.setTranslateX caret (- ^double (calculate-offset texts ccol) 1.0)) ;; negative offset for cursor width
        (-> pane .getChildren (.add caret))))))


(defn- highlight-row [^StackPane pane current-row? line-height]
  (doto pane
    (fx/re-add-class "row")
    (fx/remove-class "current-row")
    (fx/remove-class "default-row")
    (fx/add-class (if current-row? "current-row" "default-row"))
    (.setMaxHeight (dec ^double line-height))))


(defn- set-marks-and-line
  "If the row is in the set, then delegates the task"
  [line-background-pane
   ^StackPane marks-pane
   {:keys [current-row-p? marked-row-p?] :as derived}
   row chars texts line-height]

  (highlight-row line-background-pane (current-row-p? row) line-height)

  (->  marks-pane .getChildren .clear)
  (when (marked-row-p? row)
    (set-marks ^StackPane marks-pane derived row chars texts line-height)))


(defn- calculate-col [^double offset-x char-nodes]
  (if (neg? offset-x)
    0
    (loop [col 0 x 0.0   nodes char-nodes]
      (if-let [text ^Text (first nodes)]
        (let  [w (-> text .getBoundsInParent .getWidth)
               xw (+ x w)]
          (if (<= x offset-x xw) ;; Do we have a hit?
            (if (> offset-x (+ x (/ w 2))) ;; Round up if right of center of node
              (inc col)
              col)
            (recur (inc col) xw (rest nodes))))
        col))))  ;; Ran out of nodes.  Just return what we have.


(defn ensure-scrolled-to-caret [^VirtualFlow flow state]
  (let [line-height (-> state :font-size (derived-line-height))
        [^long row col] (:caret-pos state)
        cell (.getCell flow row)
        ;; The "absolute" offset (of the caret) - i.e. number of pixels from the left of the flow
        ^double offset-x (.getCellOffsetX ^IRowCell cell col)
        ^double gutter-w (.getCellGutterWidth ^IRowCell cell)
        ;; How much has been scrolled
        ^double scrolled-x (-> flow .breadthOffsetProperty .getValue)
        flow-w (.getWidth flow)
        ;; The width between the gutter and the right side of the flow - i.e. the visible text area width
        main-w (- flow-w gutter-w)
        ;; We want to keep the caret from touching the outer borders of 'main-w'
        visible-padding 12.0
        ;; Is the caret visible between the right of the gutter and and the right edge of the flow?
        col-visible? (< (+ gutter-w visible-padding) offset-x (- flow-w visible-padding))
        ;; If we need to scroll (horizontally),
        ;; we need want to pass inn a bounding-box which should be made visible.
        ;; This works (through thinking + trial-and-error). Please update this comment with a logical explanation.
        bounding-x (- (+ offset-x scrolled-x) gutter-w (/ main-w 3))
        ;; It should be as wide as the 'main-w'
        bounding-w main-w
        bounding-box (BoundingBox. bounding-x 0 bounding-w line-height)

        ;; We also want the caret to be vertically visible.
        ;; And we don't want it to reach the very top or bottom row if avoidable.
        ;; So get the current first and last visible rows.
        visible-cells (.visibleCells flow)
        ^int first-visible-row (.getCellIndex ^IRowCell (first visible-cells))
        ^int last-visible-row (.getCellIndex ^IRowCell (last visible-cells))]

    (when-not col-visible?
      ;; Scroll horizontally.
      (.show flow row bounding-box))
    (when (<= row (inc first-visible-row))
      ;; Scroll up.
      (.show flow (dec row)))
    (when (>= row (dec last-visible-row))
      ;; Scroll down.
      (.show flow (inc row)))))


(defn- new-scrolling-part [gutter text-pane marks-pane blocks-pane scroll-offset_ chars texts line-height]
  (let [
        insets ^Insets LINE_INSETS
        inset-left (.getLeft  insets)
        inset-right (.getRight insets)
        texts-width (if (empty? texts) 0.0 (.getMaxX ^Bounds (.getBoundsInParent ^Text (last texts))))
        pref-width (+ inset-left texts-width inset-right)

        scrolling-pane
        (doto
          (proxy [StackPane IScrollableText] [(fxj/vargs marks-pane blocks-pane text-pane)]
            ;; Implements IScrollableText
            (getTextColumn [^double offset-x] ;; offset-x already considers scrolled offset
              (let [^double  gw (.getGutterWidth gutter)
                    offset (- offset-x gw inset-left)
                    col
                    (if (<  (- offset-x ^double @scroll-offset_) gw) ;; offset-x is in/under in gutter.
                      :gutter
                      (calculate-col offset texts))]
                col))
            ;; Implements IScrollableText
            (getTextOffsetX [col]
              (+ inset-left
                 ^double (calculate-offset texts col)))
            ;; Implements IScrollableText
            (getTextMaxOffsetXforBlocks []
             (+ inset-left
                ^double (calculate-max-offset-for-block chars texts))))

          (.setAlignment Pos/CENTER_LEFT)
          (.setPrefHeight line-height)
          (.setPrefWidth pref-width)
          (.setPadding insets))]

    scrolling-pane))


(defn new-line-cell [state_ scroll-offset_ flow_ chars]
  (if (= chars u/*DEL_OBJ*)
    (Cell/wrapNode (Rectangle.))  ;; A minimum cell which will be disposed of anyways. For speed.
    (let [k (Object.)

          row_ (atom -1)

          font-size (:font-size @state_)
          line-height (derived-line-height font-size)
          
          line-background-pane
          (Pane.)

          gutter
          (new-row-gutter line-height)

          set-gutter-text
          #(.setGutterText gutter ((:line-count-formatter @state_) (inc ^int @row_)))

          text-pane
          (doto ^StackPane (fx/stackpane)
            (.setAlignment Pos/CENTER_LEFT))

          texts
          (layout-texts text-pane chars font-size)

          marks-pane
          (doto ^StackPane (fx/stackpane)
            (.setAlignment Pos/CENTER_LEFT))

          blocks-pane
          (doto ^StackPane (fx/stackpane)
            (.setAlignment Pos/CENTER_LEFT))

          scrolling-part
          (new-scrolling-part gutter text-pane marks-pane blocks-pane scroll-offset_ chars texts line-height)
          
          node
          (proxy [Region] []
            ;; @override
            (computeMinWidth [^double _]
              (.computePrefWidth this -1.0))
            ;; @override
            (computePrefWidth [^double _]
              (.layout ^Region this)
              (let [insets  ^Insets (.getInsets ^Region this)]
                (+ ^double (.getGutterWidth gutter)
                   (.prefWidth ^Region scrolling-part -1.0)
                   (.getLeft  insets)
                   (.getRight insets))))
            ;; @override
            (computePrefHeight [^double _]
              (.layout ^Region this)
              line-height)
            ;; @override
            (layoutChildren []
              (let [[^double w h] (-> ^Region this .getLayoutBounds fx/WH)
                    gw ^double (.getGutterWidth gutter)
                    go @scroll-offset_]
                (.resizeRelocate ^StackPane scrolling-part gw 0 (- w gw) h)
                (.resizeRelocate ^Region gutter go 0 gw h)
                (.resizeRelocate  line-background-pane go 0 w h))))]

      (->  node ^ObservableList .getChildren (.setAll ^List (list line-background-pane scrolling-part gutter)))

      (add-watch scroll-offset_ k (constantly (.requestLayout node)))

      (add-watch 
        state_ k
        (fn [_ _ {prev-digits :line-count-digits prev-blocks :blocks} {digits :line-count-digits blocks :blocks :as state}]
          (when (not= prev-digits digits) 
            (set-gutter-text))
          (set-marks-and-line line-background-pane marks-pane state @row_ chars texts line-height)
          (when (not= prev-blocks blocks) 
            (set-blocks blocks-pane (:block-ranges state) (:max-offset-x-mem_ state) @row_ @flow_ texts line-height))
          (.requestLayout node)))

      (reify
        Cell
        ;; implements
        (getNode [_] node)
        ;; implements
        (updateIndex [_ index]
          (when (not= @row_ index) ;; only update box if index changes
            (reset! row_ index)
            (set-gutter-text)
            (set-marks-and-line line-background-pane marks-pane @state_ @row_ chars texts line-height)
            (set-blocks blocks-pane (:block-ranges @state_) (:max-offset-x-mem_ @state_) @row_ @flow_ texts line-height)
            (.requestLayout node)))
        ;; implements
        (dispose [_]
          (remove-watch scroll-offset_ k)
          (remove-watch state_ k))
        
        IRowCell
        ;; implements
        (getCellColumn [_ offset-x] (.getTextColumn scrolling-part offset-x))
        ;; implements
        (getCellOffsetX [_ col]
          (-
           (+ ^double (.getGutterWidth gutter)
              ^double (.getTextOffsetX scrolling-part col))
           ^double @scroll-offset_))
        ;; implements
        (getCellMaxOffsetXforBlocks [_] (.getTextMaxOffsetXforBlocks scrolling-part))
        ;; implements
        (getCellGutterWidth [_] (.getGutterWidth gutter))
        ;; implements
        (getCellIndex [_] @row_)))))
