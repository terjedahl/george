(ns
    ^{:author "Terje Dahl"}
    george.core.core
    (:require
        [clojure.repl :as cr]
        [clojure.pprint :refer [pprint pp] :as cpp]
        [george.javafx.core :as fx]
        :reload
        [george.javafx.java :as fxj]
        :reload

        [george.code.core :as gcode]
        [clojure.string :as s])

    (:import (javafx.geometry Pos)
             (javafx.scene.paint Color)
             (javafx.scene.control Tooltip ListCell )
             (javafx.util Callback)
             (java.io StringWriter PrintStream OutputStreamWriter StringReader PushbackReader)
             (org.apache.commons.io.output WriterOutputStream)
             (javafx.scene.text Text )
             (clojure.lang LineNumberingPushbackReader ExceptionInfo)

             (javafx.scene.input KeyCode KeyEvent)
             (org.fxmisc.richtext StyledTextArea)
             (java.util.function BiConsumer)
             (java.util Collections)))


(defonce standard-out System/out)
(defonce standard-err System/err)
(declare output)


;;;; input section ;;;;

(def RT-state-atom (atom {}))

;; from Versions.java in george-client
(def IS_MAC  (-> (System/getProperty "os.name") .toLowerCase (.contains "mac")))
(def SHORTCUT_KEY (if IS_MAC "CMD" "CTRL"))




(defn- read-span [[after-l after-c] [before-l before-c] code]
    ;(output nil (str " after: " after-l ":" after-c \newline))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))
          newline-int (int \n)
          sb (StringBuilder.)]
        ;; step rdr forward to start-line and start-char
        (dotimes [_  (dec after-l)] (.readLine rdr))
        (dotimes [_  (dec after-c)] (.read rdr))

        (loop []
            ;; keep appending until end-line and end-char are reached
            (.append sb (char (.read rdr)))
            (when
                (or
                    (< (.getLineNumber rdr) before-l)
                    (and (= (.getLineNumber rdr) before-l)
                         (< (.getColumnNumber rdr) before-c)))
                (recur)))
        ;; returned a trimmed string of read code
        (.trim (str sb))))



(defn- with-newline [obj]
    "ensures that the txt ends with a new-line"
    (let [txt (if (nil? obj) "nil" (str obj))]
        (if (s/ends-with? txt "\n")
            txt
            (str txt "\n"))))


(defn- print-error

    ([^ExceptionInfo ei]
     (let [{:keys [after before source]} (ex-data ei)]
         (print-error after before source
                      (. ei getMessage) (. ei getCause) ei)))

    ([after before source msg cause exception]
     (let [
           [after-l after-c] after
           [before-l before-c] before
           exception-trace (seq (.getStackTrace exception))
           supressed-trace (seq  (.getSuppressed exception))
           cause-trace (when-let [cause (.getCause exception)](seq (.getStackTrace cause)))
           total-error
           (format
"# EXCEPTION!
%s
## SOURCE:
%s
## SOURCE SPAN:
from inclusive [%s:%s] to exclusive [%s:%s]  ([line:char])
## STACKTRACE EXCEPTION:
%s
## STACKTRACE SUPRESSED:
%s
## STACKTRACE CAUSE:
%s
"
msg
source
after-l after-c before-l before-c
exception-trace
supressed-trace
cause-trace
)

           ]
         (output :err total-error)
;         (.println standard-err loc-str)
         (.printStackTrace (or cause exception) standard-err)
         ))
    )


;; TODO:
"
The following does not give a useful printout - either from any repl or from a loaded file.
Solve this to make something more user-friendly: A more usable and beginner-firendly system!
(defn fail [v] (/ 2 v))
(fail 0)
"
(defn- output-ns [ns]
    (output :ns (str "namespace: " ns "\n")))


