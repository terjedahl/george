;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.application.ui.webview
  (:require
    [markdown.core :refer [md-to-html-string]]
    [george.javafx :as fx]
    [clojure.string :as cs]
    [clojure.java 
     [io :as cio]
     [browse :refer [browse-url]]]
    [common.george.util.cli :refer [debug]])
  (:import
    [javafx.scene.web WebView WebEvent]))



(def highlight-css (slurp (cio/resource "styles/highlight.default.css")))
(def highlight-js (slurp (cio/resource "js/highlight.pack.js")))

(def pagef "
<!DOCTYPE html>
<html>
<head>
<meta charset=\"utf-8\">
<style>
%s
</style>
<script>
%s
</script>


<style>
  body {
      padding: 10px;
  }  
  body, a {
    font-family: 'Roboto';
    font-size: 12pt;
    font-weight: regular; 
  }
  strong {
    font-family: 'Roboto';
    font-size: 12pt;
    font-weight: bold;
  }
  em {
    font-family: 'Roboto';
    font-style: italic;
    font-size: 12pt;
    }
  h1, h2, h3, h4 {
    color: grey;
    font-family: 'Source Code Pro Semibold';
    font-weight: bold;
    font-size: 22pt;
    margin-top: 0;
  }
  h2 {
    font-size: 18pt;
    font-family: 'Source Code Pro';  
  }
  h3 {
    font-size: 16pt;
    font-family: 'Source Code Pro Medium';
  }
  h4 {
    font-size: 14pt;
    font-family: 'Source Code Pro';
  }
  hr {
   border: 0;    
   height: 0;
   border-bottom: 1px solid GAINSBORO;
  }
  .clj {
    font-family: 'Source Code Pro Medium';
    font-size: 12pt;
    font-color: #2b292e; /* ANTHRECITE */
    background-color: WHITESMOKE;
    padding-left 3px;
    padding-right:3px;
    margin-left 5px;
    margin-right: 3px;
  }
  /* Hide the scrollbars so they don't cover the code */
  .clj::-webkit-scrollbar {
    display: none;
  }
  a {
    color: #337ab7;
    text-decoration: none;
  }
  a:hover {text-decoration: underline;}
</style>
</head>

<body>

%s

<script>
hljs.initHighlightingOnLoad();
</script>
</body>
</html>
")

(defn html-page [body]
  (format pagef highlight-css highlight-js body))


(defn set-statushandler [webview handler-fn]
  (doto webview
    (-> .getEngine 
        (.setOnStatusChanged 
          (fx/new-eventhandler (handler-fn event))))))


(defn set-content [^WebView webview ^String content]
  (let [page (html-page content)]
    ;(println page)
    (-> webview .getEngine (.loadContent page))))



(defn- click-wrapper
  "Returns a function which can be set as a WebView's onChangeHandler.
  If the data starts with 'CLICK:', 
  then if the rest of the value is a URL, it will open in a browser,
  else if the data converts to a keyword, clickhandler will be called with the keyword as argument."
  [click-handler]
  (fn [^WebEvent e]
    (let [data (.getData e)
          val (when (and data (cs/starts-with? data "CLICK:"))
                    (subs data 6))]
      (when val 
        (if (cs/starts-with? val "http")
            (browse-url val)
            (if (and click-handler) 
              (if (cs/starts-with? val ":")
                  (let [kw (keyword (subs val 1))]
                    (click-handler kw))
                  (if (cs/starts-with? val "var:")
                    (let [sym (symbol (subs val 4))]
                      (click-handler sym))))))))))


(defn ^WebView new-webview [^String content & [click-handler]]
  (let [wv (WebView.)]
    (doto wv
      (set-content content)
      (set-statushandler (click-wrapper click-handler)))))


(defn- code-tag [text state]
  [(cs/replace text #"<code>" "<code class=\"clj\">") state])


(defn- ahref-tag [text state]
  [(cs/replace text #"<a href" "<a <a onclick=\"window.status='CLICK:'+this;return false;\" href") state])


(defn nonspaced-br [text {:keys [code lists] :as state}]
  ;(prn "  ## text:" text)
  [(if (and (not (or code
                     lists
                     (cs/ends-with? text ">")
                     (empty? text))))
     (str text "<br />")
     text)
   state])

(defn doc->html
  "Returns the markdown as an html-string"
  [^String md]
  (let [html (md-to-html-string md :custom-transformers [code-tag ahref-tag nonspaced-br])]
    ;(println html)
    html))