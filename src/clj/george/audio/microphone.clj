;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  george.audio.microphone

  (:require
    [clojure.pprint :refer [pprint pp]]
    ;[george.app.inspector :as inspector] :reload
    ;[george.app.stage :as stage] :reload

    [clojure.string :as cs]
    [clojure.java.io :as cio]
    [clojure.data :as data]
    [clojure.core.async :as async]

    [george.javafx :as fx]
    [george.application.core :as core])

  (:import
    [org.xiph.speex.spi SpeexEncoding]
    [javax.sound.sampled
     AudioFormat AudioFormat$Encoding
     DataLine$Info
     TargetDataLine SourceDataLine
     AudioSystem AudioInputStream
     Clip
     LineUnavailableException UnsupportedAudioFileException Mixer Line Line$Info]
    [java.nio ByteBuffer ByteOrder]
    [java.io ByteArrayOutputStream ByteArrayInputStream FileNotFoundException]
    [java.net URL MalformedURLException UnknownHostException]
    [javazoom.spi.mpeg.sampled.file MpegFileFormatType]
    [javafx.scene.control ToggleGroup RadioButton]
    [javafx.stage Stage StageStyle]
    [javafx.scene Scene]))


; (set! *warn-on-reflection* true)


(def DEFAULT_MIC_FORMAT
  (AudioFormat.
    AudioFormat$Encoding/PCM_SIGNED ; format
    (float 44100)  ; sample rate
    16        ; bits per sample (2 bytes)
    1          ; channels: mono!
    2          ; frame size in bytes - for PCM it is bytes pr sample x channels.
    (float 44100)  ; frame rate
    false))    ; is big endian


;(def DEFAULT_BUFFER_SIZE 4096)  ;; 44.1k Hz / 2k samples = 22 Hz
(def DEFAULT_BUFFER_SIZE
  (let [
        f  DEFAULT_MIC_FORMAT
        Ss (.getSampleRate f)
        Sz (/ (.getSampleSizeInBits f) 8) ;; /8 for bits to byte
        c  (.getChannels f)]
        
    (/ (* Ss Sz c) 8)))  ;; /8 for 1/8 second data


(def DEFAULT_SPEEX_FORMAT
  (AudioFormat.
    SpeexEncoding/SPEEX_Q6,
    (.getSampleRate DEFAULT_MIC_FORMAT)
    -1     ; sample size in bits
    (.getChannels DEFAULT_MIC_FORMAT)
    -1,     ; frame size
    -1,     ; frame rate
    false))


(def DEFAULT_MIC_TARGET_DATA_LINE_INFO
  (DataLine$Info. TargetDataLine DEFAULT_MIC_FORMAT))


(defn audio-bytes->samples [bytes is-big-endian]
  (let [
        tempBB
        (ByteBuffer/wrap bytes)]
        
    (when-not is-big-endian (.order tempBB ByteOrder/LITTLE_ENDIAN))
    (short-array
      (/ (count bytes) 2)
      (for [i (range (/ (count bytes) 2))]
        (.getShort tempBB)))))


(defn biggest-sample [samples]
  (max
    (apply max samples)
    (- (apply min samples))))


(defn target-data-line? [line-info]
  (= (.getLineClass line-info) TargetDataLine))


(defn get-directed-lineinfo-from-mixer [^Mixer mixer line-info]
  (if (target-data-line? line-info)
    (.getTargetLineInfo mixer line-info)
    (.getSourceLineInfo mixer line-info)))


(defn get-compatible-mixer-list [data-line-info]
  (->>
    ;; get mixer-info-list
    (AudioSystem/getMixerInfo)
    ;; extract mixers for each mixer-info
    (map
      (fn [mixer-info] (AudioSystem/getMixer mixer-info)))
    ;; only want mixers that have line-info for one or more compatible lines
    (filter
      (fn [mixer] (boolean (seq (get-directed-lineinfo-from-mixer mixer data-line-info)))))))


(defn print-mixer [mixer]
  (println mixer)
  (let [mixer-info (.getMixerInfo mixer)]
    (println "          mixer:" mixer)
    (println "           name:" (.getName mixer-info))
    (println "    description:" (.getDescription mixer-info))))


(defn print-mixerinfo [mixer]
  (let [mixer-info (.getMixerInfo mixer)]
    (println "          mixer-info:" mixer-info)))


(defn print-compatible-mixers []
  (println "\n# compatible mixers for given line info (mic-target-data-line-info)")
  (doseq [mixer (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO)]
    (print-mixer mixer)))


(defn contains-default [s] (or (.contains s "default") (.contains s "primary")))