(defn- read-eval-print [code]
    (println)
    (output-ns *ns*)

    (output :in (with-newline code))
    (let [rdr (LineNumberingPushbackReader. (StringReader. code))]

        ;; inspiration:
        ;;   https://github.com/pallet/ritz/blob/develop/nrepl-middleware/src/ritz/nrepl/middleware/tracking_eval.clj
        ;;   https://github.com/pallet/ritz/blob/develop/repl-utils/src/ritz/repl_utils/compile.clj
        (push-thread-bindings
            {Compiler/LINE_BEFORE (Integer. (int 0))
             Compiler/LINE_AFTER (Integer. (int 0))})
        (try

            (loop [ns *ns*]
                (let [
                      after [(.getLineNumber rdr ) (.getColumnNumber rdr )]
                      _ (.set  Compiler/LINE_BEFORE (Integer. (first after)))

                      read-res
                      (try
                          (read rdr false :eof)
                          ;; pass exception into form for later handling,
                          ;; so we first can get the code end-point.
                          (catch Exception e e))

                      before [(.getLineNumber rdr) (.getColumnNumber rdr)]
                      _ (.set Compiler/LINE_BEFORE (Integer. (first before)))

                      ;; TODO: get source from code, not from read-res
                      source (read-span after before code)
                      ]
                    (when (instance? Exception read-res)
                        (throw
                            (ex-info
                                (.getMessage read-res)
                                {:after after :before before :source source}
                                (.getCause read-res))))

                    (when-not (= read-res :eof)
                            (try
                                (let [eval-res (eval read-res)]

                                    (output :res (with-newline eval-res))
                                    ;; we made it this far without a read-exception or eval-exception

                                    ;(output nil (str "read-res: " read-res \newline))
                                    (cond
                                        (not= *ns* ns)
                                        (do
                                            (output-ns *ns*)
                                            (output nil (str "   new ns: " *ns* " \n"))
                                            (output nil (str "   source: " source \newline))
                                            )

                                        (var? eval-res)
                                        (do
                                            (output nil (str "      var: " eval-res "   (RT-state store)\n"))
                                            (output nil (str "   source: " source \newline))
                                            )

                                        :default
                                        (do
                                            (output nil (str " call res: " eval-res "   (history store)\n"))
                                            (output nil (str "   source: " source \newline))
                                            ))
                                    )

                                ;; Catch and re-throw the eval-exception,
                                ;; adding start and end-point in code
                                    (catch Exception e
                                        (throw
                                            (ex-info
                                                (.getMessage e)
                                                {:after after :before before :source source}
                                                (.getCause e)))))
                                ;; We only recur if no read or eval exception was thrown.
                                (recur *ns*))))
            ;; here we actually catch read and eval exceptions, and print them.
            ;; This is where something much more useful should be done!!
            (catch ExceptionInfo ei
                (print-error ei))

            (finally
                (pop-thread-bindings)))

        (str *ns*)))


(declare
    read-eval-print-in-ns)


(defn- run [code-area modified? ns-textfield]
    (let [input (gcode/text code-area)]
        (if (s/blank? input)
            (println)
            (fxj/thread
                (let [new-ns (read-eval-print-in-ns input (.getText ns-textfield))]
                    (fx/thread (.setText ns-textfield new-ns))

                    ;; handle history and clearing

                    )))))



