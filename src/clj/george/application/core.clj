;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.core)


(def proto-state
  {:dialog-listeners {}
   :application-stage nil})

(defonce ^:private state_ (atom nil))

(defn init-state []
  (reset! state_ proto-state))


;;;;


(defn add-dialog-listener
  "'k' should be a ns-qualified keyword.
  'fn' is a function of 1 argument. The argument will be a boolean telling the listener that is should yield or not -
  i.e. give up it's position of alwayontop."
  [k fn]
  (swap! state_ update-in [:dialog-listeners] assoc k fn))


(defn remove-dialog-listener [k]
  (swap! state_ update-in [:dialog-listeners] dissoc k))


(defn notify-dialog-listeners [yield?]
  (doseq [f (-> @state_ :dialog-listeners vals)]
    (try (f yield?) (catch Exception e (.printStackTrace e)))))


;;;;


(defn set-application-stage [stage]
  (swap! state_ assoc :application-stage stage))


(defn get-application-stage []
  (:application-stage @state_))