(ns re-mount-module-browser.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [re-mount-module-browser.core :refer [re-index-all db-edn]]
            [clojure.string :as str]))

(defn file-content [path line]
  (let [content (if line
                  (->> (slurp path)
                       (str/split-lines)
                       (map-indexed (fn [lnumber l]
                                      (if (= lnumber line)
                                        (str "<b id=\"line-tag\" style=\"background-color:yellow\">" l "</b>\n")
                                        (str l "\n"))))
                       (apply str))
                  (slurp path))]
   (str "<p style=\"white-space: pre-wrap\">" content "</p>")))

(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/open-file" [path line :as req]
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (file-content path (Integer/parseInt line))})
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

(def handler (-> #'routes
                 wrap-keyword-params
                 wrap-params))
