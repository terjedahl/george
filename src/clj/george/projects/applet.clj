;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.applet
  (:require
    [george.applet :refer [->AppletInfo]]
    [george.javafx :as fx]))


(defn icon [_ _]
  (fx/imageview "graphics/projects/Projects_32.png" :width 32))


(defn label []
  "George Projects")


(defn description []
  "George Projects:
Text-less step-by-step tutorials")


(defn main []
  (require  '[george.projects.core :as pro])
  ((resolve 'pro/show-projects-stage)))


(defn dispose []
  (((resolve 'pro/hide-projects-stage))))


(defn applet-info []
  (->AppletInfo 'label 'description 'icon 'main 'dispose))
