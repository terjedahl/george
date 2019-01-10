;; Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns leiningen.serve
  "Start a web server in the current or specified directory, on specified port or 8080"
  (:require
    [clojure.pprint :refer [pprint]]
    [leiningen.george.core :as g])
  (:import
    [org.eclipse.jetty.server Handler Server]
    [org.eclipse.jetty.server.handler DefaultHandler HandlerList ResourceHandler AbstractHandler]
    [java.net BindException]))


(defn- static-file-handler [base-dir]
  (doto (ResourceHandler.)
        (.setDirectoriesListed true)
        (.setWelcomeFiles (into-array ["index.html"]))
        (.setResourceBase base-dir)))
        ;(.setResourceBase ".")))


(defn- info [dir port]
  {:url (format "http://localhost:%s/" port) :dir dir})


(defn- command-handler [port dir]
  (proxy [AbstractHandler] []
    (handle [target base-request request response]
      ;(prn 'handle target base-request request)
      (when  (.startsWith target "/_cmd")
          ;(println "GOT _cmd:" target)
          (.setContentType response "text/plain")
          (.setHandled base-request true)
          (with-open [out (.getOutputStream response)]
            (spit out
                  (case target
                        "/_cmd/info"
                        (str (info dir port))

                        "/_cmd/url"
                        (format "http://localhost:%s/" port)

                        "/_cmd/port"
                        (str port)

                        "/_cmd/dir"
                        (str dir)

                        "/_cmd/stop"   
                        (let [m "Server stopped."]
                          (future (Thread/sleep 1000) (System/exit 0))
                          (println m)
                          m)
                        
                        ;; else
                        (let [m (format "Unknown command: %s" target)]
                          (binding [*out* *err*] (println m))
                          m))))))))


(defn- default-handler []
  (doto (DefaultHandler.)
        (.setServeIcon false)))


(defn- create-server [port handlers]
  (let [handler-list 
        (doto (HandlerList.)
              (.setHandlers (into-array Handler handlers)))]
    (doto (Server. port) 
          (.setHandler handler-list))))


(def server_ (atom nil))


(defn- run-server
  "Starts the given Jetty server and waits for it to exit."
  []
  (.start @server_)
  (.join @server_))



(defn ^:no-project-needed serve
  "Start a web server in the current directory.

USAGE: lein server [:port port][:dir base-dir]
Starts a web server in the specified directory (defaults to current), listening on the given port (defaults to 8080)."
  [project & {:strs [:port :dir]}]
  
  (let [port 
        (Integer/parseInt  
          (str (or port (-> project :server :port) 8080)))
        dir
        (or dir (-> project :server :dir) ".")
        handlers 
        [(command-handler port dir)
         (static-file-handler dir)
         (default-handler)]
    
        server 
        (create-server port handlers)]
    (reset! server_ server)    
    (println "Starting server ...")
    (pprint (info dir port))
    (try
      (run-server)
      (catch BindException _
        (g/error "Error: Start failed. Port already in use.")))))