(defn- input-scene []
    (let [

          ns-label
          (doto
              (fx/label "user")
              ( .setStyle "
                    -fx-font: 12 'Source Code Pro Regular';
                    -fx-text-fill: gray;
                "))


          code-area
          (gcode/->codearea)


          #_(comment
                prev-button
                (doto (jfx/button (str  \u25B2) #(history-button-fn -1 false)) ;; small: \u25B4
                    (-> .getStyleClass (.add "default-button"))
                    (.setId "repl-prev-button")
                    (gui/install-tooltip (format "Previous 'local' history.   %s-UP\nAccess 'global' history by using SHIFT-%s-UP" SHORTCUT_KEY SHORTCUT_KEY))
                    )

                next-button
                (doto (jfx/button (str \u25BC) #(history-button-fn 1 false)) ;; small: \u25BE
                    (-> .getStyleClass (.add "default-button"))
                    (.setId "repl-next-button")
                    (gui/install-tooltip (format "Next 'local' history.   %s-DOWN\nAccess 'global' history by using SHIFT-%s-DOWN" SHORTCUT_KEY SHORTCUT_KEY))
                    )
                )
          run-button
          (fx/button
              "Run"
              :width 150
              :onaction #(run code-area false ns-label)
              ;                (gui/install-tooltip (format "Run code.   %s-ENTER\nPrevent clearing of code by using SHIFT-%s-ENTER" SHORTCUT_KEY SHORTCUT_KEY))
              :tooltip (format "Run code.  %s-ENTER" SHORTCUT_KEY)
              )

          button-box
          (fx/hbox
              ;prev-button
              ;next-button
              (fx/region :hgrow :always)
              run-button
              :spacing 3
              :alignment Pos/TOP_RIGHT
              :insets [5 0 0 0])

          border-pane
          (fx/borderpane
              :center code-area
              :top ns-label
              :bottom button-box
              :insets 10)

          scene
          (doto
              (fx/scene border-pane :size [500 200])
              (fx/add-stylesheets "styles/codearea.css")
              )

          key-handler
          (fx/key-pressed-handler{
                                    ;#{       :CTRL :UP}    #(history-button-fn -1 false)
                                    ;#{:SHIFT :CTRL :UP}    #(history-button-fn -1 true)
                                    ;#{       :CTRL :DOWN}  #(history-button-fn 1 false)
                                    ;#{:SHIFT :CTRL :DOWN}  #(history-button-fn 1 true)
                                    #{       :CTRL :ENTER} #(run code-area false ns-label)
                                    ;#{:SHIFT :CTRL :ENTER} #(run code-area true ns-label)
                                    })

          ]
        (. border-pane addEventFilter KeyEvent/KEY_PRESSED key-handler)
        ;; TODO: ensure code-area alsways gets focus back when focus in window ...

        scene ))



;;;; API ;;;;


(defn read-eval-print-in-ns
    "returns new ns as string"
    [^String code ^String ns]
    ;; useful?  Found in clojure.main/eval-opt
    ;    (let [cl (.getContextClassLoader (Thread/currentThread))]
    ;        (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))
    (binding [*ns* (create-ns (symbol ns)) *file* "'input'"]
        (read-eval-print code)))


(defn input-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)]
        (fx/now (fx/stage
            :style :utility
            :title "Input"
            :scene (input-scene)
            :sizetoscene true
            :location [(-> bounds .getWidth (/ 2) )
                       (-> bounds .getHeight (/ 2) (- 300) )]))))






;;;; output section ;;;;


(defonce ^:private output-singleton (atom nil))


(defn- get-outputarea []
    (when-let [stage @output-singleton]
        (->  stage .getScene .getRoot .getChildrenUnmodifiable first)
        ))


(defn- output-string-writer [typ] ;; type is one of :out :err
    (proxy [StringWriter] []
        (flush []
            ;; first print the content of StringWriter to output-stage
            (let [s (str this)]
                (if (= typ :err)
                    (. standard-err print s)
                    (. standard-out print s))
                (output typ s))
            ;; then flush the buffer of the StringWriter
            (let [sb (. this getBuffer)]
                (. sb delete 0 (. sb length))))))


