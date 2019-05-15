;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.code
  (:require
    [clojure.string :as cs]
    [george.javafx :as fx]
    [george.code.tokenizer :as gct]
    [george.util.java :as guj]
    [common.george.util.text :as gut :refer [pformat pprint]])
  (:import
    [org.fxmisc.richtext InlineCssTextArea]
    [com.github.difflib.text DiffRowGenerator DiffRow]
    [java.util List ArrayList]
    [org.fxmisc.flowless VirtualizedScrollPane]))


(def SP (.charAt "\u3007" 0))
(def LT (.charAt "\u3008" 0))
(def GT (.charAt "\u3009" 0))

(def DEL_START (.charAt "\u300a" 0))
(def DEL_END   (.charAt "\u300b" 0))
(def ADD_START (.charAt "\u301a" 0))
(def ADD_END   (.charAt "\u301b" 0))
(def starts    #{DEL_START ADD_START})
(def dels-adds #{DEL_START DEL_END ADD_START ADD_END})

(def coll-delim-chars
  ;#{\{ \[ \( \) \] \}}
  #{\{ \[  \) \] \}})
  ;#{\{ \[  \] \}})

(def punctuation-chars
  #{\. \, \! \?})


(defn- clojure-char? [ch]
  (or
    (coll-delim-chars ch)
    (gut/readermacro-char? ch) (gut/macro-char? ch) (gut/comment-char? ch) (gut/string-delim-char? ch)))


(defn- println-> [s]
  (println s) s)


(defn- prn-> [s]
  (prn s) s)


(defn- pprint-> [d]
  (pprint d) d)


(defn- new-textarea [& [s]]
  (doto (if s (InlineCssTextArea. ^String s) (InlineCssTextArea.))
    (.setStyle "-fx-font-size: 18; -fx-font-family: 'Source Code Pro'; -fx-padding: 5;")
    (.setDisable true))) ;; Prevent copying!


(defn- read-default [rdr ch]
  (let [sb (StringBuilder.)]
    (.append sb ch)
    (loop []
      (let [ch (gct/read-char rdr)]
        (cond
          (not ch)
          (str sb)

          (or (gut/newline-char? ch) (clojure-char? ch) (gut/whitespace-char? ch) (punctuation-chars ch))
          (do (gct/unread-char rdr ch) (str sb))

          ;(gut/whitespace-char? ch)
          ;(do (.append sb ch) (str sb))

          :default
          (do (.append sb ch) (recur)))))))


(defn split-lines [s]
  (if (cs/blank? s)
    '()
    (cs/split s #"\r?\n" -1)))


(defn- not-edit? [[[old-nr new-nr] _]]
  (and old-nr new-nr))


(def collapsed [[0 0] :collapsed])


(defn- collapse-lines [lines]
  (loop [lines0 lines lines1 '()]
    (if (empty? lines0)
      lines1
      (if (not-edit? (first lines0))
        (let [taken (take-while not-edit? lines0)
              after (drop-while not-edit? lines0)
              first? (empty? lines1)
              last? (= (count taken) (count lines0))
              cnt   (count taken)
              taken (if (> cnt 6)
                      (concat
                        (if first? '() (take 3 taken))
                        (list collapsed)
                        (if last? '() (take-last 3 taken)))
                      taken)]
          (recur after (concat lines1 taken)))
        (recur (rest lines0) (concat lines1 (list (first lines0))))))))


(def CSS {:collapsed
          "-fx-fill: #bbb;
          -rtfx-background-color: #fff;
          -fx-font-size: 20;"
          :nr  ;; line number
          "-fx-fill: gray;
          -fx-font-size: 16;"
          :nr-old
          "-fx-fill: gray;
          -rtfx-background-color: #ddd;
          -fx-font-size: 16;"
          :- ;; delete
          "-fx-strikethrough: true;
          -fx-fill: red;
          -rtfx-background-color: #ffe0e0;"
          :+ ;; add
          "-fx-fill: blue;
          -rtfx-background-color: lightblue;"
          := ;; default
          "-rtfx-background-color: white;"
          :=old
          "-fx-fill: gray;
          -rtfx-background-color: #ddd;"})


(defn- append [textarea txt style-k]
  (let [i (.getLength textarea)]
    (.appendText textarea txt)
    (.setStyle textarea i (+ i (count txt)) (CSS style-k))
    textarea))


(defn- read-span [rdr ch]
  (let [sb (doto (StringBuilder.) (.append ch))]
    (loop []
      (let [ch (gct/read-char rdr)]
        (cond
          (nil? ch)
          (str sb)

          (or (gut/whitespace-char? ch) (clojure-char? ch) (punctuation-chars ch))
          (do (gct/unread-char rdr ch) (str sb))

          :default
          (do (.append sb ch) (recur)))))))


(defn- parse [s]
  (let [rdr (gct/indexing-pushback-stringreader s)]
    (loop [res (transient [])]
      (let [ch (gct/read-char rdr)]
        (if (nil? ch)
          (persistent! res)
          (let [span
                (cond

                  (or (gut/whitespace-char? ch) (clojure-char? ch) (punctuation-chars ch))
                  (str ch)

                  :default
                  (read-span rdr ch))]

            (recur (conj! res span))))))))


(defn- read-tagged [rdr end]
  (let [sb (StringBuilder.)]
    (loop []
      (let [ch (gct/read-char rdr)
            ch (condp = ch
                 SP \space
                 LT \<
                 GT \>
                 ch)]
        (cond
          (nil? ch)  (str sb)
          (dels-adds ch)

          (do (when (starts ch) (gct/unread-char rdr ch))
              (if (or (nil? end) (= end ch))
                (str sb)
                (throw (Exception. (format "Mismatched tags: Expected '%s', got '%s'" end ch)))))

          :default
          (do (.append sb ch) (recur)))))))


(defn- parse-diff-inlines [s]
  (let [rdr (gct/indexing-pushback-stringreader s)]
    (loop [res (transient [])]
      (if (nil? (gct/peek-char rdr))
        (persistent! res)
        (let [ch (gct/read-char rdr)
              tagged
              (condp = ch
                ADD_START [:+ (read-tagged rdr ADD_END)]
                DEL_START [:- (read-tagged rdr DEL_END)]
                ;default
                [:= (do (gct/unread-char rdr ch) (read-tagged rdr nil))])]
          (recur (conj! res tagged)))))))


(defn- replace-empty-lines
  "Inserts a placeholding uincode-char into empty lines - to ensure correct diffing."
  [lines]
  (map #(if (= "" %) (str SP) %) lines))


(defn- replace-LTGT
  "Replace all '<' + '>' to avoid having them replaced with '&lt;' + '&gt;' by DiffRowGenerator"
  [s]
  (apply str (map #(condp = % \< LT \> GT %) s)))


(defn split-et-al [s]
  (-> s replace-LTGT split-lines replace-empty-lines))


(defn- generate-diff [a b]
  (let [list-a  (split-et-al a)
        list-b  (split-et-al b)
        generator (-> (DiffRowGenerator/create)
                      (.showInlineDiffs true)
                      (.inlineDiffBySplitter (guj/function (fn [s] (ArrayList. ^List (parse s)))))
                      (.oldTag (guj/function (fn [b] (str (if b DEL_START DEL_END)))))
                      (.newTag (guj/function (fn [b] (str (if b ADD_START ADD_END)))))
                      .build)]
    (-> generator
      (.generateDiffRows ^List list-a ^List list-b)
      (->> (map (fn [^DiffRow r] [(.getOldLine r) (.getNewLine r)]))))))


(defn- nil-empties
  "replaces empty strings with nil"
  [rows]
  (let [nil-it #(if (empty? %) nil %)]
    (loop [rows rows res (transient [])]
      (if-let [[old new] (first rows)]
        (recur (rest rows) (conj! res [(nil-it old) (nil-it new)]))
        (persistent! res)))))


(defn- number-lines
  "puts 'new' and 'old' in separate tuples [nr old-txt] if not nil"
  [rows]
  (loop [rows rows old-nr 1 new-nr 1 res (transient [])]
    (if-let [[old new] (first rows)]
      (let [[old-nrd old-nr] (if old [[old-nr old] (inc old-nr)] [nil old-nr])
            [new-nrd new-nr] (if new [[new-nr new] (inc new-nr)] [nil new-nr])]
        (recur (rest rows) old-nr new-nr (conj! res [old-nrd new-nrd])))
      (persistent! res))))


(defn- combine-numbers
  "pulls out number into separate tuple [old-nr new-nr] and unifies txt where text is the same"
  [rows]
  (loop [rows rows res (transient [])]
    (if-let [row (first rows)]
      (let [[[old-nr old-txt] [new-nr new-txt]] row
            res
            (cond
              (= old-txt new-txt)
              (conj! res [[old-nr new-nr] new-txt])
              (nil? old-nr)
              (conj! res [[nil new-nr] new-txt])
              (nil? new-nr)
              (conj! res [[old-nr nil] old-txt])
              :default
              (-> res (conj! [[old-nr nil] old-txt]) (conj! [[nil new-nr] new-txt])))]
        (recur (rest rows) res))
      (persistent! res))))


(defn- format-numbers [old-nr new-nr]
  (let [format-nr #(if % (format "%1$2d" %) "  ")]
    (format "  %s %s  " (format-nr old-nr) (format-nr new-nr))))

(defn- append-row [ta [[old-nr new-nr] txt]]
  (let [old? (nil? new-nr)
        pieces (parse-diff-inlines txt)]
    (when-not (and old? (= 1 (count pieces)) (-> pieces first first (= :=)))
      (let [i (.getLength ta)
            nr-str (format-numbers old-nr new-nr)
            _ (.appendText ta nr-str)
            i1 (.getLength ta)
            styles (mapv (fn[[t s]] (let [i (.getLength ta)] (.appendText ta s) [(if (and old? (= := t)) :=old t) i (+ i (count s))])) pieces)
            _ (.appendText ta "\n")]
        (.setStyle ta i i1 (CSS (if old? :nr-old :nr)))
        (doseq [[typ start end] styles]
          (.setStyle ta start end (CSS typ)))))))


(defn- append-collapsed [ta]
        (let [i (.getLength ta)
              m (gut/** 50 "≈")]
          (.appendText ta (str m \newline))
          (.setStyle ta i (+ i (count m)) (CSS :collapsed))))


(defn- ->textarea3 [rows]
  (let [ta (new-textarea "\n")]
    (doseq [[[_ _] txt :as row] rows]
      (if (= :collapsed txt)
        (append-collapsed ta)
        (append-row ta row)))
    ta))


(defn diff-patch-textarea [a b & [collapse?]]
  (-> (generate-diff a b)
      ;pprint->
      nil-empties
      ;pprint->
      number-lines
      ;pprint->
      combine-numbers
      (#(if collapse? (collapse-lines %) %))
      ;pprint->
      ->textarea3))


;;;;;;;;;;;;;;


(def a "
This is  a test sentence, I believe.
How are you?

Good.

Very good.
(println :hello)

A

B

C")

(def b "This is a  test for diffutils, I believe...

Are you fine?
Good.
Very very good.

(println
  (+ 2 3))

A
B
C")


(defn- staged [textarea]
  (fx/init) (fx/now (fx/stage :scene (fx/scene (VirtualizedScrollPane. textarea) :size [700 350]) :tofront true)))


;;;;

;(-> (diff-patch-textarea a b) staged)
;(-> (diff-patch-textarea nil "a b c d") staged)
