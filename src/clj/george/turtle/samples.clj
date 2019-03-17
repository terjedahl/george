;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.turtle.samples
  (:require
    [george.turtle :refer :all]
    [george.turtle.extra :as aux]
    [george.javafx :as fx]))


;(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)


;;;;; multi-tree

(defn- red [^double len]
  (* (+ 0.5 (* 0.1 ^int (rand-int 5))) len))

(defn- col [^double len]
  (if (< len 10) "green" "brown"))

(defn- ang []
  (+ 10 ^int (rand-int 45)))

(defn- wid [^double len]
  (let [w (/ len 15.)]
    (if (< w 1) 1 w)))

(defn- line [t ^double len]
  (set-width t (wid (Math/abs len)))
  (set-color t (col (Math/abs len)))
  (forward t len))

(defn- tree [t ^double len]
  (when (> len 2)
    (line t len)
    (let [t1 (clone-turtle t)
          t2 (clone-turtle t)]
      (future
        (left t1 (ang))
        (tree t1 (red len)))
      (future
        (right t2 (ang))
        (tree  t2 (red len)))))
  (delete-turtle t))


(defn multi-tree 
  "*May a thousand turtles bloom*  
      
  * * *

  *Learn how to build this animation yourself!*   
  Improve it. Share it. 
  Then go create something new and amazing.  
  Its in [\"George - the book\"](http://www.george.andante.no/book)."
  [& [len]]
  (screen [700 450])
  (reset)
  (left 90)
  (pen-up) 
  (forward (- (* 2 (double (or len 50))))) 
  (pen-down)
  (set-speed nil)
  (tree (turtle) (or len 50)))



;;;;;;;; asteroids


(def SCORE_LIFE_POS [-280 200])


(defn-  spaceship-shape []
  ;; Could be implemented with a group of lines in stead
  (aux/new-polygon [[-8 5] [8 0] [-8 -5]]
                   :color :white))
  

(defn- shot-shape []
  ;; Could be implemented with a line in stead
  (aux/new-rectangle :size [2 1] 
                     :color :white))


(defn- rock-shape [size]
  ;; Better design with a few "random" polygon designs
  (aux/new-rectangle
    :size ({3 [50 50] 2 [30 30] 1 [15 15]} size)
    :color :white))


(defn rand+- []
 (rand-nth [1 -1]))


(defn- new-rock [loc ^long size]
  (let [three? (= size 3)]
    (new-turtle
      :name :rock
      :speed nil
      :down false 
      :shape (rock-shape size)
      :position loc
      :heading (if three?
                   (+ ^int (rand-nth [0 90 180 280]) (* ^int (rand-int 30) ^int (rand+-)))
                   (rand-int 360))
      :props {:size size
              :step (if three?  1 (rand-nth [1 1.2  1.7 2.8]))
              :points (case size 
                        3 20 
                        2 50 
                        1 100)})))


(defn- make-rocks []
  (rep 5
       (let [x (* (+ ^int (rand-int 100) 100) ^int (rand+-))
             y (* (+ ^int (rand-int 100) 100) ^int (rand+-))]
         (new-rock [x y] 3))))


(defn- make-rocks-maybe [^long life-count rocks rock]
  (when (and (> life-count 0)
             (= (count rocks) 1)
             (= (get-prop rock :size) 1))
    (make-rocks)))


(defn- new-life-turtle [^long life-index]
  (doto
    (new-turtle :name :life 
                :down false 
                :speed nil 
                :heading 90 
                :shape (spaceship-shape))
    (move-to (let [[^int x ^int y] SCORE_LIFE_POS]
               [(+ x 8 (* life-index 16))
                (- y 32)]))))


(defn- update-life-count [life-count_ diff]
  (->> (get-all-turtles)
       (filter #(= (get-name %) :life))
       (mapv delete-turtle))
  (dotimes [i (swap! life-count_ + diff)]
    (new-life-turtle i)))


(defn- update-score
  [score-turtle points]
  (when points
        (doto score-turtle
          (swap-prop :score #(+ ^int % points))
          (undo)))
  (write score-turtle (get-prop score-turtle :score) false))


(defn- new-score-turtle []
  (doto
    (new-turtle
      :name :score
      :down false
      :speed 10
      :visible false
      :color :white
      :font [fx/SOURCE_CODE_PRO 24]
      :undo 2
      :props {:score 0})
    (move-to SCORE_LIFE_POS)
    (update-score nil)))


(defn-  blast-rock [rock scorekeeper]
  (let [^int size (get-prop rock :size)
        loc (get-position rock)
        points (get-prop rock :points)]
    (delete-turtle rock)
    ;; TODO: add explosion ?
    (when (> size 1)
          (rep 2 (new-rock loc (dec size))))
    (update-score scorekeeper points)))


(defn- thrust [physics spaceship]
  (when (is-visible spaceship)
    (let [physics-speed  (get-prop physics :step)
          physics-heading (get-heading physics)
          spaceship-heading (get-heading spaceship)
          [^double new-speed new-heading] 
          (aux/add-vectors [physics-speed physics-heading]
                           [0.25 spaceship-heading])]
      (doto physics
        (set-prop :step (min new-speed 8))    
        (set-heading new-heading)))))


(defn- break [physics]
  (swap-prop physics :step #(max 0 (- ^double % 0.25))))


(defn- drag [physics]
  (swap-prop physics :step #(max 0 (- ^double % 0.001))))


(defn- turn [spaceship degrees]
  (when (is-visible spaceship)
    (left spaceship degrees)))


(defn- shoot [physics spaceship]
  ;; When keeping key pressed down, a stream fo shots is release. Could be prevented with some sort of timer. 
  (when (is-visible spaceship)
    (new-turtle
      :name     :shot
      :down     false
      :speed    nil
      :position (get-position physics)
      :heading  (get-heading spaceship)
      :shape     (shot-shape)
      :props    {:step 10})))


(defn- write-game-over []
  (with-turtle
    (new-turtle
      :visible false
      :color :white
      :position [-100 16]
      :font [fx/SOURCE_CODE_PRO_MEDIUM 32])
    (write "game over")))


(defn- set-keys [physics spaceship]
  (set-onkey [:UP]           #(thrust physics spaceship))
  (set-onkey [:DOWN]         #(break physics))
  (set-onkey [:LEFT]         #(turn spaceship 10))
  (set-onkey [:RIGHT]        #(turn spaceship -10))
  ;; When shooting, the other key-downs are disabled.  
  ;; Hard to fix while maintaining a simple interface.
  ;; Would require some sort of custom EventHandler to pass to the javafx keyevent-functions.
  (set-onkey [:SPACE] #(shoot physics spaceship))
  (set-onkey [:P]     #(if (is-ticker-running) (stop-ticker) (start-ticker))))


(defn blink-in [spaceship]
  (hide spaceship)
  (let [blinker (clone-turtle spaceship)]
    (dotimes [i 10]
      (set-visible blinker (even? i))    
      (sleep 100))
    (delete-turtle blinker))
  (show spaceship))


(defn- game-loop [physics spaceship scorekeeper life-count_]
  (doseq [t (get-all-turtles)]
    (with-turtle t

      (when-let [step (get-prop :step)]
        (forward step))
                 
      (set-position spaceship (get-position physics))
      
      (drag physics)
                 
      (let [rocks 
            (filter #(= (get-name %) :rock)  
                    (get-all-turtles))]

        ;; hit
        (when (= (get-name) :shot)
          (when-let [rock (first (get-overlappers t rocks))]
            (delete-turtle t)
            (blast-rock rock scorekeeper)
            (make-rocks-maybe @life-count_ rocks rock)))

        ;; death
        (when (and (= t physics) (is-visible spaceship))
          (when-let [rock (first (get-overlappers t rocks))]

            (hide spaceship)
            ;; TODO: add explosion ?
            (blast-rock rock scorekeeper)
            (update-life-count life-count_ -1)
            (if (> ^int @life-count_ 0)
              (do
                  (make-rocks-maybe @life-count_ rocks rock)
                  (doto physics
                    (set-prop :step 0)
                    (home))
                  (doto spaceship
                    (home)
                    (set-heading 90)
                    (future (blink-in spaceship))))
              (write-game-over))))))))


(defn asteroids 
  "Yes, the classic.  

  Not prefect, but the gameplay is pretty close.
  Read about Asteroids on [Wikepedia](https://en.wikipedia.org/wiki/Asteroids_%28video_game%29).
    
  Try the original version online at [FreeAsteroids.org](http://www.freeasteroids.org/).

  * * *

  *Learn how to build this game yourself!*  
  Improve it. Share it. 
  Then build other cool games.  
  Its in [\"George - the book\"](http://www.george.andante.no/book).
  "
  []
  (reset false)
  (set-background :black)
  (to-front)
  
  (let [spaceship
        (new-turtle
          :name :spaceship
          :speed nil
          :down false
          :shape (spaceship-shape)
          :heading 90
          :visible false)
        
        physics
        (new-turtle
          :name :physics
          :speed nil
          :down false
          :visible false
          :props {:step 0})
 
        scorekeeper
        (new-score-turtle)

        life-count_ 
        (atom 3)]

    (update-life-count life-count_ 0)

    (set-keys physics spaceship)

    (set-fence
      {:fence   (fn [turtle] (if (= (get-name turtle) :shot) :stop :wrap))
       :onfence (fn [turtle _] (when (= (get-name turtle) :shot)
                                     (delete-turtle turtle)))})

    (make-rocks)
    (set-ticker 
      #(game-loop physics spaceship scorekeeper life-count_))
    (start-ticker)
    (blink-in spaceship)))


;; TODO: add better spaceship-design?
;; TODO: add better rock-design?
;; TODO: Thrust indicator (jet)
;; Missing: Extra lives
;; Missing: UFOs
;; Missing: The full arcade experience: "New game", High score, etc.

;(asteroids)


;;;;


(defn- jump [d]
  (let [down? (is-down)]
    (pen-up)
    (forward d)
    (set-down down?)))

(defn- jump-to
  "Same as 'jump', but with a position arg in stead of a distance."
  [pos]
  (let [down? (is-down)
        orig-heading (get-heading)]
    (pen-up)
    (turn-to pos)
    (move-to pos)
    (set-down down?)
    (set-heading orig-heading)))


(defn- jump-home []
  (pen-up) (home) (pen-down))

(defn- center* [w c]
  (set-width w) (set-color c) (forward 0.1) (home))

(defn- center []
  (doseq [r [100 85 70 55 40]]
    (center* r (if (even? r) :black :white))
    (sleep 300)))


(defn- pedals* [w c d]
  (set-width w) (set-color c)
  (dotimes [i 8]
    (left (* i 45)) (jump d) (forward 1) (jump-home)))


(defn- pedals []
  (home)
  (set-speed 1)
  (pedals* 60 :pink 56)
  (pedals* 50 :hotpink 53)
  (pedals* 40 :deeppink 50))


(defn- grass []
  (set-color :green)
  (jump-to [-280 -200])
  (set-speed 10)
  (rep 90
       (let [len (+ 50 (rand-int 80))]
         (set-heading 88)
         (forward len)
         (set-heading 92)
         (backward len)))
  (set-speed 1))
;(grass)


(defn- stem []
  (set-color :green)
  (set-width 10)
  (jump-to [0 -200])
  (move-to [0 0]))
;(stem)


(defn flower 
  "A pretty flower taken from a childrens' picture book.
  Do: `(samples/flower)`"

  []
  (reset)
  (set-round true)
  (grass)
  (stem)
  (pedals)
  (center))
;(flower)


;;;;


(def SIZE 20)
(def HALF (/ SIZE 2))
(def BEEDS1[:crimson :gold :green :blue])
(def BEEDS2[:indigo :slateblue "#f3f3f3"  :blueviolet])
(def POS1 [-100 -60])
(def POS2 [-70 -60])


(defn segments1 [start end]
  [
   [forward (- 150 start)]
   [arc-right 20 90] 
   [forward 20]
   [arc-right 20 90] 
   [forward 30]
   [arc-left 20 90]
   [forward 30]
   [arc-left 30 360]  ;; loop
   [forward 30]
   [arc-left 20 90]
   [forward 100]
   [arc-right 20 180] ;; top
   [forward (- 220 end)]])


(defn segments2 [start end]
  [
   [forward (- 180 start)]
   [arc-right 20 90] 
   [forward 170]

   [arc-right 100 20]
   [arc-right 20 140]
   [arc-right 100 20]

   [arc-right 100 20]
   [arc-right 10 140]
   [arc-right 100 20]

   [arc-right 100 20]
   [arc-right 20 140]
   [arc-right 100 20]

   [forward 100]

   [arc-left 20 90]
   [forward 20]

   [forward (- 92 end)]])


(defn- path [segments-fn start end forward?]
  ;(prn 'path start end)
  (let [segs (segments-fn start end)]
    (if forward?
      (doseq [[f & args] segs]           (apply f args))
      (doseq [[f & args] (reverse segs)] (apply f (map - args))))))


(defn- path1 [start end forward?]
  (path segments1 start end forward?)) 


(defn- path2 [start end forward?]
  (path segments2 start end forward?))


;; path-fn path-start-pos rail-color beed-colors
(def RAILS [[path1 POS1 :red BEEDS1]
            [path2 POS2 :darkblue BEEDS2]])


(defn- ahead-of-me []
  "Returns the turtle ahead of me, if any, else nil"
  (let [index (get-prop :index)
        state (get-prop :state)
        others_ (get-prop :others_)  
        filter-fn 
        #(let [index-2 (get-prop % :index)
               state-2 (get-prop % :state)]
           (and (not= state-2 :running)
                (= state-2 state) 
                (= index-2 (if (= state-2 :start) (inc index) (dec index)))))]
    (first (filter filter-fn @others_))))


(defn offset 
  "index 0 is the first at start, and the last at end."
  [at-start?]
  (let [index (get-prop :index)
        others_ (get-prop :others_)
        tot (dec (count @others_))]
    (+ 
      (* 
        (if at-start? index (- tot index))
        (inc SIZE))  ;; inc allows space for 1px edge
      HALF)))


(defn- run-rail 
  "figures out what the offset is based on :prop :nr, and updates the prop :state to one of :running and then to :end or :start"
  [path-fn]
  (let [state (get-prop  :state)]
    (when-not (= state :running)
      (when-let  [ahead (ahead-of-me)]
        (future (with-turtle ahead (run-rail path-fn))))
      (let [at-start? (= state :start)
            start-offset (offset true)
            end-offset (offset false)]
        
        (set-prop :state :running)
        (path-fn start-offset end-offset at-start?)
        (set-prop :state (if at-start? :end :start))))))


(defn- new-beed [index color others_ path-fn [xp yp :as pos]]
  (sleep 200)
  (let [turt 
        (new-turtle :heading 90
                    :speed 4 
                    :color :saddlebrown :fill color :down true
                    :props {:index index :others_ others_ :state :start})]
  
    (with-turtle turt
      (set-position [xp (+ (offset true) yp)])
      (let [circ (shapes (filled turt (arc-right HALF 360)))]
        (set-shape circ)
        (set-center circ)
        (set-speed 1)
        (set-down false)
        (set-onclick #(with-turtle turt  (run-rail path-fn)))))
    turt))
      

(defn- draw-path [path-fn pos color]
  (with-turtle (new-turtle :position pos :heading 90 :round true :color color :width 5 :speed 4)
    (path-fn 0 0 true)
    (delete-turtle)))


(defn- draw-base [[x y]]
  (let [arc-segments [[10 50] [50 20] [300 40] [50 20] [10 50]]]
    (with-turtle (new-turtle :position [(- x 20) y] :heading 90 :speed 4 :round true :width 2 :color :sienna :fill :burlywood)
      (filled
        (rep 2
          (doseq [seg arc-segments] (apply arc-right seg))
          (forward 30)))
      (right 180)
      (doseq [seg arc-segments] (apply arc-left seg))
      (delete-turtle))))


(defn- write-info [pos text remove-after]
  (with-turtle (new-turtle :position pos :visible false  :undo 1 :down false)
    (write text)  
    (when remove-after
      (sleep remove-after)
      (undo))
    (delete-turtle)))


(defn- usage []
  (sleep 4000)
  (write-info [ -90 -150] "(Click beeds to play.)" 2000)
  (write-info [-140 -170] "(Click screen to toggle auto-play.)" 2000))


(defn- screen-onclick [beeds_]
  (if (is-ticker-running)
      (do
        (stop-ticker)
        (write-info [-80 -200] "Auto-play stopped" 1000))
      (do
        (set-ticker 2000 #(-> @beeds_ rand-nth do-onclick))
        (start-ticker)
        (write-info [-80 -180] "Auto-play started ..." 1000))))


(defn rail-maze 
  "[rail-maze images](https://duckduckgo.com/?q=bead+rail+maze&iar=images)
  "
  [& [auto-play?]]
  (reset false)
  (set-background :oldlace)
  (draw-base POS1)
  (let [all-beeds_ (atom [])]
    (doseq [[path-fn pos rail-color beed-colors] RAILS]
      (sleep 500)
      (future
        (draw-path path-fn pos rail-color)
        (let [others_ (atom [])
              beeds (vec (map-indexed #(new-beed %1 %2 others_ path-fn pos) beed-colors))]
          (reset! others_ beeds)
          ;; reset! returns the new value. 
          ;; If you run 'rail-maze' at a REPL, the repl may try to print out this.
          ;; This will cause a stack-overflow, as the beed contains a reference to all beeds (in other_) which in turn contains reference to all beeds etc.
          ;; Therefore, make sure to return 'nil' or some other value.
          ;nil
          (swap! all-beeds_ concat beeds))))
    (set-screen-onclick #(screen-onclick all-beeds_))
    (usage)
    (when auto-play? (do-screen-onclick))))

;(rail-maze)
;(rail-maze true)

