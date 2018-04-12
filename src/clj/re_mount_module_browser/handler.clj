(ns re-mount-module-browser.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [re-mount-module-browser.core :refer [re-index-all db-edn]]))

(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/db" []
       {:status 200
        :headers {"Content-Type" "application/edn"}
        :body (db-edn)})
  (GET "/re-index-all" []
       (let [folder "/home/jmonetta/my-projects/district0x"]
         (re-index-all folder)
         {:status 200
          :body (str folder " re indexed.")}))
  (resources "/"))

(def dev-handler (-> #'routes wrap-reload))

(def handler routes)
