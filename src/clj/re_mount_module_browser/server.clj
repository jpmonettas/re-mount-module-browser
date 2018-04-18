(ns re-mount-module-browser.server
  (:require [re-mount-module-browser.handler :refer [handler]]
            [config.core :refer [env]]
            [re-mount-module-browser.core :as core]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

 (defn -main [& args]
   (let [port (Integer/parseInt (or (env :port) "3000"))]
     (core/re-index-all "/home/jmonetta/my-projects/district0x")
     (run-jetty handler {:port port :join? false})))
