;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.core)


(def default-state 
  {
   ;; map of 1 arg functions called synchronously before and after quit-dialog is shown/hidden. 
   ;; Arg is one of :show :quit :cancel
   :quit-dialog-listeners {}
   
   :application-stage nil})

(defonce ^:private state_ (atom nil))

(defn init-state []
  (reset! state_ default-state))


;;;;


(defn add-quit-dialog-listener [k fn]
  (swap! state_ update-in [:quit-dialog-listeners] assoc k fn))


(defn remove-quit-dialog-listener [k]
  (swap! state_ update-in [:quit-dialog-listeners] dissoc k))


(defn call-quit-dialog-listeners [kw]
  (doseq [f (-> @state_ :quit-dialog-listeners vals)]
    (try (f kw) (catch Exception e (.printStackTrace e)))))


;;;;


(defn set-application-stage [stage]
  (swap! state_ assoc :application-stage stage))


(defn get-application-stage []
  (:application-stage @state_))