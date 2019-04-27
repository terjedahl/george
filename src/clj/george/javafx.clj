;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.javafx
  (:require
    [clojure.string :as cs]
    [clojure.java.io :as cio]
    [common.george.util.cli :refer [debug warn info except]]
    [george.util :as u]
    [common.george.util.platform :as pl])
  (:import
    [javafx.animation Timeline KeyFrame KeyValue]
    [javafx.application Platform]
    [javafx.beans.value ChangeListener WritableValue]
    [javafx.collections FXCollections ObservableList ListChangeListener]
    [javafx.event EventHandler]
    [javafx.geometry Insets Pos VPos Side Orientation]
    [javafx.scene Group Node Scene]
    [javafx.scene.control
     Alert Alert$AlertType
     Button ButtonType ButtonBar$ButtonData
     Label
     ListView RadioButton
     TextField TextArea
     Tooltip
     ScrollPane CheckBox ScrollBar]
    [javafx.scene.image ImageView]
    [javafx.scene.input MouseEvent KeyEvent Clipboard ClipboardContent]
    [javafx.scene.layout
     BorderPane HBox Priority Region StackPane VBox
     Border
     BorderStroke BorderStrokeStyle CornerRadii BorderWidths Background BackgroundFill GridPane Pane FlowPane]
    [javafx.scene.paint Color Paint]
    [javafx.scene.text Font Text FontPosture FontWeight]
    [javafx.scene.shape Line Rectangle Polygon StrokeLineCap StrokeType]
    [javafx.stage FileChooser FileChooser$ExtensionFilter Screen Stage StageStyle Modality]
    [javafx.util Duration]
    [java.util Collection Optional List]
    [clojure.lang Atom]
    [javafx.fxml FXMLLoader]
    [javafx.beans Observable]
    [java.awt SplashScreen]
    [javafx.embed.swing JFXPanel]
    [org.kordamp.ikonli.javafx FontIcon]))


"
Notes on JavaFX

Certain classes touch the native JavaFX runtime system, and must not be used in type-hinting function - args or return - 
as they will require init-ing the runtime at compile time.
The same goes for proxy-ing them directly, as proxy is a macro which will touch them at compile-time.
See my comments here:  https://dev.clojure.org/jira/browse/CLJ-1743

The includes (but is not limited to):
  ListView
  TreeView
  ScrollPane
  ComboBox
  ListCell
  TreeCell
  TextField
  TextArea
  Label
  Button
  RadioButton
  CheckBox  
  Screen  
"


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)
;(set! *unchecked-math* true)


(defn set-implicit-exit [& [b]]
  (debug (str "george.javafx/set-implicit-exit " b))
  (Platform/setImplicitExit (boolean b)))


