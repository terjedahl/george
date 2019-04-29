;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.projects.code
  (:require
    [clojure.string :as cs]
    [clj-diff.core :as diff]
    [george.javafx :as fx]
    [george.code.tokenizer :as gct]
    [george.util.colls :as guc]
    [common.george.util.text :as gut :refer [pformat]])
  (:import
    [org.fxmisc.richtext InlineCssTextArea]))


(defn- codearea [& [s]]
  (doto (if s (InlineCssTextArea. ^String s) (InlineCssTextArea.))
    (.setStyle "-fx-font-size: 18; -fx-font-family: 'Source Code Pro'; -fx-padding: 5;")
    (.setDisable true))) ;; Prevent copying!



(defn- clojure-char? [ch]
  (or (gut/coll-delim-char? ch) (gut/readermacro-char? ch) (gut/macro-char? ch) (gut/comment-char? ch) (gut/string-delim-char? ch)))


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

#_(def c
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



(defn split-lines [s]
  (if (cs/blank? s)
    '()
    (cs/split s #"\r?\n" -1)))


(defn- not-edit? [[_ typ _]]
  (= := typ))


(def collapsed [0 :collapsed ""])


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


(defn diff-patch-textarea [a b & [collapse?]]
  (let [ca (codearea (if collapse? "\n" "\n"))
        ap (split-lines a) ;(parse a)
        bp (split-lines b) ;(parse b)
        ;_ (prn 'AP ap)
        ;_ (prn 'BP bp)
        edits (diff/diff ap bp)
        ;_ (prn 'EDITS edits)
        ;; apply dels
        res (reduce (fn [ap i]
                      (guc/replace-at ap i [[:- (let [s (get ap i)] (if (cs/blank? s) " " s))]]))
                    ap (:- edits))
        ;; apply adds
        res (reduce (fn [res add]
                      (guc/insert-at
                        res
                        (inc ^int (first add)) ;; increment because of how 'insert-at' works.
                        (mapv #(vector :+ (if (cs/blank? %) " " %)) (rest add))))
                    res (reverse (:+ edits)))
        ;; moralize
        res (map #(if (string? %) [:= %] %) res)
        ;; add line numbers
        res (loop [nr 1
                   res0 res
                   res1 []]
              (if-let [[typ txt] (first res0)]
                (recur (if (= :- typ) nr (inc nr))
                       (rest res0)
                       (conj res1 [nr typ txt]))
                res1))
        ;; collapse
        res (if collapse? (collapse-lines res) res)]
    ;(prn 'RES )(mapv prn res)

    (doseq [[nr typ txt] res]
      (if (= typ :collapsed)
        (let [i (.getLength ca)
              mes (gut/** 50 "≈")]
          (.appendText ca (str mes \newline))
          (.setStyle ca i (+ i (count mes))
                     "-fx-fill: #bbb;
                     -rtfx-background-color: #fff;
                     -fx-font-size: 20;"))
        (let [i (.getLength ca)
              nr-str (format "%1$2d" nr)]
          (.appendText ca (str nr-str "  "))
          (.setStyle ca i (+ i (count nr-str))
                     "-fx-fill: gray;
                     -fx-font-size: 16;")
          (let [i (.getLength ca)]
                ;txt (str txt \newline)]
            (.appendText ca (str txt \newline))
            (.setStyle ca i (+ i (count txt))
                       (condp = typ
                         :-
                         "-fx-strikethrough: true;
                         -fx-fill: red;
                         -rtfx-background-color: #ffe0e0;"

                         :+
                         "-fx-fill: blue;
                         -rtfx-background-color: lightblue;"

                         ;; := / default
                         "-rtfx-background-color: white;"))))))
    ca))


(defn- diff-str-test2 []
  (let [a "Ole har en bil.\nDen er veldig blå.\n  Tut tut."
        b "Lars har en fin bil.\nDen er grønn.\n  Tut tut."
        ca (codearea)]
    (diff-patch-textarea ca a b)
    (fx/init) (fx/later (fx/stage :scene (fx/scene ca :size [300 250]) :tofront true))))

;(diff-str-test2)
