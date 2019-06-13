;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.locked
  (:require
    [clojure.java.browse :refer [browse-url]]
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.util.singleton :as singleton]
    [george.application.ui.styled :as styled]
    [george.projects.data :as d])
  (:import
    [javafx.scene.control Separator]
    [javafx.scene.input MouseEvent]
    [javafx.scene.paint Color]))


(defn- short-code? [s]
  (re-matches #"\d+-\d{4}" s))


(defn- license-key? [s]
  (re-matches #"[\d\D]{8}-[\d\D]{8}-[\d\D]{8}-[\d\D]{8}" s))


(defn- inform-unlock [{:keys [short-code product entered-key?]}]
  (fx/alert
    :title "Licence key valid"
    :owner (singleton/get :george.projects.core/projects-stage)
    :header "George Projects are now unlocked"
    :text (when entered-key?
            (format "The \"short-code\" for you license key is:

    %s

Share this code with your %s.

You can also find this code in Settings.
(The cogwheel top right)."
                    short-code
                    (if (= product "george-projects") "family" "pupils and colleagues")))))


(defn- get-licensed [field label]
  (let [label*   #(fx/later (doto label (.setTextFill %1) (.setText %2)) nil)
        update*  #(label* Color/GRAY %)
        fail*    #(label* Color/RED %)
        success* #(label* Color/BLUE %)

        x (-> field .getText .trim)]

    (cond
      (short-code? x)
      (if-let [k (do (update* "Fetching license key ...")
                     (d/get-license-key x))]
        (let [{:keys [valid message]  :as licensed}
              (do (update* "Verifying license key ...")
                  (d/verify-license-key k))]
          (if-not valid
            (fail* message)
            (do (success* "Success!")
                (assoc licensed :short-code x))))
        ;; else (not k)
        (fail* "No license key found for this short-code."))

      (license-key? x)
      (let [{:keys [valid message] :as licensed}
            (do (update* "Verifying license key ...")
                (d/verify-license-key x))]
        (if-not valid
          (fail* message)
          (let [c (do (update* "Fetching short-code ...")
                      (d/get-short-code x))]
            (success* "Success!")
            (assoc licensed :short-code c :entered-key? true))))

      :default
      (fail* "This does not look like short-code or license key."))))


(defn- process-unlock [field label]
  (when-let [licensed (get-licensed field label)]
    (fx/now (inform-unlock licensed))
    (d/set-licensed (dissoc licensed :entered-key?))
    true))


(defn unlock-dialog [on-unlock]
  (let [head-font  (fx/new-font fx/ROBOTO 18)
        text-font  (fx/new-font fx/OPEN_SANS 16)
        code-font  (fx/new-font fx/ROBOTO_MONO 16)
        head*      #(fx/text %1 :font head-font)
        text*      #(fx/text %1 :font text-font)
        tiny-code* #(fx/text %1 :font (fx/new-font fx/ROBOTO_MONO 12) :color Color/GRAY)
        code-f      (fx/textfield :prompt "short-code or license key here" :cols 36 :font code-font)
        feedback-lb (fx/new-label nil :font (fx/new-font fx/ROBOTO :normal :italic 16) :color Color/BLUE)

        close-bt    (fx/new-button "Close")
        unlock-bt   (fx/new-button "Unlock George Projects"
                                   :onaction  #(future (when (process-unlock code-f feedback-lb) (fx/later (.fire close-bt) (on-unlock)))))

        root
        (fx/vbox
          (text* "Enter a \"short code\" or license key and press 'Unlock ...'")
          (fx/vbox code-f (tiny-code* " xx-xxxx  /  xxxxxxxx-xxxxxxxx-xxxxxxxx-xxxxxxxx"))
          unlock-bt
          feedback-lb
          (Separator.)
          (head* "Don't have a short-code or license key?")
          (text* "Ask your teacher or parent for help.")
          (Separator.)
          (fx/vbox
            (text* "To purchase a license key, go to:")
            (doto
              (styled/new-link
                "www.george.andante.no/projects"
                #(browse-url "https://www.george.andante.no/projects?source=george-application"))
              (fx/set-font 16)))
          (Separator.)
          close-bt
          :spacing 20
          :padding 30)

        owner
        (singleton/get :george.projects.core/projects-stage)

        stage
        (styled/style-stage
         (fx/stage
            :title "Unlock George Projects"
            :style :utility
            :scene (fx/scene root); :size [400 200])
            :tofront true
            :centeronowner? true
            :owner owner))]

    (fx/set-onaction close-bt #(.hide stage))

    stage))


(defn clickable-lock [on-unlock]
  (doto
    (fx/new-label
      nil
      :graphic (doto (fx/icon "fas-lock:16") (fx/add-class "projects-list-lock"))
      :tooltip "Project Locked. Click to unlock.")
    (.addEventFilter
      MouseEvent/MOUSE_PRESSED
      (fx/new-eventhandler (unlock-dialog on-unlock) (.consume event)))))


(defn locked-pane [on-unlock]
  (fx/hbox
     (clickable-lock on-unlock)
     (fx/new-label "This project is locked." :size 15 :color fx/ANTHRECITE)
     (styled/new-link "Click to unlock" #(unlock-dialog on-unlock))
     :spacing 10 :padding [0 10 0 20] :alignment fx/Pos_CENTER_LEFT))


(defonce licensed-to_ (atom nil))


(defn- update-licensed-to [licensed]
  (when-let [tf @licensed-to_]
    (let [t (if-let [n (:licensed-to licensed)] (str "Licensed to\n" n) "")]
        (.setText tf t)
        (if (:valid licensed)
          (.setStyle tf "-fx-strikethrough: false;")
          (.setStyle tf "-fx-strikethrough: true;")))))


(add-watch d/licensed_ :licensed-to #(update-licensed-to %4))


(defn licensed-to []
  (reset! licensed-to_ (fx/text "..." :color Color/GRAY))
  (update-licensed-to (d/licensed))
  @licensed-to_)


;;;;; DEV


;(when (env :repl?) (fx/init) (fx/later (unlock-dialog #(do))))
