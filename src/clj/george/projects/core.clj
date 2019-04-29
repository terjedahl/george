;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.core
  (:require
    [clojure.string :as cs]
    [george.javafx :as fx]
    [george.projects.code :as code]
    [george.application
     [history :as gah]
     [input :as input]]
    [george.application.ui
     [layout :as layout]
     [styled :as styled]]
    [george.util :as u]
    [common.george.util
     [cli :refer [debug info warn]]
     [text :refer [pformat pprint]]]
    [george.util.singleton :as singleton])
  (:import
    [javafx.scene.image Image]
    [javafx.scene.paint Color]
    [javafx.scene.control ListCell ListView]
    [javafx.util Callback]
    [java.net ConnectException UnknownHostException SocketException]
    [javafx.scene.web WebView]
    [javafx.stage Stage]))


;; If true, then only code is shown, with eval and mark + copy options
(defonce ^:private code-mode_ (atom false))
(defn code-mode
  ([b] (reset! code-mode_ b))
  ([]  @code-mode_))


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


(defn- box [node & {:keys [icon title color size] :or {color Color/BLACK size 18}}]
  (let [header-box
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
                :spacing 5))]
    (->
      (fx/vbox header-box node
        :padding (if (= icon :edit) 10 0)
        :spacing 10)
      ((if (= icon :edit) apply-border identity)))))


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


(defn- load-uri-vec [v]
  (->> v (interpose "/") (apply str) slurp))

(defn- read-uri-vec [v]
  (read-string (load-uri-vec v)))

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

(defn- load-welcome [^String uri]
  (load-uri-vec [uri "welcome.html"]))


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


(defn webview [html]
  (doto (WebView.)
    (-> .getEngine (.loadContent html))))


(defn- bad-image-load [image]
  (let [ex (.getException image)]
    (fx/vbox
      (fx/new-label "Broken image!" :color Color/RED :size 18)
      (fx/new-label "Check you internet-connection and try again.\n\n" :color Color/RED :size 14)
      (fx/new-label (str ex) :color Color/RED :size 12)
      (fx/new-label (.getUrl image) :color Color/RED :size 10)
      :spacing 5 :fill? false)))


