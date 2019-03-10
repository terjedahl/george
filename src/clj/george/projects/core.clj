(ns george.projects.core
  (:require
    [clojure.string :as cs]
    [clj-diff.core :as diff]
    [george.javafx :as fx]
    [george.code.tokenizer :as gct]
    [george.application.history :as gah]
    [george.application.ui
     [layout :as layout]
     [styled :as styled]]
    [george.util :as u]
    [george.util.colls :as guc]
    [common.george.util
     [text :refer [pformat pprint] :as gut]
     [cli :refer [debug info warn]]]
    [george.util.singleton :as singleton])
  (:import
    [org.fxmisc.richtext InlineCssTextArea]
    [javafx.scene.image Image]
    [javafx.scene.paint Color]
    [javafx.scene.control ListCell ListView]
    [javafx.util Callback]
    [javafx.scene.text TextFlow]
    [javafx.scene Node]
    [java.net ConnectException UnknownHostException SocketException]))


(defn- strip-trailing-slash [^String uri]
  (if (.endsWith uri "/") (subs uri 0 (dec (count uri))) uri))


(defn- scrolling-setter [message layout-fn setter-fn]
  (setter-fn (styled/scrolling-widget message true))
  (fx/future-sleep-later 50 (setter-fn (layout-fn))))


(defn- apply-border
 ([region]
  (doto region (.setBorder (fx/new-border Color/LIGHTSTEELBLUE 2 4))))
 ([region insets]
   ;; Outer vbox for faking margin (using padding)
  (fx/vbox (apply-border region) :padding insets)))


(defn- box [node & {:keys [icon title color size]
                    :or {color Color/BLACK
                         size 18}}]
  (->
    (fx/vbox
      (when (or icon title)
        (fx/hbox
          (when icon
            (fx/icon
              (format "%s:18:STEELBLUE"
                (case icon
                  :info 'fas-info   :asterisk 'fas-asterisk
                  :edit 'far-edit   :play 'fas-play
                  :check 'fas-check :tip 'far-lightbulb))))
          (when title (fx/new-label (str " " title) :size size :color color))
          :spacing 5))
      node
      :padding 10 :spacing 10)
    (apply-border)))


(defn- titled-pformat-layout [title data]
  (fx/new-label (format "\n%s\n\n%s" title (pformat data)) :font (fx/new-font "Source Code Pro" 14)))


(defn- net-exception-layout [e]
  (fx/borderpane
    :center
     (fx/vbox
       (fx/new-label (str e) :color Color/RED :size 18)
       (fx/new-label "Check you internet-connection and try again." :color Color/RED :size 14)
       :spacing 10 :padding 30 :fill? false)))