(defn no-default [mixer-list]
  (filter #(-> % .getMixerInfo .getName .toLowerCase contains-default not) mixer-list))


(defn get-first-compatible-mixer-and-line [data-line-info]
  (println "get-first-compatible-mixer-and-line")
  (let [mixer ^Mixer (first (get-compatible-mixer-list data-line-info))
        line (.getLine  mixer data-line-info)]
    [mixer line]))


(def selected-mixer-atom (atom nil))


(add-watch selected-mixer-atom :selected-mixer-atom
           (fn [_ _ _ new-mixer]
             (println "selected-mixer-atom updated:")
             (print-mixer new-mixer)))


(defn get-selected-mixer-and-line [data-line-info]
  (println "get-selected-mixer-and-line")
  (if-let [mixer @selected-mixer-atom]
    [mixer (.getLine mixer data-line-info)]
    nil))


(defn ignite [lights prosent sticky-prosent]
  "'turn on' number of lights relative to prosent level and also lights the light at sticky-prosent"
  (let [len (count lights)
        lim-activate (Math/round ^double (* (/ len 100.) prosent))
        sticky-activate (Math/round ^double (* (/ len 100.) sticky-prosent))]
    
    (fx/later
      (doseq [l lights]
        (.setFill l fx/GREY))
      (doseq [l (take lim-activate lights)]
        (.setFill l fx/BLUE))
      (if(< 0 sticky-activate len)
        (.setFill (get lights sticky-activate) fx/BLUE)))))


(defn level-meter [label]
  (let [
        lights 
        (vec (repeatedly 15 #(fx/rectangle :size [12 36] :fill fx/BLUE :arc 8)))
          ;(for [i (range 15)]
            ;(doto
            ;  (javafx.scene.shape.Rectangle. 12. 36. fx/BLUE)
            ;  (.setArcWidth 8.)
            ;  (.setArcHeight 8.))))
        lights-pane 
        (apply fx/hbox (concat lights [:spacing 10 :padding 20]))

        outer-pane
        ;(doto (fx/vbox (fx/text label) lights-pane])
        ;  (.setPadding (javafx.geometry.Insets. 20. 20. 0. 20.))) 
        (fx/vbox 
          (fx/text label) 
          lights-pane
          :padding [20. 20. 0. 20.])]
        
    (def lights lights)
    (ignite lights 10 10)

    [outer-pane lights]))


(defn mixer-line-meter [^Mixer mixer line-info]
  (let [
        line
        (.getLine mixer line-info)
        [meter-pane meter-lights]
        (level-meter (-> mixer .getMixerInfo .getName))  ;; TODO: Also add description (and other goodies)

        radio-button
        (fx/radiobutton)]
        
    {:radio-button radio-button
     :mixer mixer
     :line line
     :meter-pane
     ;(doto (fx/hbox [radio-button meter-pane])
     ;  (.setAlignment fx/Pos_CENTER)
     ;  (fx/set-padding 0 0 0 20.)
     ;  )
     (fx/hbox radio-button meter-pane
       :alignment fx/Pos_CENTER
       :padding [0 0 0 20.])
  
     :meter-lights meter-lights}))



(defonce do-monitoring-atom (atom false))


(defn calculate-prosent [BAOS buffer len monitor-data-size is-big-endian]
  (.reset BAOS)
  (.write BAOS buffer 0 (if (> len monitor-data-size) monitor-data-size len))
  (-> BAOS .toByteArray (audio-bytes->samples is-big-endian) biggest-sample (/ Short/MAX_VALUE) (* 100.)))


(defn start-monitoring [{:keys [line meter-lights]} run-flag-atom]
  (println "start-monitoring")
  (reset! do-monitoring-atom true)
  (let [
        is-big-endian (.isBigEndian DEFAULT_MIC_FORMAT)
        AIS (AudioInputStream. line)
        buffer-size 4096 ;; 44.1k Hz / 2k samples = 22 Hz
        buffer (byte-array buffer-size)
        BAOS (ByteArrayOutputStream. buffer-size)
        monitor-data-size (/ buffer-size 8)]
        
    (future
      (.open ^TargetDataLine line DEFAULT_MIC_FORMAT)
      (.start line)
      (loop [len (.read AIS buffer) cnt 0 prev-prosent 0 prev-sticky-prosent 0 prev-sticky-countdown 0]
        (when (and @run-flag-atom (not= len -1))
          ;(println "looping ...")
          (let [
                do-monitor-update (= cnt 0)
                prosent
                (if do-monitor-update
                  (calculate-prosent BAOS buffer len monitor-data-size is-big-endian)
                  prev-prosent)
                [sticky-prosent sticky-countdown]
                (if (> prosent prev-sticky-prosent)
                  [prosent 30]
                  (if (= prev-sticky-countdown 0)
                    [0 0]
                    [prev-sticky-prosent (dec prev-sticky-countdown)]))

                sticky-prosent (if (> sticky-prosent 100) 100 sticky-prosent)
                sticky-prosent (if (< sticky-prosent 0) 0 sticky-prosent)]
                
            (when do-monitor-update
              (ignite meter-lights prosent sticky-prosent))
            (recur (.read AIS buffer) (mod (inc cnt) 2) prosent sticky-prosent sticky-countdown))))

      (doto line .stop .flush .close))))


(defn show-monitor0 []
  (fx/init)
  (fx/later
    (let [
          compatible-mixers-no-default
          (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))

          mixer-line-meter-list
          (map #(mixer-line-meter % DEFAULT_MIC_TARGET_DATA_LINE_INFO) compatible-mixers-no-default)

          meter-pane-list
          (map
            (fn [m-l-m]
              (:meter-pane m-l-m))

            mixer-line-meter-list)

          toggle-group (ToggleGroup.)
          _ (doseq [m-l-m mixer-line-meter-list]
              (doto (:radio-button m-l-m)
                (.setToggleGroup toggle-group)
                (fx/set-onaction 
                  (fn []
                    ;(println "mixer selected:")
                    ;(print-mixer (:mixer m-l-m))
                    (reset! selected-mixer-atom (:mixer m-l-m))))))
                    

          meters-pane
          (fx/vbox meter-pane-list)

          stage
          (doto (Stage. StageStyle/UTILITY)
            (.setScene (Scene. meters-pane))
            (.sizeToScene)
            (.setResizable false)
            (.setAlwaysOnTop true)
            (.setTitle "Microphone Monitor")

            (.setOnHidden
              (fx/event-handler
                 (println "monitor window closed")
                 (reset! do-monitoring-atom false)))

            (.show))]

      (doseq [LM mixer-line-meter-list]
        (start-monitoring LM do-monitoring-atom)))))
      

(defonce splitters-atom (atom {}))


(defn show-monitor1 []
  (fx/init)
  (fx/later
    (let [
          _ (add-watch splitters-atom :show-monitor-splitters-watch
                       (fn [key _ old-splitters-map new-splitters-map]
                         (println "  ## watch:" key)
                         (let [
                               [removed-mixers added-mixers _]
                               (data/diff
                                 (keys old-splitters-map)
                                 (keys new-splitters-map))]
                               
                           (if-not (empty? removed-mixers)
                             (println "  ## removed-mixers:" removed-mixers))
                             
                           (if-not (empty? added-mixers)
                             (println "  ## added-mixers:" added-mixers)))))
               
          compatible-mixers-no-default
          (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))

          mixer-line-meter-list
          (map #(mixer-line-meter % DEFAULT_MIC_TARGET_DATA_LINE_INFO) compatible-mixers-no-default)

          meter-pane-list
          (map :meter-pane  mixer-line-meter-list)

          toggle-group (ToggleGroup.)
          _ (doseq [m-l-m mixer-line-meter-list]
              (doto ^RadioButton (:radio-button m-l-m)
                (.setToggleGroup toggle-group)
                (fx/set-onaction 
                  (fn []
                    ;(println "mixer selected:")
                    ;(print-mixer (:mixer m-l-m))
                    (reset! selected-mixer-atom (:mixer m-l-m))))))
                    

          meters-pane
          (apply fx/vbox meter-pane-list)

          stage
          ;(doto (javafx.stage.Stage. javafx.stage.StageStyle/UTILITY)
          ;  (.setScene (javafx.scene.Scene. meters-pane))
          ;  (.sizeToScene)
          ;  (.setResizable false)
          ;  (.setAlwaysOnTop true)
          ;  (.setTitle "Microphone Monitor")
          ;
          ;  (.setOnHidden
          ;    (fx/event-handler
          ;       (println "monitor window closed")
          ;       (reset! do-monitoring-atom false)))
          ;
          ;  (.show))
          (fx/stage
            :style :utility
            :title "Microphone Monitor"
            :scene (fx/scene meters-pane)
            :location [20 40]
            :resizable false
            :alwaysontop true
            :onhidden
            (fx/event-handler
               (println "monitor window closed")
               (reset! do-monitoring-atom false)))]
          
      (doseq [LM mixer-line-meter-list]
        (start-monitoring LM do-monitoring-atom)))))


(defn get-mixer-and-line []
  (let [mixer-and-line
        (if-let [m&l (get-selected-mixer-and-line DEFAULT_MIC_TARGET_DATA_LINE_INFO)]
          m&l
          (get-first-compatible-mixer-and-line DEFAULT_MIC_TARGET_DATA_LINE_INFO))]     
    mixer-and-line))


(defn start-speex-record []
  "takes a map representing a 'speex-recorder' as arg
  returns a map with relavant values for this recording"
  (println "start-speex-record")
  (try
    (let [
          [mixer mic-TDL]
          (get-mixer-and-line)]

      (.open ^TargetDataLine mic-TDL DEFAULT_MIC_FORMAT)

      ;;setForeground(lf.topPanelStatusTextColor);
      ;;    setFont(lf.USER_NAME_FONT);
      ;;    setText("Mikrofontilgang: OK");
      (println "   # Mikrofontilgang: OK ")

      ;; start TDL before thread creation, to start capture as soon as possible
      (.start mic-TDL)

      (let [
            mic-AIS
            (AudioInputStream. mic-TDL)
            ;; wrap mic-AIS in speex-AIS for automatic conversion
            speex-AIS
            (AudioSystem/getAudioInputStream DEFAULT_SPEEX_FORMAT mic-AIS)

            buffer
            (byte-array DEFAULT_BUFFER_SIZE)

            captured-speex-BAOS
            (ByteArrayOutputStream. (* 2 2 2 DEFAULT_BUFFER_SIZE)) ;; give it a sensible start-size

            capture-thread
            (doto 
              (Thread.
                #(loop [len (.read speex-AIS buffer)]
                   (when (not= len -1)
                     (.write captured-speex-BAOS buffer 0 len)
                     (recur (.read speex-AIS buffer)))))
              .start)]

        [mic-TDL capture-thread captured-speex-BAOS]))

    (catch LineUnavailableException lue
      (.printStackTrace lue)
      (fx/later
            (fx/alert
              :owner (core/get-application-stage)
              :type :error
              :title "Line unavailable"
              :header "The selected line is \"unavailable\""
              :text
              (format "%s

View stacktrace bellow:" (.getMessage lue))
              :expandable-content 
              (fx/expandable-content  
                                     (->> lue .getStackTrace seq (cons (.toString lue )) (interpose "\n  ") (apply str)) 
                                     (fx/new-font "Source Code Pro" 12) 
                                     500)))
              
      ;;setText("<html>Mikrofonfeil:<br>Systemet får ikke kontakt med mikrofon!</html>");
      nil)))


(defn stop-speex-record [[mic-TDL capture-thread captured-Speex-BAOS]]
  "returns a byte-array fo captured speex"
  (println "stop-speex-record")
  (doto mic-TDL .stop .flush .close) ;; causes capture-thread loop to end and thread to exit
  (.join capture-thread) ;; wait for capture-thread to exit
  (.toByteArray captured-Speex-BAOS))


(defn add-subscriber-to-splitter [splitter status-fn outputstream]
  swap! ((:subscribers-atom splitter) conj {:status-fn status-fn :outputstream outputstream}))


(defn stop-splitter [mim {:keys [subscribers-atom running-atom] :as splitter}]
  (println "stop-splitter mim:" mim)
  (reset! running-atom false)
  ; inform subscribers
  (doseq [subscriber @subscribers-atom]
    (-> subscriber :status-fn false)))


(defn start-splitter [mim {:keys [mixer TDL  AIS subscribers-atom running-atom] :as splitter}]
  (println "start-splitter splitter:") (pprint splitter)
  (let [buffer (byte-array (/ DEFAULT_BUFFER_SIZE 2))]
    (reset! running-atom true)

    (future
      (println "  # splitter loop starting")
      (loop [len (.read AIS buffer)]
        (when (and (not= len -1) @running-atom)
          (doseq [subscriber @subscribers-atom]
            (.write (:outputstream subscriber) buffer 0 len))
          (recur (.read AIS buffer))))
      (println "  # splitter loop ended"))
      ;(doseq [OS @outputstreams-atom]
      ;    (doto OS .close .flush))
      ;(println "  # splitter outputstreams closed")
    
    ; inform subscribers
    (doseq [subscriber @subscribers-atom]
      (-> subscriber :status-fn splitter))))


(defn- get-TDL-from-mixer [mixer]
  (let [lines (.getTargetLines mixer)]
    ;(println "  empty?:" (empty? lines))
    ;(if-not (empty? lines)
    ;    (println "  count:" (count lines)))
    ;(println "  type:" (type lines))
    ;(println "  first:" (first lines))
    ;(println "  TDLs (getTargetLines): " lines)
    (if-not (empty? lines)
      (first lines)
      (.getLine mixer DEFAULT_MIC_TARGET_DATA_LINE_INFO))))


(comment .addLineListener TDL
         (reify javax.sound.sampled.LineListener
           (update [this line-event]
             (when (= (.getType line-event) javax.sound.sampled.LineEvent$Type/STOP)
               (println "   line-listener got STOP")
               (reset! run-atom false)
               (-> line-event .getLine (.removeLineListener this))))))


(defn create-splitter [mixer]
  "A splitter takes an mixer and returns an empty seq in an atom.
  It will continually read from the mixer's target-dataline's the audio-input-stream,
  and feed the read data to all output-streams in the atom's seq.
  Simply add or remove output-streams to the seq to tap into the feed.
  The splitter-thread can be stopped by calling stop and/or .close on the target-dataline"
  (println "create-splitter")
  (let [subscribers-atom (atom '())
        running-atom (atom false)

        TDL (get-TDL-from-mixer mixer)
        AIS (AudioInputStream. TDL)]
     
    (try
      (if-not (.isOpen ^TargetDataLine TDL)
        (.open ^TargetDataLine TDL DEFAULT_MIC_FORMAT))
      (.start TDL)

      {:mixer mixer
       :TDL TDL
       :AIS AIS
       :subscribers-atom subscribers-atom
       :running-atom running-atom}

      (catch LineUnavailableException lue
        (println " # LineUnavailableException (in create-splitter):" lue)
        (.printStackTrace lue)
        nil)

      (catch Exception e
        (println " # Exception (in create-splitter):" e)
        (.printStackTrace e)
        nil))))


(defn mixer->mim [mixer]
  (let [m (-> mixer .getMixerInfo)]
    {:name (apply str (take 30 (.getName m))) ;; a hack to avoid comparison issues if name is truncated
     :description (.getDescription m)
     :vendor (.getVendor m)
     :version (.getVersion m)}))


(defn splitter->mim [splitter]
  (mixer->mim (:mixer splitter)))


(defn update-splitters [splitters]
  ;(println "update-splitters-atomic")
  (let [
        ;_ (println " ## splitters:") _ (pprint splitters)

        current-running-splitters
        (filter #(-> % second :running-atom deref true?) splitters)
        _ (println "### current-running-splitters:") _ (pprint current-running-splitters)

        current-nonrunning-splitters
        (filter #(-> % second :running-atom deref false?) splitters)
        _ (println "### current-nonrunning-splitters:") _ (pprint current-nonrunning-splitters)

        found-mixers
        (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))

        found-mim-mixer-map
        (apply conj (map (fn [m] {(mixer->mim m) m}) found-mixers))
        _ (println "### found-mim-mixer-map:") _ (pprint found-mim-mixer-map)

        [removed-mixers added-mixers _]
        (data/diff
          (set (keys current-running-splitters))
          (set (keys found-mim-mixer-map)))

        ;; ensure not nil
        removed-mixers (if removed-mixers removed-mixers #{})
        added-mixers    (if added-mixers added-mixers #{})
        _ (println "### added-mixers:") _ (pprint added-mixers)

        ;; filter out any splitters that have already been stopped!
        old-mims
        (filter #(-> % splitters :running-atom deref true?) removed-mixers)
        _ (println "### removed-mixers:") _ (pprint removed-mixers)

        new-mims
        (filter #(-> % splitters nil?) added-mixers)
        _ (println "### new-mims:") _ (pprint new-mims)

        new-mims-splitters-maps
        (filter some?
                (map
                  (fn [mim]
                    (if-let[splitter (create-splitter(found-mim-mixer-map mim))]
                      {mim splitter}
                      nil))
                  new-mims))
        ;             new-mims-splitters-map
        ;            (filter (fn [[k v]] (some? v)) (seq new-mims-splitters-map))

        updated-splitters-map
        (apply conj splitters
               ; filter out nils - in case there was a problem creating new splitter
               (filter #(-> % second some?)
                       new-mims-splitters-maps))]
        ;_ (println "### updated-splitters-map:") _ (pprint updated-splitters-map)
        
    (doseq [mim old-mims]
      (stop-splitter mim (splitters mim)))

    (pprint updated-splitters-map)

    (doseq [mim added-mixers]
      (start-splitter mim (updated-splitters-map mim)))

    updated-splitters-map))


(defn update-splitters-atom []
  (println "update-splitters-atom")
  (swap! splitters-atom update-splitters)
  ;(Thread/sleep 500)
  ;(println "  splitters-atom:" splitters-atom)
  nil)


(defn start-update-splitters-loop []
  (let [flag (atom true)]
    (future
      (while @flag
        (update-splitters-atom)
        (Thread/sleep 3000))
      (println "start-update-splitters-loop ended"))
      
    flag))


;(def update-splitters-flag-atom (start-update-splitters-loop))

;(print-compatible-mixers)

;(do-update-splitters)

;(show-monitor1)


(defn monitor-pane [label]
  (let [
        [meter-pane meter-lights]
        (level-meter label)
        radio-button
        (fx/radiobutton)]
        
    (doto (fx/hbox [radio-button meter-pane])
      (.setAlignment fx/Pos_CENTER)
      (fx/set-padding 0 0 0 20.))))
      

(defn update-monitors-pane [current-mixers mim-monitor-map-atom monitors-vbox stage]
  (let [
        [_ added-mixers shared-mims]
        (data/diff (keys @mim-monitor-map-atom)(keys current-mixers))

        _ (println "added-mixers:" added-mixers)
        _ (println "shared-mims:" shared-mims)

        added-mixers (filter some? added-mixers)

        _ (if-not (empty? added-mixers)
            (reset! mim-monitor-map-atom 
                    (apply conj 
                           (map
                             (fn [mim]
                               {mim (monitor-pane (str (:name mim) "\n    (" (:description mim)")"))})
                             added-mixers))))]
    
    (fx/later
      (.addAll 
        (.getChildren monitors-vbox)
        (map #(@mim-monitor-map-atom %) added-mixers))

      ;(doto (.getChildren monitors-vbox) (.add (monitor-pane "another monitor still ")))

      (.sizeToScene stage))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;; new code


(defonce subscribers-atom (atom {}))  ;; [mim (atom '())]
(defonce mim-wrapper-map-atom (atom {})) ;; [mim wrapper]
(defonce found-mixers-prev-atom (atom {})) ;; [mim mixer]
(defonce found-mixers-curr-atom (atom {})) ;; [mim mixer]


(defn get-subscribers-list-atom
  "Returns the atom containing the subscribers to the given mim.
  If it doesn't exist, one will be created."
  [mim]
  (if-let[s-a (@subscribers-atom mim)]
    s-a
    ;; else
    (let [l-a (atom (list))]
      (swap! subscribers-atom assoc mim l-a)
      l-a)))


(defn get-mims-list []
  (keys @mim-wrapper-map-atom))


(defn find-mixers!
  "Queries system for compatible mixers, and sets mims for found splitters int root-level atom"
  []
  (println "find-mixers!")
  (let [mixer-list
        (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))]
    (reset! found-mixers-prev-atom @found-mixers-curr-atom)
    (reset! found-mixers-curr-atom
            (apply conj {}
                   (map (fn [mixer] {(mixer->mim mixer) mixer})
                        mixer-list)))))


(defn diff-found-mixers []
  (data/diff
    (set (keys @found-mixers-curr-atom))
    (set (keys @found-mixers-prev-atom))))
    
(defn added-mixers []   (first (diff-found-mixers)))
(defn removed-mixers [] (second (diff-found-mixers)))


(defn diff-lines []
  "returns mims for old, new, and common mims"
  (data/diff
    (set (keys @mim-wrapper-map-atom))
    (set (keys @found-mixers-curr-atom))))
    

(defn splitter [TDL]
  (println "splitter")
  (let [
        subscribers-atom (atom '())
        running-atom (atom false)

        AIS (AudioInputStream. TDL)]
    
    {:TDL TDL
     :AIS AIS
     :subscribers-atom subscribers-atom
     :running-atom running-atom})) 


(defn get-mixer [mim]
  (println "get-mixer")
  (println "  mim: " mim)
  (@found-mixers-curr-atom mim))


(defn open-TDL [TDL]
  (println "open-TDL")
  (.open ^TargetDataLine TDL DEFAULT_MIC_FORMAT)
  TDL)


(defn open-TDL-safe [TDL]
  (try
    (open-TDL TDL)

    (catch LineUnavailableException lue
      (println " # LineUnavailableException (in open-TDL-safe):" lue)
      (.printStackTrace lue)
      nil)

    (catch Exception e
      (println " # Exception (in open-TDL-safe):" e)
      (.printStackTrace e)
      nil)))
    

(defn- start-read-and-feed-loop [line chan count]
  (let [AIS (AudioInputStream. line)
        buffer (byte-array DEFAULT_BUFFER_SIZE)
        BAOS (ByteArrayOutputStream. DEFAULT_BUFFER_SIZE)]
    ;; the "read-and-feed loop"
    (async/go-loop [len (.read AIS buffer)]
      (if (or (= len -1) (= @count 0))
        (println "read-and-feed loop ended")
        ; else
        (do
          (when (not= len 0)
            (.write BAOS buffer 0 len)
            (async/>! chan (.toByteArray BAOS))
            (.reset BAOS))
          (println "read-and-feed looping ...")
          (recur (.read AIS buffer)))))))


(defn- tap-line
  "taps mult, increments tap-count, and starts loop if no previous taps"
  [{:keys [line mult count] :as line-wrapper} chan]
  (async/tap mult chan)
  (swap! count inc)
  (when (= @count 1)
    (start-read-and-feed-loop line (async/muxch* mult) count)))


(defn- untap-line
  "untaps mult, decrements tap-count, (causing loop to end if count becomes 0)"
  [{:keys [line mult count] :as line-wrapper} chan]
  (swap! count dec)
  (async/untap mult chan))


(defn- make-line-wrapper [line]
  (let [chan (async/chan (async/sliding-buffer 8))]
    {:line line
     :mult (async/mult chan)
     :count (atom 0)}))


(defn update-lines! []
  (println "update-lines!")
  (let [mims-for-mixer-to-add (second (diff-lines))]
    (pprint mims-for-mixer-to-add)
    (swap! mim-wrapper-map-atom
           #(apply conj % (map
                            (fn [mim]
                              {    mim
                               (make-line-wrapper
                                 (open-TDL-safe
                                   (get-TDL-from-mixer
                                     (get-mixer mim))))})
                            mims-for-mixer-to-add)))
    mims-for-mixer-to-add))


(defn start-stop-lines! []
  (println "start-stop-lines!")
  (let [added-mims (added-mixers)
        removed-mims (removed-mixers)]
    (println " start lines for mixers: ")
    (doseq [mim added-mims]
      (println "  mim:" mim)
      (doto (:line (@mim-wrapper-map-atom mim)) .start))
    
    (println " stop lines fo mixers: ")
    ( doseq [mim removed-mims]
      (println "  mim:" mim)
      (doto (:line (@mim-wrapper-map-atom mim)) .stop .flush))

    nil))

;(show-monitors)


(defn find-update-activate! []
  "queries sound-system, adds new lines, and turns lines on/off"
  (find-mixers!)
  (update-lines!)
  (start-stop-lines!))


(comment defn show-monitors []
         (fx/init)
         (fx/later
           (let [
                 mim-monitor-map-atom
                 (atom {})

                 monitors-vbox
                 (fx/vbox [])

                 stage
                 (doto (Stage. StageStyle/UTILITY)
                   (.setScene (Scene. monitors-vbox))
                   (.sizeToScene)
                   (.setResizable false)
                   (.setAlwaysOnTop true)
                   (.setTitle "Microphone Monitor")

                   (.setOnHidden
                     (fx/reify-EventHandler
                       #(do
                          (println "monitor window closed"))))
                          ;(reset! do-monitoring-atom false)
                          
                   (.show))

                 update-fn
                 #(update-monitors-pane
                    % mim-monitor-map-atom monitors-vbox stage)]

             (add-watch mim-wrapper-map-atom :splitters-microphone-monitor-watch
                        (fn [_ _ old-lines new-lines]
                          (when true ;(not= old-splitters new-splitters)
                            (println "mim-wrapper-map-atom changed")
                            (update-fn new-lines))))

             ;(update-splitters-atom)
             (update-fn @mim-wrapper-map-atom))))


(def monitors-meters-atom (atom {}))  ;; [mim meter-pane]


(def monitors-vbox (fx/vbox))


(def monitors-stage-atom (atom nil))


(defn diff-meters []
  "returns [mims-to-add mims-to-remove mims-in-common]"
  (data/diff
    (set (keys @mim-wrapper-map-atom))
    (set (keys @monitors-meters-atom))))

(defn update-meters-pane! []
  (println "update-meters-pane!")
  (let [
        mims-to-add
        (first(diff-meters))
        entries-to-add
        (apply conj {}
               (map
                 (fn [mim]
                   {mim (monitor-pane (str (:name mim) "\n    (" (:description mim)")"))})
                 mims-to-add))]

        
    (println "  mims-to-add:")(pprint mims-to-add)
    (println "  entries-to-add:")(pprint entries-to-add)
    (when mims-to-add
      (swap! monitors-meters-atom conj entries-to-add)
      (fx/later (.addAll (.getChildren monitors-vbox) (vals entries-to-add))))

    nil))


(defn update-monitors-stage! []
  (when @monitors-stage-atom
    (fx/later
      (update-meters-pane!)
      (.sizeToScene @monitors-stage-atom))))


(defn monitors-pane []
  (fx/borderpane :center monitors-vbox))


(defn -monitors-stage! []
  (if-let [stg @monitors-stage-atom]
    ;; is this relevant for utility-stage?!?
    (.setIconified stg false)
    ;;else
    (let [
          stg
          (doto (fx/stage (fx/stagestyle :utility))
            (.setScene (Scene. (monitors-pane)))
            (.sizeToScene)
            (.setResizable false)
            (.setAlwaysOnTop true)
            (.setTitle "George :: Microphones")

            (.setOnHidden
              (fx/event-handler
                 (println "monitors stage closed")
                 (reset! monitors-stage-atom nil))))]
          
      (reset! monitors-stage-atom stg)
      (update-monitors-stage!)
      (.show stg))))


(defn monitors-stage! []
  (fx/init)
  (fx/later (-monitors-stage!)))


;;;; "API" ;;;;;


;TODO: update registered monitors
;TODO: call this when new monitor is registered
(defn poll-once!
  "Polls the computers system once for available mixers/micrphones in the DEFAULT_MIXER_FORMAT
Updates an internal listing.
Updates registered any shown/visible monitors.
Can be called directly for an immediate refresh.
This function si the same as is called by the polling loop started by start-polling!
Is also called immediately when a new monitor is registered.
"
  []
  (find-update-activate!))

  ; TODO: implement here
  


(def
  ^{  :private true
    :doc "Boolean flag for polling loop.
        start-polling! sets it to true (and then starts the polling loop),
        stop-polling! sets it to false (which will cause the polling loop to end)."}
  do-polling
  (atom false))

(def
  ^{:doc "The interval (in milliseconds) the systme will be polled for updates in microphone status and availability.
Changing it (with alter-var-root) will dynamically change the interval."}
  polling-interval (atom 5000))


(defn start-polling!
  "Sets do-polling atom to true and starts polling loop.
  The interval can be changed dynamically by altering polling-interval.
  The loop is stopped with stop-polling!"
  []
  (println "start-polling-loop!")
  (println "INCOMPLETE IMPL!") ; TODO
  (reset! do-polling true)
  (async/go-loop []
    (if @do-polling
      (do
        (async/<! (async/timeout @polling-interval))
        (println "polling loop ...")
        ;; TODO actual polling here!
        (poll-once!)
        (recur))
      (println "polling ended"))))


(defn stop-polling! []
  (println "stop-polling!")
  (reset! do-polling false))



(comment
  
  (defn show-monitors!
    "Show a list of microphone monitors (live meters).
They system is polled every 5 seconds for changes.
Optional true/false indicates show/hide. (Defaults to true).
Monitors are automatically updated according to *poll-system* and *poll-system-interval*
Implemnented as a sinleton.
If hidden/minimzed, then de-minize/reveal.
    "
    ([]
     (show-monitor true))
    ([bool]))
      ; TODO: implement here
      

  (defn monitors-showing?
    "Returns true if monitors-window is 'showing'
Returns true also if moniors-window is 'miniized, or not very visible.'
Pehaps monitors-visible? instead."
    [])
    ; TODO: implement here
    

  (defn monitors-visible?
    "Returns true if monitors-window is 'showing' and is not minimized or in other knowable ways not very visible.
This is an augmented version of monitors-showing?"
    [])
    ; TODO: implement here
    


  (defn selected-mixer-ID
    "returns an internal ID (a map) representing a mixer (and its input-line an 'splitter').
If no mixer as been "
    ([]
     (selected-mixer-ID true))
    ([use-default-selection]))
      ; TODO: implement here
      

  (defn monitor
    "Returns a JavaFX pane with a 'live' meter for the given mixer-ID.
Must be called on an FX thread.
Throws exeption if erroneous mixer-ID given.
Use dynamic monitor, if a pane automatically updated containing currently selected monitor is preffered."
    [mixer-ID])
    ; TODO: implement
    

  (defn dynamic-monitor []
    "Returns a JavaFX pane containing pane representing currently selected monitor
(based on default result of call to selected-mixer-ID).
Must be called on an FX thread.
Whenever selected-mixer-ID changes, content of this pane will automatically update.")
    ; TODO: implement
    

  (defn tap
    "connects a channel to the mult of mixer-ID.
Defaults to (selected-mixer-ID).
Transmits arrays of bytes from mixer's TargetDataLine.
A channel with sliding-buffer is preferable.
"
    ([]
     (tap channel (selected-mixer-ID)))
    [mixer-ID]))
    ; TODO: implement
     ;; end comment


(defn dev
  "for developing this component"
  []
  "how should this work?
      1. polling is started
      2. for each mixer, put it in a map together with a chan in a mult.
      3. for each active mixer, wrap it in a go-loop
  "
  (comment let [c (async/chan 1)]
           (async/go-loop []
             (if-some [r (async/<! c)]
               (do (println "r:" r)
                   (recur))
               (do
                 (async/<! (async/timeout 1000))
                 (println "go-loop ended"))))
           (async/>!! c "hello") (async/>!! c "world") (async/close! c))

  (comment let [run-atom (atom true)]
           (async/go-loop []
             (when @run-atom
               (async/<! (async/timeout 10))
               (println "looping ...")
               (recur)))
           (async/go
             (async/<! (async/timeout 50))
             (println "stopping loop.")
             (reset! run-atom false)))
  
  (poll-once!)
  (println "## lines:")
  (doseq [[mim line] @mim-wrapper-map-atom]
    (pprint mim)))
    ;(println "  line open?:" (.isOpen line))))


;(dev)