(defn wrap-outs []
    (println "wrap-outs")
    (let [
          ow (output-string-writer :out)
          ew (output-string-writer :err)
          ]
        (System/setOut (PrintStream. (WriterOutputStream. ow) true))
        (System/setErr (PrintStream. (WriterOutputStream. ew) true))
        (alter-var-root #'*out* (constantly ow))
        (alter-var-root #'*err* (constantly ew))))


(defn unwrap-outs []
    (println "unwrap-outs")
    (System/setOut standard-out)
    (System/setErr standard-err)
    (alter-var-root #'*out* (constantly (OutputStreamWriter. System/out)))
    (alter-var-root #'*err* (constantly (OutputStreamWriter. System/err))))


(defrecord OutputStyleSpec [color])

(def ^:private DEFAULT_SPEC (->OutputStyleSpec "GRAY"))

(defn ^:private output-style [typ]
    (get {:out (->OutputStyleSpec "#404042")
          :err (->OutputStyleSpec "RED")
          :in (->OutputStyleSpec "BLUE")
          :ns (->OutputStyleSpec "BROWN")
          :res (->OutputStyleSpec "GREEN")}
         typ
         (->OutputStyleSpec "ORANGE")))



(defn output [typ obj]  ;; type is one of :in :ns :res :out :err
    ;; TODO: maybe crop old lines from beginning for speed?
    (if-let[oa (get-outputarea)]
        (fx/later
            (let [start (.getLength oa)]
            (.insertText oa start (str obj))
            (.setStyle oa start (.getLength oa) (output-style typ)))))
    ;; else:  make sure these always also appear in stout
    (if (#{:in :res} typ)
        (. standard-out print (str obj))))



(defn- style [spec]
    (let [{c :color} spec]
        (str
            "-fx-fill: " (if c c "#404042") "; "
            "-fx-font-weight: normal; "
            "-fx-underline: false; "
            "-fx-background-fill: null; ")))


(defn- apply-specs [^Text text specs]
    (cond
        (instance? OutputStyleSpec specs)
        (.setStyle text (style specs))

        (= Collections/EMPTY_LIST specs)
        (.setStyle text (style DEFAULT_SPEC))

        :default
        (doseq [spec specs]
            (.setStyle text (style spec)))))


(defn- style-biconsumer []
    (reify BiConsumer
        (accept [_ text style]
            (apply-specs text style))))


(defn- output-scene []
    (let [
          outputarea
              (doto (StyledTextArea. DEFAULT_SPEC (style-biconsumer))
                  (.setFont (fx/SourceCodePro "Regular" 16))
                  (.setStyle "-fx-padding: 0; -fx-background-color: WHITESMOKE;")
                  (.setUseInitialStyleForInsertion true)
                  (-> .getUndoManager .forgetHistory)
                  (-> .getUndoManager .mark)
                  (.selectRange 0 0)
                  (.setEditable false))

          clear-button
          (fx/button
              "Clear"
              :width 150
              :onaction #(.replaceText outputarea "")
              :tooltip (format "Clear output")
              )
          button-box
          (fx/hbox

              (fx/region :hgrow :always)
              clear-button
              :spacing 3
              :alignment Pos/TOP_RIGHT
              :insets [0 0 5 0])

          scene
          (fx/scene (fx/borderpane
                        :top button-box
                        :center outputarea
                        :insets 5))
          ]
        #_(-> text-flow
            .getChildren
            (. addListener
               (reify ListChangeListener
                   (onChanged [_ _]
                       (. text-flow layout)
                       (doto scroll-pane (. layout) (. setVvalue 1.0))))))

        #_(def append (fn [node] (fx/later (-> text-flow .getChildren (. add node)))))

        scene ))




(defn- output-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)
          size [1000 300];[700 300]
          ]
        (fx/now
            (fx/stage
                :style :utility
                :title "Output"
                :location [(. bounds getMinX)
                           (-> bounds .getMaxY (- (second size)))]
                :size size
                :scene (output-scene)))))




(defn show-or-create-output-stage []
    (if @output-singleton
        (fx/later (. @output-singleton toFront))
        (do (wrap-outs)
            (reset! output-singleton
                    (fx/setoncloserequest
                        (output-stage)
                        #(do
                            (println "reset!-ing @output-singleton to nil")
                            (reset! output-singleton nil)
                            (unwrap-outs)))))))







;;;; VAR section ;;;;



(defrecord NsThing [k v t]
    Object
    (toString [_]
        (let [derefed (deref v)]

         (format "%s   %s  %s   %s" t k (if (fn? derefed) "FN" "") (meta v)))))


(defn print-all [nsthing]
    (let [
          {:keys [k v t]} nsthing
          m (meta v)
          n (:name m)
          doc (:doc m)

          src (cr/source-fn (symbol (str (:ns m))  (str (:name m))))]

        (printf "\n  ## %s ##\n" (name t))
        (println  "  name: " n)
;        (println  "  meta: " (cpp/write (dissoc met :ns) :stream nil))
        (println  "  meta: " (dissoc m :ns))
        (println  "   doc: " doc)
        (println  "   src: " src)))


(defn- tooltip [nsthing]
    (let [
          {:keys [k v t]} nsthing
          derefed (deref v)
          s (format "%s: \n%s   ->  %s \nfn? %s \n%s"
                    (name t)
                    k v
                    (fn? derefed)
                    ;(meta v)
                    (cpp/write (dissoc (meta v) :ns) :stream nil))]


        (doto (Tooltip. s)
            (. setFont (fx/SourceCodePro "Medium" 16))
            (. setStyle "
            -fx-padding: 5;
            -fx-background-radius: 2 2 2 2;
            -fx-text-fill: #2b292e;
            -fx-background-color: WHITESMOKE;"))))


(defn- a-namespace-listcell-factory []
    (let[]
;         lbl (fx/label)
 ;        tt (Tooltip.)

     (reify Callback
         (call [this view]
             (proxy [ListCell] []
                 (updateItem [item empty?]
                     (proxy-super updateItem item empty?)
                     (when-not empty?
                        ;(.setText  lbl (str item))
                         (.setText this (str item))
;                        (.setText tt (str "TT: " item))
                         (.setTooltip this  (tooltip item)))

                     this))))))


(defn- a-namespace-scene [namespace]
    (let [
          interns (map (fn [[k v]] (->NsThing k v :interned)) (ns-interns namespace))
          refers (map (fn [[k v]] (->NsThing k v :refered)) (ns-refers namespace))
          aliases (map (fn [[k v]] (->NsThing k v :aliased)) (ns-aliases namespace))
          imports (map (fn [[k v]] (->NsThing k v :imported)) (ns-imports namespace))

          all (concat
                  interns
                  refers
                  aliases
                  imports)


          view (fx/listview
                   (apply fx/observablearraylist-t Object
                          all))]



        (-> view
            .getSelectionModel
            .selectedItemProperty
            (.addListener
                (fx/changelistener [_ _ _ val]
                                   (print-all val))))

        (. view (setCellFactory (a-namespace-listcell-factory)))
        (fx/scene view)))



(defn- a-namespace-stage [namespace]
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title (str "namespace: " namespace)
                :location [(-> bounds .getMinX (+ 350))(-> bounds .getMinY (+ 120))]
                :size [350 350]
                :scene (a-namespace-scene namespace)))))





;;;; namespaces section ;;;;

(defn- namespaces []
    (sort #(compare (str %1) (str %2))
          (all-ns)))


(defn- namespaces-scene []
    (let [
          view (fx/listview (apply fx/observablearraylist (namespaces)))]

        (-> view
            .getSelectionModel
            .selectedItemProperty
            (.addListener
                (fx/changelistener [_ _ _ val]
                                   (println "namespace:" val)
                                   (a-namespace-stage val))))

        (fx/scene view)))


(defn- namespaces-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title "Namespaces"
                :location [(. bounds getMinX)(-> bounds .getMinY (+ 120))]
                :size [350 350]
                :scene (namespaces-scene)))))



(defonce ^:private namespaces-singleton (atom nil))

(defn show-or-create-namespaces-stage []
    (if @namespaces-singleton
        (. @namespaces-singleton toFront)
        (reset! namespaces-singleton
                (fx/setoncloserequest (namespaces-stage)
                                      #(do
                                          (println "reset!-ing namespaces-singleton to nil")
                                          (reset! namespaces-singleton nil))))))


