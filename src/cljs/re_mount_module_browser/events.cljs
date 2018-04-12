(ns re-mount-module-browser.events
  (:require [re-frame.core :as re-frame]
            [re-mount-module-browser.db :as db]
            [day8.re-frame.tracing :refer-macros [fn-traced defn-traced]]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [datascript.core :as d]))

(def db-conn (d/create-conn))

(re-frame/reg-fx
 :reset-db
 (fn [new-db]
   (d/reset-conn! db-conn new-db)))

;; (d/pull @db-conn '[:project/name {:project/dependency 6}] 2)
;; (d/pull @db-conn '[:project/name :project/dependency] 1)

(re-frame/reg-cofx
 :dep-tree
 (fn [cofxs pid]
   (let [tree (d/pull @db-conn '[:project/name :project/ours? {:project/dependency 6}] pid)]
     (assoc cofxs :dep-tree tree ))))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
 
(re-frame/reg-event-fx
 ::reload-db
 (fn [cofxs [_]]
   {:http-xhrio {:method          :get
                 :uri             "http://localhost:3000/db"
                 :timeout         8000                                           ;; optional see API docs
                 :response-format (ajax/raw-response-format)  ;; IMPORTANT!: You must provide this.
                 :on-success      [::db-loaded]
                 :on-failure      [:bad-http-result]}}))

(re-frame/reg-event-fx
 ::re-index-all
 (fn [cofxs [_]]
   {:http-xhrio {:method          :get
                 :uri             "http://localhost:3000/re-index-all"
                 :timeout         8000                                           ;; optional see API docs
                 :response-format (ajax/raw-response-format)  ;; IMPORTANT!: You must provide this.
                 :on-success      [::everything-re-indexed]
                 :on-failure      [:bad-http-result]}}))

(re-frame/reg-event-fx
 ::build-dep-tree
 [(re-frame/inject-cofx :dep-tree 10)]
 (fn [{:keys [dep-tree db]} [_]]
   (.log js/console dep-tree)
   {:db (assoc db :dep-tree dep-tree)}))


(re-frame/reg-event-fx
 ::db-loaded
 (fn [_ [_ new-db]]
   {:reset-db (cljs.reader/read-string new-db)
    :dispatch [::build-dep-tree]}))

(re-frame/reg-event-fx
 ::everything-re-indexed
 (fn [_ [_ msg]]
   (.log js/console msg)))