(defn- step-content
  "'value' is a map containing a key in #{:img :text :button}"
  [value project-uri]
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


(defn- step-row-edit [value prev-step curr-step project-uri]
  (if-let [code-name (:code value)]
    (let [collapse?   (:collapse value)
          load?       (not= code-name "Input")
          curr-code   (.trim ^String (get (:code curr-step) code-name))
          prev-code   (.trim ^String (get (:code prev-step) code-name ""))
          textarea    (code/diff-patch-textarea prev-code curr-code collapse?)
          set-textarea-WH
          #(fx/set-all-WH % [ 600 (-> % .getText code/split-lines count (* 24) (+ 5 5))]) ;;  5 5 is for codearea padding

          ;; The following are for "code-mode"
          textarea-c  (fx/stackpane textarea)
          ns-label    (doto (styled/ns-label) (.setText "user.turtle"))
          ns-fn       (input/set-ns-label-fn ns-label)
          interrupt-b (input/interrupt-button)
          run-b       (fx/new-button (if load? "Load" "Run") :tooltip "Load/Run this code" :focusable? false)
          collapsed-cb
          (fx/new-checkbox "Collapsed" :tooltip "Code is collapsed" :selected? collapse? :focusable? false)
          collapsed-bar
          (fx/hbox (fx/region :hgrow :always) collapsed-cb)
          eval-bar
          (fx/hbox ns-label (fx/region :hgrow :always) interrupt-b run-b :spacing 3 :padding 5 :alignment fx/Pos_CENTER_LEFT)
          eval-fn
          (fn [] (input/do-eval curr-code run-b interrupt-b #(.getText ns-label) ns-fn code-name nil nil load?))
          collapsed-fn
          #(doto (code/diff-patch-textarea prev-code curr-code (.isSelected collapsed-cb) (code-mode))
             set-textarea-WH
             (->> (fx/children-set textarea-c 0)))]

      (fx/set-onaction run-b eval-fn)
      (fx/set-onaction collapsed-cb collapsed-fn)

      (set-textarea-WH textarea)
      (box (if-not (code-mode) textarea (fx/vbox collapsed-bar textarea-c eval-bar))
           :title (str \" code-name \") :icon :edit))

    ;; else there is another key than :code (see acceptable keys in step-content doc)
    (box (step-content value project-uri)
         :icon :edit)))


(defn- step-row [type value prev-step curr-step  project-uri]
  (if (code-mode)
    (when (= type :edit)
      (step-row-edit value prev-step curr-step  project-uri))
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
      (step-row-edit value prev-step curr-step project-uri)

      ;; default
      (throw (IllegalArgumentException. (format "Unknown layout type '%s'" type))))))


(defn- step-layout [data step-index project-uri]
  (let [prev (get data (dec step-index))
        curr (get data step-index)
        content
        (-> (apply fx/vbox
                   (concat
                     (map (fn [[typ & values]]
                            (step-row typ (if (= typ :row) values (first values)) prev curr project-uri))
                          (:layout curr))
                     (list :padding 20 :spacing 30 :fill? false))))]
       (doto
         (fx/scrollpane content)
         (fx/add-class "projects-scrollpane"))))


(defn- set-step [container data step-index project-uri]
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
                                 :onaction #(set-step container data step-ind project-uri))
              (.setDisable disable?)))

          prev-b   (button-gen 'fas-angle-left:18  "Previous step" (dec step-index) (not (pos? step-index)))
          next-b   (button-gen 'fas-angle-right:18 "Next step"     (inc step-index) (= step-index (dec step-count)))
          reset-b
          (fx/new-button nil :graphic (fx/icon 'fas-angle-double-left:16) :tooltip "Go to first step"
                             :onaction #(set-step container data 0 project-uri)
                             :focusable? false)
          reload-b
          (fx/new-label nil :graphic (fx/icon 'fas-redo:16:gray) :tooltip "Reload data"
                            :mouseclicked #(set-step container (read-steps project-uri) step-index project-uri))
          button-box
          (fx/hbox
                prev-b
                (fx/new-label (format "Step %s of %s" (inc step-index) step-count)
                              :tooltip (str "step id: " (:id (get data step-index) "<NA>")))
                next-b
                (fx/region :hgrow :always)
                reload-b
                reset-b
                :spacing 10 :padding 10 :alignment fx/Pos_CENTER_LEFT)
          navbar
          (doto
            (fx/stackpane
              button-box
              (fx/new-label (:id (get data step-index) "") :size 12 :color Color/LIGHTGRAY))
            (.setBorder (fx/new-border fx/DEES [1 0 0 0])))]

      (set-current-step-index (last (cs/split project-uri #"/")) step-index)

      (-> container .getScene (.setOnKeyPressed (fx/key-pressed-handler
                                                  {#{:LEFT}  #(.fire prev-b)
                                                   #{:RIGHT} #(.fire next-b)})))
      (doto container
        (.setCenter (step-layout data step-index project-uri))
        (.setBottom navbar)))))


(defn- project-player-layout [data id project-uri]
  (doto (fx/borderpane)
    (set-step data (or (get-current-step-index id) 0) project-uri)))


(defn- project-description-layout [id data ^String uri setter-fn title-label]
  (if-not data
    (titled-pformat-layout "NO DATA" (symbol (str \< id \>)))
    (let [project-uri (str uri "/" id)
          title (:title data)
          load-button
          (fx/new-button "Start/resume project"
             :onaction
             (fn [] (scrolling-setter (format "Loading %s ..." title)
                      #(exception-layout
                         (do
                           (.setText title-label title)
                           (project-player-layout (read-steps project-uri) id project-uri)))
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
                (fx/text s :size 16 :width 450 :color fx/ANTHRECITE))
              load-button
              (when-let [img (:img data)]
                (let [image (Image. (str project-uri "/" img) false)]
                  (if (.isError image)
                    (bad-image-load image)
                    (fx/imageview image))))
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


(defn- welcome-layout [^String html]
  (webview html))


(defn- replace-base [html uri]
  (cs/replace html #"<base.+href=\"https://projects.george.andante.no/\"" (format "<base href=\"%s\"" uri)))


(defn- projects-index-layout [index index-data ^String uri root-details-fn title-label]
  (let [[root master-fn detail-fn] (layout/master-detail)]
    (doto ^ListView (->> index
                         (map #(u/->Labeled (:title (get index-data %) (str \< % \>)) {:id % :index-data index-data}))
                         (apply fx/observablearraylist)
                         fx/listview)
      (fx/set-pref-WH [200 nil])
      master-fn
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
                   root-details-fn
                   title-label)
                detail-fn)))))

    (detail-fn (scrolling-setter
                 "Loading Welcome page ..."
                 #(exception-layout (welcome-layout (replace-base (load-welcome uri) uri)))
                 detail-fn))
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

        title-label
        (fx/new-label "Title" :size 20 :color Color/STEELBLUE)

        home-fn
        (fn []
          (.setText title-label "")
          (let [uri (get-uri)]
            (scrolling-setter
              "Loading projects list ..."
              #(exception-layout (projects-index-layout (read-index uri) (read-index-data uri) uri d-setter title-label))
              d-setter)))

        home-button
        (fx/new-button "Home" :graphic (fx/icon 'fas-home)  :size 14  :onaction home-fn :focusable? false)

        settings-label
        (fx/new-label nil :graphic (fx/icon 'fas-cog:18:gray) :size 14 :mouseclicked #(d-setter (home-uri-layout home-fn)))

        top-bar
        (doto
          (fx/stackpane
            (fx/hbox  home-button (fx/region :hgrow :always) settings-label
                      :padding 10 :spacing 20 :alignment fx/Pos_CENTER_LEFT)
            title-label)
          (.setBorder (fx/new-border fx/DEES [0 0 1 0])))]

    (m-setter top-bar)
    (home-fn)

    (doto root
      (fx/add-stylesheet "styles/projects.css"))))


(defn hide-projects-stage []
  (when-let [stage (singleton/get ::projects-stage)]
    (when (.isShowing stage) (fx/later (.hide stage)))
    (singleton/remove ::projects-stage))
  nil)


(defn- new-projects-stage []
  (fx/init)
  (let [size     [800 600]
        location [(- (first (fx/primary-center)) (/ (first size) 2)) 100]]
    (fx/now
      (styled/style-stage
        (fx/stage
          :style :utility
          :title "George Projects"
          :scene (fx/scene (stage-root) :size size)
          :location location
          :alwaysontop true
          :tofront true
          :onhidden hide-projects-stage)))))


(defn show-projects-stage []
  (fx/later
    (if-let [st ^Stage (singleton/get ::projects-stage)]
      (if (.isAlwaysOnTop st)
        (doto st (.setAlwaysOnTop false) (.toBack))
        (doto st (.setAlwaysOnTop true)))
      (singleton/get-or-create ::projects-stage new-projects-stage)))
  nil)
