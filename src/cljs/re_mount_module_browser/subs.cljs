(ns re-mount-module-browser.subs
  (:require [re-frame.core :as re-frame]
            [datascript.core :as d]))

(defn only-ours [tree]
  (update tree :project/dependency (fn [deps]
                                     (->> deps
                                          (filter :project/ours?)
                                          (map only-ours)))))

(re-frame/reg-sub
 ::dependency-tree
 (fn [{:keys [:datascript/db :selected-project-id]} _]
   (when selected-project-id
     (let [tree (d/pull db '[:project/name :project/ours?  {:project/dependency 6}] selected-project-id)]
      (only-ours tree)))))

(re-frame/reg-sub
 ::all-projects
 (fn [{:keys [:datascript/db]} _]
   (if db
    (let [all-projects (d/q '[:find ?pid ?pname
                              :where
                              [?pid :project/name ?pname]
                              [?pid :project/ours? true]]
                            db)]
      (->> all-projects
           (map (fn [[id name]]
                  {:db/id id :project/name name}))
           (sort-by :project/name)))
    [])))

(re-frame/reg-sub
 ::selected-project-id
 (fn [{:keys [:selected-project-id]} _]
   selected-project-id)) 

