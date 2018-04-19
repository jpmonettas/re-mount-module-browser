(ns re-mount-module-browser.server
  (:require [re-mount-module-browser.handler :refer [handler]]
            [config.core :refer [env]]
            [re-mount-module-browser.core :as core]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& [folder]]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (reset! core/projects-folder folder)
    (core/re-index-all)
    (run-jetty handler {:port port :join? false})))
