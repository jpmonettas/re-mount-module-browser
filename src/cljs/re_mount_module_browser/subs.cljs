(ns re-mount-module-browser.subs
  (:require [re-frame.core :as re-frame]
            [datascript.core :as d]))

(defn only-ours [tree childs-key ours-fn]
  (update tree childs-key (fn [deps]
                            (->> deps
                                 (filter ours-fn)
                                 (map #(only-ours % childs-key ours-fn))))))

(defn dependency-tree [db selected-project-id]
   (when selected-project-id
     (let [tree (d/pull db '[:project/name :project/ours?  {:project/dependency 6}] selected-project-id)]
       (only-ours tree :project/dependency :project/ours?))))

(defn namespaces-tree [db selected-namespace-id]
  (when selected-namespace-id
    (let [tree (d/pull db '[:namespace/name :namespace/ours? {:namespace/require 100}]
                       selected-namespace-id)]
      (only-ours tree :namespace/require :namespace/ours?))))

(re-frame/reg-sub
 ::projecs-dependencies-edges
 (fn [{:keys [:datascript/db :selected-project-id]} _]
   (->> (dependency-tree db selected-project-id)
        (tree-seq (comp not-empty :project/dependency) :project/dependency )
        (mapcat (fn [{:keys [:project/name :project/dependency]}]
                  (map (fn [d]
                         [name (:project/name d)])
                       dependency))) 
        (into #{}))))

(re-frame/reg-sub
 ::project-namespaces-edges
 (fn [{:keys [:datascript/db :selected-namespace-id]} _]
   (->> (namespaces-tree db selected-namespace-id)
        (tree-seq (comp not-empty :namespace/require) :namespace/require)
        (mapcat (fn [{:keys [:namespace/name :namespace/require]}]
                  (map (fn [d]
                         [name (:namespace/name d)])
                       require))) 
        (into #{}))))

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
 ::all-project-namespaces
 (fn [{:keys [:datascript/db :selected-project-id]} _]
   (if db
     (let [all-project-namespaces (d/q '[:find ?nid ?nname
                                          :in $ ?pid
                                          :where
                                          [?nid :namespace/name ?nname]
                                          [?nid :namespace/project ?pid]]
                                        db
                                        selected-project-id)]
      (->> all-project-namespaces
           (map (fn [[id name]]
                  {:db/id id :namespace/name name}))
           (sort-by :namespace/name)))
    [])))

(re-frame/reg-sub
 ::selected-project-id
 (fn [{:keys [:selected-project-id]} _]
   selected-project-id))

(re-frame/reg-sub
 ::selected-namespace-id
 (fn [{:keys [:selected-namespace-id]} _]
   selected-namespace-id))

(re-frame/reg-sub
 ::selected-tab-id
 (fn [{:keys [:selected-tab-id]} _]
   selected-tab-id))

(re-frame/reg-sub
 ::features
 (fn [{:keys [:datascript/db :selected-project-id]} [_ type]]
   (let [features (d/q '[:find ?fns ?fp ?fname ?fnspath ?fline
                         :in $ ?ftype
                         :where
                         [?fid :re-frame.feature/namespace ?fnsid]
                         [?fid :re-frame.feature/line ?fline]
                         [?fnsid :namespace/name ?fns]
                         [?fnsid :namespace/path ?fnspath]
                         [?fid :re-frame.feature/project ?fpid]
                         [?fpid :project/name ?fp]
                         [?fid :re-frame.feature/name ?fname]
                         [?fid :re-frame.feature/type ?ftype]]
                       db
                       type)]
     (->> features
          (map (fn [[fns fp fname fnspath fline]]
                 {:re-frame.feature/name fname
                  :namespace/path fnspath
                  :re-frame.feature/line fline
                  :namespace/name fns
                  :re-frame.feature/project fp}))
          (group-by :re-frame.feature/project)
          (map (fn [[p features]]
                 [p (group-by :namespace/name features)]))
          (into {})))))

(re-frame/reg-sub
 ::specs
 (fn [{:keys [:datascript/db]} _]
   (let [specs (d/q '[:find ?sns ?sp ?sname ?snspath ?sline ?sform
                      :where
                      [?sid :spec/namespace ?snsid]
                      [?sid :spec/line ?sline]
                      [?sid :spec/project ?spid]
                      [?sid :spec/form ?sform]
                      [?sid :spec/name ?sname]
                      [?snsid :namespace/name ?sns]
                      [?snsid :namespace/path ?snspath]
                      [?spid :project/name ?sp]]
                    db)
         ret (->> specs
                  (map (fn [[sns sp sname snspath sline sform]]
                         {:spec/name sname
                          :namespace/path snspath
                          :spec/line sline
                          :spec/form sform
                          :namespace/name sns
                          :spec/project sp}))
                  (group-by :spec/project)
                  (map (fn [[p spcs]]
                           [p (group-by :namespace/name spcs)]))
                  (into {}))]
     ret)))

(re-frame/reg-sub
 ::smart-contracts
 (fn [{:keys [:datascript/db]} _]
   (let [contracts (d/q '[:find ?pid ?pname ?sid ?spath
                          :where
                          [?pid :project/name ?pname]
                          [?sid :smart-contract/project ?pid]
                          [?sid :smart-contract/path ?spath]]
                        db)]
     (->> contracts
          (map (fn [[pid pname sid spath]]
                 {:project/name pname
                  :smart-contract/path spath}))
          (group-by :project/name))))) 