;;; launcher section ;;;;


(defn- launcher-scene []
    (let [
          button-width
          150

          namespace-button
          (fx/button
              "Namespaces"
              :width button-width
              :onaction #(show-or-create-namespaces-stage)
              :tooltip "Open/show namespace-list")

          output-button
          (fx/button
              "Output"
              :width button-width
              :onaction show-or-create-output-stage
              :tooltip "Open/show output-window"
              )
          input-button
          (fx/button
              "Input"
              :width button-width
              :onaction input-stage
              :tooltip "Open a new input window / REPL"
              )

          logo
          (fx/imageview "graphics/George_logo.png")]



        (fx/scene
            (doto (fx/hbox
                   logo namespace-button output-button input-button
                   :spacing 20
                   :padding 20
                   :alignment Pos/CENTER_LEFT)
                (. setBackground (fx/color-background Color/WHITE))))))


(defn- launcher-stage []
    (let [bounds (. (fx/primary-screen) getVisualBounds)]

        (fx/now
            (fx/stage
                :style :utility
                :title "George"
                :location [(. bounds getMinX)(. bounds getMinY)]
                :size [(. bounds getWidth) 120]
                :scene (launcher-scene)))))



#_(defn- scene-root []
   (let [
         root (fx/borderpane
                :center (fx/rectangle :fill (fx/web-color "yellow"))
                :insets 0)]



     root))


#_(defn -main [& args]
   (fx/later
     (fx/stage
       :title "George :: core"
       :size [600 600]
       :scene (fx/scene (scene-root)))))




(defonce ^:private launcher-singleton (atom nil))

(defn show-or-create-launcher-stage []
    (if-not @launcher-singleton
        (reset! launcher-singleton
                (fx/setoncloserequest (launcher-stage)
                                      #(do
                                          (println "reset!-ing launcher-singleton to nil")
                                          (reset! launcher-singleton nil))))))



(defn -main []
    (show-or-create-launcher-stage)
    (show-or-create-output-stage)
    )


;;; DEV ;;;

(println "  ## WARNING: running george.core.core/-main")  (-main)