(defmacro exception-layout [& body]
  `(try
     ~@body
     (catch ConnectException e#  (net-exception-layout e#))
     (catch UnknownHostException e# (net-exception-layout e#))
     (catch SocketException e# (net-exception-layout e#))
     (catch Exception e#
       (fx/scrollpane
         (fx/new-label
           (format "%s\n%s" (str e#) (pformat (seq (.getStackTrace e#))))
           :font (fx/new-font "Source Code Pro" 14) :color fx/RED)))))


(defn- codearea [& [s]]
  (doto (InlineCssTextArea. ^String (or s ""))
    (.setStyle "-fx-font-size: 18; -fx-font-family: 'Source Code Pro'; -fx-padding: 5;")
    ;(.setEditable false)
    ;; Prevent copying!
    (.setDisable true)))


;(defmethod diff/patch InlineCssTextArea [ca edit-script]
;  (doseq [del (:- edit-script)]
;    (.setStyle ca del (inc del)  "-fx-strikethrough: true; -fx-fill: red; -rtfx-background-color: pink;"))
;  (doseq [[ind & chars] (reverse (:+ edit-script))]
;    (let [ind (inc ind)]
;      (.insertText ca ind (apply str chars))
;      (.setStyle ca ind (+ ind (count chars)) "-fx-fill: blue; -rtfx-background-color: aqua;")))
;  ca)

;(defn- diff-str-test []
;  (let [a "Ole har en bil.\nDen er veldig blå.\n  Tut tut."
;        b "Lars har en fin bil.\nDen er grønn.\n  Tut tut."
;        ca (doto (codearea) (gcc/set-text a))
;        edits (diff/diff a b)]
;    (diff/patch ca edits)
;    (fx/init) (fx/later (fx/stage :scene (fx/scene ca :size [300 250]) :tofront true))
;    (prn 'E edits)))
;(diff-str-test)



(defn- clojure-char? [ch]
  (or (gut/coll-delim-char? ch) (gut/readermacro-char? ch)  (gut/macro-char? ch) (gut/comment-char? ch)  (gut/string-delim-char? ch)))


(defn- special-char? [ch]
  (or (gut/whitespace-char? ch) (clojure-char? ch)))


(defn- read-default [rdr ch]
  (let [sb (StringBuilder.)]
    (.append sb ch)
    (loop []
      (let [ch (gct/read-char rdr)]
        ;(if (or (not ch) (other-char? ch))
        ;  (do (gct/unread-char rdr ch) (str sb))
        ;  (do (.append sb ch) (recur)))))))
        (cond
          (not ch)
          (str sb)

          (or (gut/newline-char? ch) (clojure-char? ch))
          (do (gct/unread-char rdr ch) (str sb))

          ;(gut/whitespace-char? ch)
          ;(do (.append sb ch) (str sb))

          :else
          (do (.append sb ch) (recur)))))))


(defn- parse [s]
  (let [rdr (gct/indexing-pushback-stringreader s)]
    (loop [res (transient [])]
      (let [ch (gct/read-char rdr)]
        (if (nil? ch)
          (persistent! res)
          (let [token
                (cond

                  (or (gut/newline-char? ch) (special-char? ch))
                  (str ch)

                  :else
                  (read-default rdr ch))]

            (recur (conj! res token))))))))

(def c
  "(defn- diff-str-test []
    (let [a \" Ole har en bil. \nDen er veldig blå. \n Tut tut. \"
          b \" Lars har en fin bil. \nDen er grønn. \n Tut tut. \"
          ca (doto (codearea) (gcc/set-text a))
          edits (diff/diff a b)]
      (diff/patch ca edits)
      (fx/init) (fx/later (fx/stage :scene (fx/scene ca :size [300 250]) :tofront true))
      (prn 'E edits)))")

;(prn c)
;(println c)
;(prn (parse c))


(defn- split-lines [s]
  (->>
    (cs/split s #"\r?\n" -1)))
    ;(map #(str % ""))))


(defn- diff-patch-textarea [^InlineCssTextArea ca a b]
  (let [ap (split-lines a) ;(parse a)
        bp (split-lines b) ;(parse b)
        ;_ (prn 'AP ap)
        ;_ (prn 'BP bp)
        edits (diff/diff ap bp)
        ;_ (prn 'EDITS edits)
        ;; apply dels
        res (reduce (fn [ap i] (guc/replace-at ap i [[:- (get ap i)]]))
                    ap (:- edits))
        ;; apply adds
        res (reduce (fn [res add]
                      (guc/insert-at res
                                     (inc ^int (first add)) ;; increment because of how 'insert-at' works.
                                     ;[[:+ (apply str (rest add))]]
                                     [[:+ (apply str (interpose \newline (rest add)))]]))
                    res (reverse (:+ edits)))]
    ;(prn 'RES res)
    (doseq [r res]
      (if (string? r)
        (let [i (.getLength ca)]
          (.appendText ca r) (.appendText ca "\n")
          (.setStyle ca i (+ i (count r))
                     "-rtfx-background-color: white;"))
        (let [[e s] r
              i (.getLength ca)]
          (.appendText ca s) (.appendText ca "\n")
          (.setStyle ca i (+ i (count s))
                     (if (= e :-)
                       "-fx-strikethrough: true;
                       -fx-fill: red;
                       -rtfx-background-color: #ffe0e0;"

                       "-fx-fill: blue;
                       -rtfx-background-color: lightblue;
                       -rtfx-border-stroke-color: blue;
                       -rtfx-border-stroke-width: 0.5;
                       -rtfx-border-stroke-type: centered;")))))
    ca))


(defn- diff-str-test2 []
  (let [a "Ole har en bil.\nDen er veldig blå.\n  Tut tut."
        b "Lars har en fin bil.\nDen er grønn.\n  Tut tut."
        ca (codearea)]
    (diff-patch-textarea ca a b)
    (fx/init) (fx/later (fx/stage :scene (fx/scene ca :size [300 250]) :tofront true))))

;(diff-str-test2)

;;;;;

(defn- read-uri-vec [v]
  (->> v (interpose "/") (apply str) slurp read-string))

(defn- read-final [^String uri ^String id]
  (read-uri-vec [uri id "final.clj"]))

(defn- read-steps
 ([^String uri ^String id]
  (read-steps (str uri "/" id)))
 ([^String project-uri]
  (read-uri-vec [project-uri "steps.edn"])))

(defn- read-description [^String uri ^String id]
  (read-uri-vec [uri id "description.edn"]))

(defn- read-index [^String uri]
  (read-uri-vec [uri "index.edn"]))

(defn- read-index-data [^String uri]
  (read-uri-vec [uri "index-data.edn"]))


;(def default-uri "http://localhost:50000")
(def default-uri "https://projects.george.andante.no")

(defn- get-uri [& [not-default?]]
  (or (gah/get-george-projects-uri)
      (when-not not-default? default-uri)))


(defn- set-uri [^String uri]
  (gah/set-george-projects-uri uri))


(defn- get-current-step-index [id]
  (gah/get-george-projects-step id))


(defn- set-current-step-index [id step-index]
  (gah/set-george-projects-step id step-index))

(defn- bad-image-load [image]
  (let [ex (.getException image)]
    (fx/vbox
      (fx/new-label "Broken image!" :color Color/RED :size 18)
      (fx/new-label "Check you internet-connection and try again.\n\n" :color Color/RED :size 14)
      (fx/new-label (str ex) :color Color/RED :size 12)
      (fx/new-label (.getUrl image) :color Color/RED :size 10)
      :spacing 5 :fill? false)))


(defn- step-content [value project-uri]
  (if-let [img (:img value)]
    (let [image (Image.  (str project-uri "/" img) false)]
      (if (.isError image)
        (bad-image-load image)
        (fx/imageview image :width (:width value) :height (:height value))))

    (if-let [text (:text value)]
      (fx/text text :size 16 :color fx/ANTHRECITE)

      (if-let [button (:button value)]
        (fx/stackpane
           (doto (fx/new-label (str "  " button "  ") :size 18)
             (.setBorder (fx/new-border Color/GRAY 2 3))
             (fx/set-stackpane-insets [0 3 10 0]))
           (-> (fx/icon 'fas-mouse-pointer:18)
               (fx/set-stackpane-alignment fx/Pos_BOTTOM_RIGHT)))

        (throw (IllegalArgumentException. (format "Don't know how to build step-content for: \n %s" value)))))))


(defn- step-row [type value prev-step curr-step  project-uri]
  (case type
    :none  (box (step-content value project-uri))
    :info  (box (step-content value project-uri) :icon :info)
    :check (box (step-content value project-uri) :icon :check)
    :tip   (box (step-content value project-uri) :icon :tip)
    :play  (box (step-content value project-uri) :icon :play)
    :row
    (apply fx/hbox (concat (map #(step-row (first %) (second %) prev-step curr-step project-uri) value)
                           (list :spacing 20 :fill? false)))
    :edit
    (if-let [code (:code value)]
      (let [curr-code (.trim ^String (get (:code curr-step) code))
            prev-code (.trim ^String (get (:code prev-step) code ""))
            ca (codearea)]
        (diff-patch-textarea ca prev-code curr-code)
        (fx/set-all-WH ca [ 600 (-> ca .getText cs/split-lines count (* 24) (+ 5 5))]) ;;  5 5 is for codearea padding
        (box  ca :title (str \" code \") :icon :edit))
      ;; else
      (box (step-content value project-uri) :icon :edit))
    ;; default
    (throw (IllegalArgumentException. (format "Unknown layout type '%s'" type)))))


(defn- step-layout [data step-index project-uri]
  (let [prev (get data (dec step-index))
        curr (get data step-index)
        ;_ (debug 'PREV prev)
        ;_ (debug 'CURR curr)
        content
        (-> (apply fx/vbox
                   (concat
                     (map (fn [[typ & values]]
                            (step-row typ (if (= typ :row) values (first values)) prev curr project-uri))
                          (:layout curr))
                     (list :padding 30 :spacing 45 :fill? false)))
          (apply-border [10 30 30 30]))]
       (doto
         (fx/scrollpane content)
         (fx/add-class "projects-scrollpane"))))


(defn- set-step [container title data step-index project-uri]
  (doto container
    (.setCenter (styled/scrolling-widget (format "Loading step %s ..." (inc step-index)) true))
    (.setBottom nil))
  (fx/future-sleep-later 50
    (let [step-count (count data)
          step-index (min step-index (dec step-count))  ;; ensure no index-out-of-bounds
          button-gen
          (fn [icon-str tooltip step-ind disable?]
            (doto (fx/new-button nil
                                 :graphic (fx/icon icon-str)
                                 :tooltip tooltip
                                 :focusable? false
                                 :onaction #(set-step container title data step-ind project-uri))
              (.setDisable disable?)))

          prev-b   (button-gen 'fas-angle-left:18  "Previous step" (dec step-index) (not (pos? step-index)))
          next-b   (button-gen 'fas-angle-right:18 "Next step"     (inc step-index) (= step-index (dec step-count)))
          reset-b
          (fx/new-label nil :graphic (fx/icon 'fas-angle-double-left:18) :tooltip "Go to first step"
                            :mouseclicked #(set-step container title data 0 project-uri))
          reload-b
          (fx/new-label nil :graphic (fx/icon 'fas-redo) :tooltip "Reload data"
                            :mouseclicked #(set-step container title (read-steps project-uri) step-index project-uri))
          navbar
          (fx/stackpane
            (fx/hbox
              prev-b
              (fx/new-label (format "Step %s of %s" (inc step-index) step-count)
                            :tooltip (str "step id: " (:id (get data step-index) "<NA>")))
              next-b
              (fx/region :hgrow :always)
              reset-b
              reload-b
              :spacing 10 :padding 10 :alignment fx/Pos_CENTER_LEFT)
            (styled/new-label title))]
      (set-current-step-index (last (cs/split project-uri #"/")) step-index)

      (doto container
        (.setCenter (step-layout data step-index project-uri))
        (.setBottom navbar)))))


(defn- project-player-layout [title data id project-uri]
  (doto (fx/borderpane)
    (set-step title data (or (get-current-step-index id) 0) project-uri)))


(defn- project-description-layout [id data ^String uri setter-fn]
  (if-not data
    (titled-pformat-layout "NO DATA" (symbol (str \< id \>)))

    (let [project-uri (str uri "/" id)
          title (:title data)
          load-button
          (fx/new-button "Start/resume project"
             :onaction
             (fn [] (scrolling-setter (format "Loading %s ..." title)
                      #(exception-layout (project-player-layout title (read-steps project-uri) id project-uri))
                      setter-fn)))
          content
          (doto
            (fx/vbox
              (fx/vbox
                (fx/text title :size 24  :color Color/STEELBLUE)
                (when-let [s (:ingres data)]
                  (fx/text s :font (fx/new-font fx/ROBOTO  :normal :italic 20) :color Color/GREY))
                :spacing 10)
              (when-let [s (:description data)]
                (fx/text s :size 16 :width 500 :color fx/ANTHRECITE))
              (when-let [img (:img data)]
                (let [image (Image. (str project-uri "/" img) false)]
                  (if (.isError image)
                    (bad-image-load image)
                    (fx/imageview image))))
              load-button
              :spacing 30 :padding [10 30 30 30]))

          scrollpane
          (doto
            (fx/scrollpane content)
            (.setFocusTraversable false)
            (fx/add-class "projects-scrollpane"))]

      (fx/future-sleep-later 100 (.setVvalue scrollpane 0))
      scrollpane)))


(defn listcell-layout [{:keys [label value]}]
  (let [{:keys [id index-data]} value
        ingres (:ingres (get index-data id))]
    (-> (fx/vbox
          (fx/new-label label :size 16 :color fx/ANTHRECITE)
          (fx/new-label (str ingres) :size 14 :color Color/GRAY)
          :spacing 3 :padding [7 15 7 15])
        (apply-border 5))))


(defn- index-listcell []
    (eval
      `(proxy [ListCell] []
         (updateItem [item# empty?#]
           (proxy-super updateItem item# empty?#)
           (let [node# (cond
                         (or (nil? item#) empty?#) nil
                         :default                  (listcell-layout item#))]
             (doto ~'this
               (fx/add-class "projects-index-listcell")
               (.setText nil)
               (.setGraphic  node#)
               (.setPrefWidth 0)))))))


(defn- index-listcell-factory []
  (reify Callback
    (call [_ listview]
      (index-listcell))))


(defn- welcome-layout []
  (fx/vbox

    (fx/text "Welcome!" :size 28 :color Color/STEELBLUE)
    (TextFlow.
      (into-array
        Node
        (list
            (fx/icon 'fas-arrow-left)
            (fx/text "  Select a project." :size 18 :color fx/ANTHRECITE)
            (fx/text "  Start with \"Starry night\"." :size 16 :color Color/GRAY))))

    :spacing 20 :padding [10 30 30 30]))


(defn- projects-index-layout [index index-data ^String uri root-details-fn]
  (let [[root master-fn detail-fn] (layout/master-detail)]
    (doto ^ListView (->> index
                         (map #(u/->Labeled (:title (get index-data %) (str \< % \>)) {:id % :index-data index-data}))
                         (apply fx/observablearraylist)
                         fx/listview)
      (master-fn)
      (.setFocusTraversable false)
      (.setCellFactory (index-listcell-factory))
      (fx/add-class "projects-index-listview")

      (-> .getSelectionModel .selectedItemProperty
          (.addListener
            (fx/new-changelistener
              (scrolling-setter
                "Loading project description ..."
                #(project-description-layout
                   (:id (:value new-value))
                   (get index-data (:id (:value new-value)))
                   uri
                   root-details-fn)
                detail-fn)))))

    (detail-fn (welcome-layout))
    root))


(defn- home-uri-layout [home-fn]
  (let [uri-field
        (fx/textfield :text (or (get-uri true) "") :font (fx/new-font 16) :prompt default-uri :cols 20)
        set-fn #(let [uri (-> uri-field .getText .trim strip-trailing-slash)]
                  (set-uri (when-not (cs/blank? uri) uri))
                  (home-fn))
        reset-fn (fn []
                   (.setText uri-field "")
                   (set-uri nil)
                   (fx/future-sleep-later 500 (home-fn)))

        set-button   (fx/new-button "Set"   :onaction set-fn   :tooltip "Set as URI and load.")
        reset-button (fx/new-button "Reset" :onaction reset-fn :tooltip "Set URI back to default and load.")]

    (fx/vbox (fx/hbox (fx/new-label "Projects URI:") uri-field set-button reset-button :spacing 5) :padding 10)))


(defn- stage-root []
  (let [[root m-setter d-setter]
        (layout/master-detail true)

        home-fn
        (fn []
          (let [uri (get-uri)]
            (scrolling-setter
              "Loading projects list ..."
              #(exception-layout (projects-index-layout (read-index uri) (read-index-data uri) uri d-setter))
              d-setter)))

        home-button
        (fx/new-button "Home" :graphic (fx/icon 'fas-home)  :size 14  :onaction home-fn :focusable? false)

        settings-label
        (fx/new-label "URI" :size 14 :mouseclicked #(d-setter (home-uri-layout home-fn)))]

    (m-setter
      (fx/hbox  home-button (fx/region :hgrow :always) settings-label
                :padding 10 :spacing 20 :alignment fx/Pos_CENTER_LEFT))

    (home-fn)

    (doto root
      (fx/add-stylesheet "styles/projects.css"))))



(defonce feedback-borderlayout_ (atom nil))


(defn hide-projects-stage []
  (when-let [stage (singleton/get ::projects-stage)]
    (when (.isShowing stage) (.hide stage))
    (singleton/remove ::projects-stage)
    (when-let [bl @feedback-borderlayout_]
      (.setCenter bl (styled/new-heading "Projects window closed"))
      (reset! feedback-borderlayout_ nil)))
  (styled/new-heading "George Projects has been disposed"))


(defn- new-projects-stage []
  (fx/init)
  (fx/now
    (styled/style-stage
      (fx/stage :title "George Projects"
                :scene (fx/scene (stage-root) :size [900 600])
                :location [30 60]
                :tofront true
                :onhidden hide-projects-stage))))


(defn show-projects-stage []
  (fx/later
    (doto (singleton/get-or-create ::projects-stage new-projects-stage)
      (.setIconified false)
      (.toFront))
    (reset! feedback-borderlayout_
      (fx/borderpane :center (styled/new-heading "George Projects\n\n(shown in separate window)")))))
