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

(re-frame/reg-sub
 ::selected-tab-id
 (fn [{:keys [:selected-tab-id]} _]
   selected-tab-id))

(re-frame/reg-sub
 ::features
 (fn [{:keys [:datascript/db :selected-project-id]} [_ type]]
   (let [features (d/q '[:find ?fns ?fp ?fname
                         :in $ ?ftype
                         :where
                         [?fid :feature/namespace ?fnsid]
                         [?fnsid :namespace/name ?fns]
                         [?fid :feature/project ?fpid]
                         [?fpid :project/name ?fp]
                         [?fid :feature/name ?fname]
                         [?fid :feature/type ?ftype]]
                       db
                       type)]
     (->> features
          (map (fn [[fns fp fname]]
                 {:feature/name fname
                  :feature/namespace fns
                  :feature/project fp}))
          (group-by :feature/project)
          (map (fn [[p features]]
                 [p (group-by :feature/namespace features)]))
          (into {}))))) 