(defn set-classloader [cl]
  (let [p (promise)]
    (Platform/runLater #(deliver p (or (.setContextClassLoader (Thread/currentThread) cl) true)))
    @p))


;; Fonts need to be loaded early, for where fonts are called for in code, rather than in CSS.
(defn preload-fonts [& [verbose?]]
  (debug "george.javafx/preload-fonts")
  (let [dir-path (str (cio/resource "fonts/"))
        list-path "fonts/fonts.txt"
        names (cs/split-lines (slurp (cio/resource list-path)))]
    (doseq [n names]
      (when verbose? (print " " n " ->  "))
      (->
        (str dir-path n)
        (cs/replace "%20" " ")
        (Font/loadFont 10.)
        (#(when verbose? (-> % str println)))))))


(def init
  "An easy way to 'initialize [JavaFX] Toolkit'
Needs only be called once in the applications life-cycle.
Has to be called before the first call to/on FxApplicationThread (javafx/later)

Memoize-ing it makes it effectively lazy and run only once (unless new/different parameters are passed).
Add any additional random key+value to trigger a new load (as this triggers a new run of the memoize fn)."
  (memoize
    (fn [& {:keys [fonts? classloader] :or {fonts? true}}]
      (debug "george.javafx/init")

      (try
        (JFXPanel.)
        (catch Throwable t (except (.getMessage t))))

      (set-implicit-exit false)
    
      (when classloader
        (set-classloader classloader))
    
      (when fonts?
        (preload-fonts (= fonts? :verbose)))

      true)))


;;;;;;;;;


(defn web-color [s & [opacity]]
  (Color/web s (if opacity opacity 1.0)))


;; A nice combo for black text on white background
(def ANTHRECITE (Color/web "#2b292e")) ;; Almost black
(def WHITESMOKE Color/WHITESMOKE)  ;; off-white

(def RED Color/RED)
(def WHITE Color/WHITE)
(def BLUE Color/BLUE)
(def GREEN Color/GREEN)
(def BLACK Color/BLACK)
(def THREES (Color/web "#333"))
(def DEES (Color/web "#ddd"))
(def EEES (Color/web "#eee"))

(def BLUE Color/BLUE)
(def GREY Color/GREY)


(def Pos_TOP_LEFT Pos/TOP_LEFT)
(def Pos_TOP_RIGHT Pos/TOP_RIGHT)
(def Pos_TOP_CENTER Pos/TOP_CENTER)
(def Pos_CENTER Pos/CENTER)
(def Pos_CENTER_LEFT Pos/CENTER_LEFT)
(def Pos_CENTER_RIGHT Pos/CENTER_RIGHT)
(def Pos_BOTTOM_LEFT Pos/BOTTOM_LEFT)
(def Pos_BOTTOM_RIGHT Pos/BOTTOM_RIGHT)

(def VPos_TOP VPos/TOP)
(def VPos_CENTER VPos/CENTER)

(def MouseEvent_ANY MouseEvent/ANY)

(def HORIZONTAL Orientation/HORIZONTAL)
(def VERTICAL Orientation/VERTICAL)


(def OPEN_SANS              "Open Sans")    ;; preferably use in body text
(def ROBOTO                 "Roboto")       ;; preferably use in headers and labels
(def ROBOTO_MONO            "Roboto Mono")  ;; preferably use in non-code

(def SOURCE_CODE_PRO        "Source Code Pro")        ;; preferably use in connection to code
(def SOURCE_CODE_PRO_MEDIUM "Source Code Pro Medium") ;; preferably use in actual code


(defn ^CornerRadii corner-radii [rad]
  (when rad
    (if (vector? rad)
      (let [[tl tr br bl] rad] (CornerRadii. tl tr br bl false))
      (CornerRadii. rad))))


(defn ^Background color-background [^Paint color & [rad insets]]
    (Background. ^"[Ljavafx.scene.layout.BackgroundFill;"
                 (into-array (list (BackgroundFill. color (corner-radii rad) insets)))))


(defn set-background [^Region r paint-or-background]
  (if (instance? Background paint-or-background)
    (.setBackground r  paint-or-background)
    (if (instance? Paint paint-or-background)
      (.setBackground r (color-background paint-or-background))
      (throw (IllegalArgumentException.
               (format "Don't know how to convert %s to javafx.scene.layout.Background" paint-or-background))))))


(defn fxthread? []
  (Platform/isFxApplicationThread))


(defn later*
    "Utility function for 'thread'."
    [expr]
    (if (fxthread?)
      (try (expr) (catch Throwable e e (println e)))
      (Platform/runLater #(try (expr) (catch Throwable e e (println e))))))


(defmacro later
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater"
    [& body]
    `(later* (fn [] ~@body)))


(defmacro future-later [& body]
  `(future (later* (fn [] ~@body))))


(defmacro future-sleep-later [ms & body]
  `(future (Thread/sleep ~ms) (later* (fn [] ~@body))))


(defn now*
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'"
    [expr]
    (if (fxthread?)
        (expr)
        (let [result (promise)]
            (later
                (deliver result (try (expr) (catch Throwable e e (println e)))))

            @result)))


(defmacro now
    "Ensure running body in JavaFX thread: javafx.application.Platform/runLater, but returns result. Prefer using 'later'."
    [& body]
    `(now* (fn [] ~@body)))


(defmacro ^EventHandler new-eventhandler
          "Returns an instance of javafx.event.EventHander.
        Evaluates body in handle-method.  'this' and 'event' are implicitly."
          [& body]
          `(reify EventHandler (~'handle [~'this ~'event] ~@body)))


(defn ^EventHandler ensured-handler [f]
  (if (instance? EventHandler f) f (new-eventhandler (f))))


(defmacro ^ChangeListener changelistener
  "Returns an instance of javafx.beans.value.ChangeListener,
where args-vec is a vector of 4 elements  - naming the bindings for 'this', 'observable', 'old', 'new',
and the body is called on 'changed'"
  [args-vec & body]
  (assert (vector? args-vec) "First argument must be a vector representing 4 args")
  (assert (= 4 (count args-vec)) "args-vector must contain 4 elements - for binding 'this', 'observable', 'old', 'new'")
  `(reify ChangeListener (~'changed ~args-vec ~@body)))


(defmacro ^ChangeListener new-changelistener [& body]
  `(reify ChangeListener (~'changed [~'this ~'observable ~'old-value ~'new-value] ~@body)))


(defmacro add-changelistener [^Observable observable & body]
  `(.addListener ~observable (reify ChangeListener (~'changed [~'this ~'observable ~'old-value ~'new-value] ~@body))))


(defmacro ^ListChangeListener new-listchangelistener [& body]
  `(reify ListChangeListener (~'onChanged [~'this ~'change] ~@body)))


(defn ^ObservableList children [parent]
  (.getChildren parent))


(defn children-set [parent index kid]
  (.set ^ObservableList (.getChildren parent) index kid)
  parent)


(defn children-set-all [parent ^List kids]
  (.setAll ^ObservableList (.getChildren parent) ^List kids)
  parent)


(defn children-add
 ([parent kid]
  (.add ^ObservableList (.getChildren parent) kid)
  parent)
 ([parent index kid]
  (.add ^ObservableList (.getChildren parent) index kid)
  parent))


(defn children-add-all [parent ^List kids]
  (.addAll ^ObservableList (.getChildren parent) ^List kids)
  parent)


(defn children-clear [parent]
  (.clear ^ObservableList (.getChildren parent))
  parent)


(defn children-remove [parent kid-or-int]
  (.remove ^ObservableList (.getChildren parent) kid-or-int)
  parent)


(defn children-remove-all [parent kids]
  (.removeAll ^ObservableList (.getChildren parent) ^List kids)
  parent)


(defn XY [item]
  [(.getX item) (.getY item)])


(defn WH [item]
  (if (instance? Node item)
      (WH (.getBoundsInParent item))
      [(.getWidth item) (.getHeight item)]))


(defn get-translate-XY [^Node node]
    [(.getTranslateX node) (.getTranslateY node)])


(defn set-translate-XY [^Node node [x y]]
  (when (some? x) (.setTranslateX node (double x)))
  (when (some? y) (.setTranslateY node (double y)))
  node)


(defn set-WH [item [w h]]
  (when (some? w) (.setWidth item (double w)))
  (when (some? h) (.setHeight item (double h)))
  item)


(defn set-XY [item [x y]]
  (when (some? x) (.setX item (double x)))
  (when (some? y) (.setY item (double y)))
  item)


(defn set-min-WH [node [w h]]
  (when (some? w) (.setMinWidth node (double w)))
  (when (some? h) (.setMinHeight node (double h)))
  node)


(defn set-pref-WH [node [w h]]
  (when (some? w) (.setPrefWidth node (double w)))
  (when (some? h) (.setPrefHeight node (double h)))
  node)


(defn set-max-WH [node [w h]]
  (when (some? w) (.setMaxWidth node (double w)))
  (when (some? h) (.setMaxHeight node (double h)))
  node)


(defn set-all-WH [node [w h :as size]]
  (doto node
    (set-min-WH size)
    (set-pref-WH size)
    (set-max-WH size)))


(defn new-border
    ([color]
     (new-border color 1.))
    ([color width]
     (new-border color width 0.))
    ([color width rad]
     (Border. ^"[Ljavafx.scene.layout.BorderStroke;"
       (into-array
         (list
           (BorderStroke. color
                          BorderStrokeStyle/SOLID
                          (corner-radii rad)
                          (if (vector? width)
                              (let [[t r b l] width] (BorderWidths. t r b l))
                              (BorderWidths. width))))))))


(defn get-userdata [^Node n]
  (.getUserData n))


(defn set-userdata [^Node n m]
  (.setUserData n m)
  m)


(defn swap-userdata [^Node n f & args]
  (let [res (apply f (cons (get-userdata n) args))]
    (set-userdata n res)))


(defn load-fxml [pth]
  (.load (FXMLLoader. (cio/resource pth))))


(defn lookup [node id]
  "Similar to Node.lookup, but goes recursively through all children, and returns the first found or nil"
  ;	(println "lookup  node:" node "  id:" id)
  ;	(println "\t\tnode.id:" (.getId node) "  empty?:" (empty? (.getId node)))
  ;	(println "\t\tnode.class.name:" (-> node class .getSimpleName ))
  (let [
         n-name
         (-> node class .getSimpleName)
         n-id
         (.getId node)

         [a b]
         (cs/split id #"#")
         [a b]
         [(if (empty? a) nil a) (if (empty? b) nil b)]]

     (if  ;; does this match on tpe and id, or either?
      (or
        (and
          (and a b)
          (and
            (= a n-name)
            (= b n-id)))
        (and
          a
          (not b)
          (= a n-name))
        (and
          b
          (= b n-id)))
      ;; then return node
      node
      ;; else recur over its children, if parent, else nil
      (first
        (filter identity
                (map
                  (fn [c] (lookup c id))
                  (concat
                    (try (.getChildren node) (catch Exception _))
                    (try (.getTabs node) (catch Exception _))
                    (try (-> node .getContent .getChildren) (catch Exception _))
                    (try (.getMenus node) (catch Exception _))
                    (try (.getItems node) (catch Exception _)))))))))
                       

(defn external [path]
  (.toExternalForm (cio/resource (str path))))


(defn remove-stylesheet [scene-or-parent path]
  (doto scene-or-parent
    (-> .getStylesheets (.remove (external path)))))


(defn add-stylesheet [scene-or-parent path & [refresh? delay]]
  (let [css (external path)
        ss  (.getStylesheets scene-or-parent)]
    (.add ss css)
    (when refresh?
      (future-sleep-later (or delay 2000)
        (doto ss (.remove css) (.add css))))
    scene-or-parent))


(defn remove-class [node css-class]
  (doto node
    (-> .getStyleClass (.remove (str css-class)))))


(defn add-class [node css-class]
  (doto node
    (-> .getStyleClass (.add (str css-class)))))


(defn re-add-class [node css-class]
  (doto node
    (-> .getStyleClass (.remove (str css-class)))
    (-> .getStyleClass (.add (str css-class)))))


(defn ^KeyFrame new-keyframe
  "creates an instance of Keyframe with duration (millis) and KeyValue-s from vectors of format [property value]"
  [duration onfinished & keyvalues]
  (let [d (Duration. duration)
        ah (when onfinished (new-eventhandler (onfinished)))
        kvs (mapv (fn [[p v]] (KeyValue. p v)) (filter some? keyvalues))]
    (if ah
      (KeyFrame. d "NN" ^EventHandler ah ^Collection kvs)
      (KeyFrame. d (into-array KeyValue kvs)))))


(defn ^Timeline timeline
    "creates a timeline of instances of KeyFrame"
    [onfinished-fn & KeyFrames]
    (let [t (Timeline. ^"[Ljavafx.animation.KeyFrame;" (into-array KeyFrames))]
      (when onfinished-fn
            (.setOnFinished t  (new-eventhandler (onfinished-fn))))
      t))


(defn simple-timeline
    "creates a timeline containing a single keyframe with duration (in millis),
    onfinished (or nil),
    and keyvalues as vectors as per function 'keyframe'"
    [duration onfinished & keyvalues]
    (timeline onfinished (apply new-keyframe (concat (list duration nil) keyvalues))))


(def NANO_PR_SEC
    "number of nano-seconds pr second"
    1000000000)

(def NANO_PR_MILLI
    "number of nano-seconds pr milli-second"
    1000000)

(def DEFAULT_TICKS_PR_SEC
    "default number of 'ticks' pr second"
    60)


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)

(defn synced-keyframe
    "same as 'keyframe', but runs immediately in current thread"
    [duration & keyvalues]
    (when keyvalues
        (let [
              ;;  replace [prop end] with [prop start end]
              keyvalues (map
                         (fn [[prop end]] [prop (.getValue ^WritableValue prop) end])
                         (filter some? keyvalues))

              start-nano     ^long (System/nanoTime)
              duration-nano  (* ^int duration ^int NANO_PR_MILLI)
              end-nano       (+ start-nano duration-nano)
              ^int sleep-nano     (/ ^int NANO_PR_SEC ^int DEFAULT_TICKS_PR_SEC)] ;; 60 fps

          (when (> ^int duration 0)
            (loop [current-nano start-nano
                   next-nano (+ current-nano sleep-nano)]
              (when (<= current-nano end-nano)
                (later
                  (doseq [[^WritableValue prop ^int start ^int end] keyvalues]
                    (.setValue prop
                       (+ start
                          (* ^double (/ (- current-nano start-nano) duration-nano)
                             (- end start))))))

                (let [sleep-milli (int (/ (- next-nano current-nano) ^int NANO_PR_MILLI))]
                  (if (> sleep-milli 0)
                    (Thread/sleep sleep-milli)))

                (recur next-nano (+ current-nano sleep-nano))))
            ;; correct final value and "hold" until to ensure consistent state at end
            (now (doseq [[^WritableValue p _ e] keyvalues]
                       (.setValue p e)))))))

;(set! *warn-on-reflection* false)
;(set! *unchecked-math* false)


(defn observablearraylist [& elements]
    (FXCollections/observableArrayList ^List elements))


(defn names-list []
    ["Julia", "Ian", "Sue", "Matthew", "Hannah", "Stephan", "Denise"])


; ^ListView  ;; DON'T TYPE. It touches JavaFX!
(defn listview
 ([]
  (listview (apply observablearraylist (names-list))))
 ([observable-list]
  (ListView. observable-list)))


; ^Scrollbar  ;; DON'T TYPE. It touches JavaFX!
(defn find-scrollbar [view & [horizontal?]]
  (let [nodes (.lookupAll view ".scroll-bar")]
    (first (filter #(and (instance? ScrollBar %)
                         (= (.getOrientation %) (if horizontal? HORIZONTAL VERTICAL)))
                   nodes))))


;(defn multiline-listcell
;  "Given a javafx.scene.control.ListView and a function which when passe an item, returns a string, this function returns a ListCell subclass based on javafx.scene.text.Text which can display multiple lines of text, and which wraps to fit within the width of th ListView."
;  [listview item->str-fn]
;  (proxy [javafx.scene.control.ListCell] []
;    (updateItem [item is-empty]
;      (proxy-super updateItem item is-empty)
;      (.setText this nil)
;      (.setPrefWidth this 0)
;      (if (or is-empty (nil? item))
;        (.setGraphic this nil)
;        ;; else
;        (.setGraphic this
;                     (doto (javafx.scene.text.Text. (item->str-fn item))
;                       (-> .wrappingWidthProperty (.bind (-> listview .widthProperty (.subtract 35))))))))))


;(defn multiline-listcell-factory
;  "Returns a new instance of multiline-listcell whenever called.
;  See 'multiline-listview' and 'multiline-listcell'."
;  [item->str-fn]
;  (reify javafx.util.Callback
;    (call [_ listview]
;      (multiline-listcell listview item->str-fn))))


;(defn multiline-listview
;  "The provided function takes one argument, an item, and returns a string.
;The function may be a keyword or simply 'str', or something more complex.
;It must return a string (which may be wrapped to fit the width of the list."
;  ([item->str-fn]
;   (doto (javafx.scene.control.ListView.)
;     (.setCellFactory (multiline-listcell-factory item->str-fn))))
;  ([]
;   (multiline-listview str)))


(defn priority [kw]
  ({:always Priority/ALWAYS
    :never Priority/NEVER
    :sometimes Priority/SOMETIMES} kw))


(defn ^Region region
 "optional kwargs:
    :hgrow :always/:never/:sometimes
    :vgrow :always/:never/:sometimes"

    [& {:keys [hgrow vgrow] :or {}}]

    (doto (Region.)
        (HBox/setHgrow (priority hgrow))
        (VBox/setVgrow (priority vgrow))))


(defn ^StackPane stackpane [& nodes]
  (StackPane. (into-array Node nodes)))


(defn ^Group group [& nodes]
     (Group. ^"[Ljavafx.scene.Node;" (into-array Node nodes)))


(defn ^Pane pane [& nodes]
   (Pane. (into-array Node nodes)))


(defn ^Line line [& {:keys [x1 y1 x2 y2 color width stroketype smooth round]
                     :or   {x1 0 y1 0
                            ;x2 x1 y2 y1
                            color Color/BLACK
                            width 1
                            stroketype :centered
                            smooth true
                            round false}}]
    (doto (Line. x1 y1 (or x2 x1) (or y2 y1))
      (.setStroke color)
      (.setStrokeWidth width)
      (.setSmooth smooth)
      (.setStrokeLineCap (if round StrokeLineCap/ROUND StrokeLineCap/SQUARE))
      (.setStrokeType
        (condp = stroketype
          :outside StrokeType/OUTSIDE
          :inside StrokeType/INSIDE
          StrokeType/CENTERED))))


(defn set-stroke
  ([shape stroke]
   (doto shape
     (.setStroke stroke)))
  ([shape stroke width]
   (doto shape
     (.setStrokeWidth width)
     (set-stroke stroke))))


(defn ^Polygon polygon [& args]
    (let [[points kwargs]
          (u/partition-args
              args
              {:fill Color/TRANSPARENT
               :stroke Color/BLACK
               :strokewidth 1.})]
         (doto (Polygon. (into-array Double/TYPE points))
             (.setFill (:fill kwargs))
             (set-stroke (:stroke kwargs) (:strokewidth kwargs)))))


(defn node? [item]
  (instance? Node item))


(defn ^Rectangle rectangle [& args]
    (let [default-kwargs
          {:location [0 0]
           :rotation 0
           :size [50 50]
           :fill Color/BLACK
           :arc 0}

          [_ kwargs] (u/partition-args args default-kwargs)

          location (:location kwargs)
          size (:size kwargs)
          arc (:arc kwargs)]

      (doto (Rectangle.
                (first location)
                (second location)
                (first size)
                (second size))
          (.setFill (:fill kwargs))
          (.setArcWidth arc)
          (.setArcHeight arc)
          (.setRotate (:rotation kwargs)))))


(defn set-tooltip [control s]
  (doto control (.setTooltip (Tooltip. s))))


(defn set-onaction [buttonbase fn-or-handler]
  (doto buttonbase (.setOnAction (ensured-handler fn-or-handler))))


(defn set-onmouseclicked [clickable fn-or-handler]
  (doto clickable (.setOnMouseClicked (ensured-handler fn-or-handler))))


; ^RadioButton  ;; DON'T TYPE. It touches JavaFX!
(defn radiobutton []
  (RadioButton.))


; ^Button  ;; DON'T TYPE. It touches JavaFX!
(defn new-button [s & {:keys [graphic onaction pref-WH all-WH tooltip style focusable?]}]
    (let [b (if graphic (Button. s graphic) (Button. s))]
      (when pref-WH   (set-pref-WH b pref-WH))
      (when all-WH   (set-all-WH b all-WH))
      (when onaction (set-onaction b onaction))
      (when tooltip  (set-tooltip b tooltip))
      (when style    (.setStyle b style))
      (when (some? focusable?) (.setFocusTraversable b focusable?))
      b))


(defn set-enable
      "A simple tool that is easier to reason about."
      [^Button button enable?]
      (doto button (.setDisable (not enable?))))


; ^CheckBox  ;; DON'T TYPE. It touches JavaFX!
(defn  checkbox [label & {:keys [onaction tooltip]}]
  (let [cb (CheckBox. label)]
    (when onaction (set-onaction cb onaction))
    (when tooltip (set-tooltip cb tooltip))
    cb))


(def font-postures
  {:regular FontPosture/REGULAR
   :italic  FontPosture/ITALIC})


(def font-weights
  {:normal  FontWeight/NORMAL
   :medium FontWeight/MEDIUM
   :semibold FontWeight/SEMI_BOLD
   :bold FontWeight/BOLD})


(defn new-font
 ([family-or-size]
  (if (string? family-or-size)
     (Font/font ^String family-or-size)
     (Font/font (double family-or-size))))
 ([family size]
  (Font/font family (double size)))
 ([family weight size]
  (Font/font ^String family ^FontWeight (font-weights weight) (double size)))
 ([family weight posture size]
  (Font/font family (font-weights weight) (font-postures posture) (double size))))


(defn set-font
 ([item font-or-family-or-size]
  (if (instance? Font font-or-family-or-size)
    (.setFont item font-or-family-or-size)
    (set-font item (new-font font-or-family-or-size))))
 ([item family size]
  (set-font item (new-font family size))))


(defn textfield
  [& {:keys [text font prompt cols]
      :or {text ""
           prompt ""
           font nil
           cols nil}}]
  (let [tf (doto (TextField. text) (.setPromptText prompt))]
    (when font (.setFont tf font))
    (when cols (.setPrefColumnCount tf cols))
    tf))


(defn textarea
    [& {:keys [text font prompt]
         :or {text ""
              prompt ""
              font nil}}]
    (let [ta (doto (TextArea. text) (.setPromptText prompt))]
      (when font (set-font ta font))
      ta))


(defn text [txt & {:keys [font size color width]
                     :or {size  12
                          color Color/BLACK
                          width 0}}]
  (doto (Text. txt)
    (.setFill color)
    (set-font (or font (new-font size)))
    (.setWrappingWidth width)))


; ^Label  ;; DON'T TYPE. It touches JavaFX!
(defn  new-label [s & {:keys [graphic font size color mouseclicked tooltip style]
                       :or   {size 12}}]
  (let [label (doto  (Label. s graphic) (set-font (or font (new-font ROBOTO size))))]
    (when color (.setTextFill label color))
    (when style (.setStyle label style))
    (when mouseclicked (set-onmouseclicked label mouseclicked))
    (when tooltip (set-tooltip label tooltip))
    label))


(defn insets
    ([v]
     (if (sequential? v) (apply insets v) (Insets. v)))
    ([top right bottom left]
     (Insets. top right bottom left)))


(defn set-borderpane-insets [node inset-vector]
  (BorderPane/setMargin node (insets inset-vector))
  node)


(defn set-stackpane-alignment [node pos]
  (StackPane/setAlignment node pos)
  node)


(defn set-stackpane-insets [node inset-vector]
  (StackPane/setMargin node (insets inset-vector))
  node)


(defn set-padding
    ([pane v]
     (set-padding pane v v v v))
    ([pane t r b l]
     (.setPadding pane (insets t r b l))
     pane))


(defn set-spacing [box n]
  (.setSpacing box n)
  box)


(defn set-alignment [box pos]
  (.setAlignment box pos)
  box)


(defn box [vertical? & args]
    (let [[nodes kwargs] (u/partition-args
                             (filter some? args) 
                             {:spacing 0
                              :padding 0
                              :alignment nil
                              :background nil
                              :fill? true}) ;; fill? indicates whether nodes should "fill" width/height
          
          padding (:padding kwargs) 
      
          box (if vertical?
                (VBox. (:spacing kwargs) (into-array Node nodes))
                (HBox. (:spacing kwargs) (into-array Node nodes)))]
          
      (set-alignment box (:alignment kwargs))

      (if (number? padding)
          (set-padding box padding)
          (apply set-padding (cons box padding)))      

      (when-let [b (:background kwargs)]
        (set-background box b))

      (when-not (:fill? kwargs)
        (if vertical? (.setFillWidth box false) (.setFillHeight box false)))

      box))


(defn ^HBox hbox [& args]
    (apply box (cons false args)))


(defn ^VBox vbox [& args]
    (apply box (cons true args)))


(defn flowpane [& args]
  (let [[nodes kwargs] (u/partition-args
                             (filter some? args)
                             {:vertical? false
                              :spacing 0
                              :padding 0
                              :wrap-length nil})
        spacing (:spacing kwargs)
        padding (:padding kwargs)

        pane
        (FlowPane.
          (if (:vertical? kwargs) Orientation/VERTICAL Orientation/HORIZONTAL)
          ^double spacing
          ^double spacing
          (into-array Node nodes))]

    (if (number? padding)
        (set-padding pane padding)
        (apply set-padding (cons pane padding)))

    (when-let [l (:wrap-length kwargs)]
      (.setPrefWrapLength pane l))

    pane))


(defn ^BorderPane borderpane [ & args]
    (let [
          default-kwargs
          {:center nil :top nil :right nil :bottom nil :left nil
           :padding 0}
          [_ kwargs] (u/partition-args args default-kwargs)
          padding        (:padding kwargs)
          pane
          (BorderPane. (:center kwargs) (:top kwargs) (:right kwargs) (:bottom kwargs) (:left kwargs))]

      (if (number? padding)
          (set-padding pane padding)
          (apply set-padding (cons pane padding)))

      pane))


(defn ^Scene scene [root & args]
            (let [
                  default-kwargs
                  {:size         nil ;[300 300]
                   :depthbuffer  false
                   :fill         nil
                   ;:antialiasing SceneAntialiasing/BALANCED  ;; set to nil if not required
                   :antialiasing nil}  ;; set to nil due to "upside-down" bug on Mac/Linux

                  [_ kwargs] (u/partition-args args default-kwargs)
                  size (:size kwargs)]

                (doto (if size
                          (Scene. root
                                  (double (first size))
                                  (double (second size))
                                  (:depthbuffer kwargs)
                                  (:antialiasing kwargs))
                          (Scene. root))
                    (.setFill (:fill kwargs)))))


(defn option-index
  "Returns the index of the selected option, or nil"
  [result options]
  (when (not= result (Optional/empty))
    (let [index (.indexOf options (-> result .get .getText))]
      (when (not= index -1)
        index))))


(defn ^Alert$AlertType alerttype [type-kw] 
  (let [types  
        {:none         Alert$AlertType/NONE
         :information  Alert$AlertType/INFORMATION
         :warning      Alert$AlertType/WARNING
         :confirmation Alert$AlertType/CONFIRMATION
         :error        Alert$AlertType/ERROR}]
    (if-let [typ (types type-kw)]
      typ
      (do
        (warn (format "Unkown type '%s'.  Using ':information'." type-kw))
        (types :information)))))


(defn expandable-content [^String content & [^Font font ^long pref-width]]
  ;; http://code.makery.ch/blog/javafx-dialogs-official/
  (let [ta
        (doto ^TextArea (textarea :text content :font font)
          (.setEditable false)
          (.setWrapText false)
          (.setMaxWidth Double/MAX_VALUE)
          (.setMaxHeight Double/MAX_VALUE)
          (GridPane/setVgrow Priority/ALWAYS)
          (GridPane/setHgrow Priority/ALWAYS))]

    (doto (GridPane.)
      (.setMaxWidth Double/MAX_VALUE)
      (.setPrefWidth (or pref-width 800))
      (.add ta 0 0))))


(defn ^Alert alert [& args]
  "Returns index of selected option, else nil.
  If :mode is nil, then returns the pre-shown dialog. You must then show it yourself, and process the results.

  ex: (actions-dialog \"Message\" :title \"Title\" :options [\"A\" \"B\"] :cancel-option? true)

  In this example \"A\" will return 0, \"B\" will return 1, cancel will return nil.
  "
  (let [default-kwargs {:title "Info"
                        :header nil
                        :text nil
                        :content nil
                        :expandable-content nil
                        :expanded? false
                        :options ["OK"]
                        :cancel-option? false
                        :owner nil
                        :mode :show-and-wait ;; :show-and-wait or :show or nil
                        :type :information}

        [_ {:keys [options type] :as kwargs}] (u/partition-args args default-kwargs)

        options
        (if (and (empty? options) (not (:cancel-option? kwargs)))
          (do (debug "Adding 'OK' option. (There must be one button on dialog.)" )["OK"]) options)

        buttons
        (mapv #(ButtonType. %) options)
        
        buttons
        (if (:cancel-option? kwargs)
          (conj buttons (ButtonType. "Cancel" ButtonBar$ButtonData/CANCEL_CLOSE))
          buttons)

        alert
        (doto (Alert. (if (keyword? type) (alerttype (:type kwargs)) type))
          (.setTitle (:title kwargs))
          (.initOwner (:owner kwargs))
          (.setHeaderText (:header kwargs))
          (-> .getButtonTypes (.setAll ^List buttons)))]

    (when-let [t (:text kwargs)]
      (.setContentText alert t))
  
    (when-let [c (:content kwargs)]
      (-> alert .getDialogPane (.setContent c)))

    (when-let [ec (:expandable-content kwargs)]
      (-> alert .getDialogPane (.setExpandableContent ec)))

    (-> alert .getDialogPane (.setExpanded (:expanded? kwargs)))

    ;; Workaround for JavaFX bug on Linux KDE
    ;; https://stackoverflow.com/questions/55190380/javafx-creates-alert-dialog-which-is-too-small
    ;; https://github.com/javafxports/openjdk-jfx/issues/222
    (when (pl/linux?)
      (doto alert
        (.setOnShown
          (new-eventhandler
            ;; A couple of times for good measure (in case the first call came too early).
            (doseq [t [30 100 500 1500]]
              (future-sleep-later  t (-> alert .getDialogPane .getScene .getWindow .sizeToScene)))))))

    (condp = (:mode kwargs)
      :show-and-wait (option-index (.showAndWait alert) options)
      :show          (option-index (.show alert) options)
      ;; default (nil) - simply return the dialog itself (unshown)
      alert)))


(defn close-splash []
  (when-let [splash (SplashScreen/getSplashScreen)]
    (.close splash)))


; ^Screen  ;; DON'T TYPE. It touches JavaFX!
(defn screens []
  (Screen/getScreens))


; ^Screen  ;; DON'T TYPE. It touches JavaFX!
(defn  primary-screen []
  (Screen/getPrimary))


(defn primary-WH
  "returns [x y] for center of primary screen"
  []
  (-> (Screen/getPrimary) .getVisualBounds WH))


(defn primary-center
  "returns [x y] for center of primary screen"
  []
  (let [[w h] (primary-WH)]
    [(/ ^double w 2.) (/ ^double h 2.)]))


(defn ^ImageView imageview [image-or-rsc-str & {:keys [width height preserveratio smooth]
                                                :or   {width nil
                                                       height nil
                                                       preserveratio true
                                                       smooth true}}]
  (let [iv (doto (ImageView.  image-or-rsc-str)
             (.setSmooth smooth)
             (.setPreserveRatio preserveratio))]
    (when width (.setFitWidth iv (double width)))
    (when height (.setFitHeight iv (double height)))
    iv))


(defn ^StageStyle stagestyle [style-kw]
  (let [styles {:decorated   StageStyle/DECORATED
                :transparent StageStyle/TRANSPARENT
                :undecorated StageStyle/UNDECORATED
                :unified     StageStyle/UNIFIED
                :utility     StageStyle/UTILITY}]
    (if-let [style (styles style-kw)]
      style
      (do
        (warn (format "Unknown stagestyle '%s'.  Using ':decorated'." style-kw))
        (styles :decorated)))))


(defn ^Side side [side-kw]
  (let [sides {:top    Side/TOP
               :bottom Side/BOTTOM
               :left   Side/LEFT
               :right  Side/RIGHT}]
    (if-let [side (sides side-kw)]
      side
      (do
        (warn (format "Unknown side '%s'.  Using ':bottom'." side-kw))
        (sides :bottom)))))


(defn ^Modality modality [mod-kw]
  (let [mods {:application Modality/APPLICATION_MODAL
              :window      Modality/WINDOW_MODAL
              :none        Modality/NONE}]
    (if-let [mod (mods mod-kw)] 
      mod
      (do
        (warn (format "Warning. Unknown modality '%s'.  Using ':none'." mod-kw))
        (mods :none)))))


(defn ^Stage setoncloserequest [^Stage stage fn-or-handler]
    (.setOnCloseRequest stage (ensured-handler fn-or-handler))
    stage)


(defn ^Stage setonhiding [^Stage stage fn-or-handler]
    (.setOnHiding stage (ensured-handler fn-or-handler))
    stage)


(defn ^Stage setonhidden [^Stage stage fn-or-handler]
    (.setOnHidden stage (ensured-handler fn-or-handler))
    stage)


; ^ScrollPane  ;; DON'T type. It touches JavaFX!
(defn  scrollpane [& [node]]
  (if node
    (ScrollPane. node)
    (ScrollPane.)))


(defn ^Stage stage [& args]
    (let [default-kwargs
          {:style  :decorated
           :modality nil
           :title  "Untitled stage"
           :scene nil
           :sizetoscene true
           :size nil ;[200 200]
           :location nil ;[100 100]
           :centeronowner? nil  ;; overrides 'location'  TODO: implement this!
           :centeronscreen? nil ;; overrides 'centerononwer?'
           :owner nil
           :show true
           :alwaysontop false
           :tofront false
           :resizable true
           :oncloserequest #()  ;; good for preventing closing (consume event)
           :onhiding #()  ;; good for saving content
           :onhidden #()}  ;; good for removing references to self

          [_ kwargs] (u/partition-args args default-kwargs)]

      (let [stg (doto (Stage. (stagestyle (:style kwargs)))
                    (.setTitle (:title kwargs))
                    (.setScene (:scene kwargs))
                    (.setAlwaysOnTop (:alwaysontop kwargs))
                    (.setResizable (:resizable kwargs))
                    (setoncloserequest (:oncloserequest kwargs))
                    (setonhiding (:onhiding kwargs))
                    (setonhidden (:onhidden kwargs)))]

        (when-let [mod-kw (:modality kwargs)]
          (.initModality stg (modality mod-kw)))

        (when (:sizetoscene kwargs) (.sizeToScene stg))

        (when-let [[w h] (:size kwargs)]
          (doto stg (.setWidth w) (.setHeight h)))

        (when-let [[x y] (:location kwargs)]
          (doto stg (.setX x) (.setY y)))
          
        (when-let [owner (:owner kwargs)]
          (.initOwner stg owner))

        (when-let [cos (:centeronscreen? kwargs)]
          (when cos (.centerOnScreen stg)))

        (when (:show kwargs) (.show stg))

        (when (:tofront kwargs) (.toFront stg))

        stg)))


(defn filechooserfilter [^String description & extensions]
    (FileChooser$ExtensionFilter.
      description
      ^"[Ljava.lang.String;" (into-array extensions)))


(defn filechooser-filters-clj [] 
  [(filechooserfilter "Clojure Files" "*.clj")
   (filechooserfilter "All Files"   "*.*")])


(defn filechooser-filters-png [] 
  [(filechooserfilter "PNG files" "*.png")
   (filechooserfilter "All Files"   "*.*")])


(defn ^FileChooser filechooser [& filters]
    (doto (FileChooser.) (-> .getExtensionFilters (.addAll ^Collection filters))))


(def clipboard
  (memoize #(now (Clipboard/getSystemClipboard))))


(defn clipboard-str []
  ;(println "  ## clipboard content types:" (fx/now (.getContentTypes CB)))
  (now (.getString ^Clipboard (clipboard))))


(defn set-clipboard-str [s]
  (let [cbc (doto (ClipboardContent.)
              (.putString s))]
    (now (.setContent ^Clipboard (clipboard) cbc))))


(defn char-ensured
  "Returns the char that was typed,
  else empty-flag or nil if no char was found in event,
  else undefined-flag or nil if the found char was not Character/isDefined"
  ([^KeyEvent event]
   (char-ensured event nil nil))
  ([^KeyEvent event empty-flag undefined-flag]
   (let [ch-str (.getCharacter event)]
     (if (empty? ch-str)
       empty-flag
       (let [ch (.charAt ch-str 0)]
         (if-not (Character/isDefined ch)
           undefined-flag
           ch))))))


(defn code-modifier-set
  "Returns a set of keywords (all caps) for key-code and active modifier-keys extracted form event. Useful for handling KEY_PRESSED / KEY_RELEASED events.
  Ex: #{:UP :SHORTCUT} or #{:BACKSPACE}  or #{:SHIFT :ENTER}"
  [^KeyEvent event]
  (let [code (str (.getCode event))
        shift (when (.isShiftDown event) "SHIFT")
        shortcut (when (.isShortcutDown event) "SHORTCUT")  ;; CTRL / CMD / "C-"
        alt (when (.isAltDown event) "ALT")] ;;  "M-"
    (set (map keyword (filter some? [code shift shortcut alt])))))


(defn char-modifier-set
  "Similar to code-modifier-set, but in stead of code, returns char+modifier-keywrods. Useful for handling KEY_TYPED events. Returns an empty set with :CHARACTER_UNDEFINED or :CHARACTER_EMPTY if that is the case.
  Ex: #{:SHORTCUT \\a} or #{\\A}"
  [^KeyEvent event]
  (let [ch-str (.getCharacter event)]
    (if (empty? ch-str)
      #{:CHARACTER_EMPTY}
      (let [ch (.charAt ch-str 0)]
        (if-not (Character/isDefined ch)
          #{:CHARACTER_UNDEFINED}
          (let [
                shift (when (.isShiftDown event) "SHIFT")
                shortcut (when (.isShortcutDown event) "SHORTCUT")
                alt (when (.isAltDown event) "ALT")]
            (set (conj (map keyword (filter some? [shift shortcut alt])) ch))))))))


;(def sample-codes-map {
;                       #{:S} #(println "S")
;                       #{:S :SHIFT} #(println "SHIFT-S")
;                       #{:S :SHORTCUT} #(println "CTRL-S")
;                       #{:S :ALT} #(println "ALT-S")
;                       #{:S :SHIFT :SHORTCUT} (new-eventhandler (println "SHIFT-CTRL/CMD-S"))
;                       #{:SHORTCUT :ENTER} (new-eventhandler (println "CTRL/CMD-ENTER") (.consume event))})


;(def sample-chars-map {
;                       "a" #(println "a")
;                       "A" #(println "A")
;                       " " (new-eventhandler (println "SPACE (consumed)") (.consume event))})


(defn key-pressed-handler
 "Takes a map where the key is a set of keywords and the value is a no-arg function to be run or an instance of EventHandler.

The keywords int the set must be uppercase and correspond to the constants of javafx.scene.input.KeyCode.
Use :SHIFT :SHORTCUT :ALT for platform-independent handling of these modifiers (CTRL maps to Command on Mac).
If the value is a function, then it will be run, and then the event will be consumed.
If the value is an EventHandler, then it will be called with the same args as this handler, and it must itself consume the event if required.

See:  https://docs.oracle.com/javase/8/javafx/api/javafx/scene/input/KeyCode.html

Example of codes-map:
{   #{:S}              #(println \"S\")  ;; event consumed
    #{:S :SHIFT}       #(println \"SHIFT-S\")
    #{:S :SHIFT :SHORTCUT} (fx/new-eventhandler (println \"SHIFT-CTRL/CMD-S\"))  ;; event not consumed
    #{:SHORTCUT :ENTER}    (fx/new-eventhandler (println \"CTRL/CMD-ENTER\") (.consume event))
    }"
    [codes-map & {:keys [handle-type consume-types]}]
    (new-eventhandler
        ;(println "  ## this:" this "  source:" (.getSource event ))
        (let [
              ev-typ (.getEventType event)
              combo (code-modifier-set event)
              ;_ (println "combo:" (str combo))
              
              do-handle
              #(if (instance? EventHandler %)
                   (.handle ^EventHandler % event)
                   (do (%) (.consume event)))
              
              codes-map1 
              (if (instance? Atom codes-map) @codes-map codes-map)]

          (when-let [f (codes-map1 combo)]
              ;(println "  ## f:" f)
              (if handle-type
                (if (= handle-type ev-typ)
                  (do-handle f))
                (do-handle f))
              ;(println "  ## ev-typ:" ev-typ)
              ;(println "  ## consume-types:" consume-types)
              (when (and consume-types ((set consume-types) ev-typ))
                ;(println "  ## consuming:" ev-typ)
                (.consume event))))))


(defn char-typed-handler
    "Similar to `key-pressed-handler`,
    but takes a map where the key case-sensitive 1-character string and the value is a no-arg function to be run or an instance of EventHandler.

    If the value is a function, then it will be run, but then the event will *not* be consumed. (You are probably typing text.)
    If the value is an EventHandler, then it will be called with the same event as this handler, and it must itself consume the event if required.

    Example of chars-map:
    {   \"s\"    #(println \"s\")  ;; event not consumed
        \"S\"    #(println \"S\")
        \"{\"    (fx/new-eventhandler (println \"{\"))  ;; event not consumed
        \" \"    (fx/new-eventhandler (println \"SPACE (consumed)\") (.consume event))
        }"
    [chars-map]

    (new-eventhandler
        (let [ch-str (.getCharacter ^KeyEvent event)
              chars-map1 (if (instance? Atom chars-map) @chars-map chars-map)]
          (when-let [v  (chars-map1 ch-str)]
                (if (instance? EventHandler v)
                    (.handle ^EventHandler v event)
                    (v))))))

(defn icon
  ;; Usage: http://aalmiray.github.io/ikonli/#_javafx
  ;; Cheatsheet: http://aalmiray.github.io/ikonli/cheat-sheet-fontawesome5.html
  ;; FontAwesome: https://fontawesome.com
  ;; Examples (fx/icon 'di-java) (fx/icon "di-java") (fx/icon 'di-java:32) (fx/icon 'di-java:64:BLUE)
  [icon-code]
  (FontIcon. (str icon-code)))