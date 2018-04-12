(ns re-mount-module-browser.core
  (:require [ike.cljj.file :as files]
            [ike.cljj.stream :as stream]
            [clojure.string :as str]
            [clojure.tools.analyzer :as ann]
            [clojure.tools.reader :as reader]
            [cljs.analyzer :as an]
            [cljs.analyzer.api :as ana]
            [clojure.edn :as edn]
            [cljs.tagged-literals :as tags]
            [cljs.js :as cljs]
            [datascript.core :as d])
  (:import [java.io File]))

(def schema {:project/dependency {:db/valueType :db.type/ref
                                  :db/cardinality :db.cardinality/many}
             :namespace/project {:db/valueType :db.type/ref}
             :event/namespace {:db/valueType :db.type/ref}})

(def db-conn (d/create-conn schema))

(defn cljs-file-forms [f]
 (binding [reader/*alias-map* (-> (File. f) ana/parse-ns :ast :requires)]
   (reader/read-string (str "[" (slurp f) "]"))))

(defn get-all-files [base-dir pred?]
  (with-open [childs (files/walk (files/as-path base-dir))]
    (->> (stream/stream-seq childs)
         (filter pred?)
         (map (fn [p]
                {:absolute-path (str p)
                 :content (files/read-str p)}))
         (into []))))

(defn get-all-projects [base-dir]
  (->> (get-all-files base-dir #(str/ends-with? (str %) "project.clj"))
       (map #(update % :content read-string))
       (map (fn [{:keys [absolute-path content] :as p}]
              (let [[_ project-name project-version & {:keys [dependencies]}] content
                    project-folder (.getParent (File. absolute-path))]
                {:project-folder project-folder
                 :project-name project-name
                 :project-version project-version
                 :dependencies dependencies})))))

(defn all-projects-datoms [all-projects]
  (let [project-ids (->> all-projects
                         (mapcat (fn [{:keys [project-name dependencies]}]
                                   (conj (map (fn [[dname]] dname) dependencies)
                                         (with-meta project-name {:our-project? true}))))
                         (into #{})
                         (map-indexed (fn [i project-name]
                                        [project-name (* -1 i)]))
                         (into {}))
        project-datoms (->> all-projects
                            (mapcat (fn [p]
                                      (let [pid (project-ids (:project-name p))]
                                        [[:db/add pid :project/name (str (:project-name p))]
                                         [:db/add pid :project/folder (:project-folder p)]
                                         [:db/add pid :project/version (:project-version p)]
                                         [:db/add pid :project/ours? true]]))))
        dependency-datoms (->> all-projects
                               (mapcat (fn [p]
                                         (let [pid (project-ids (:project-name p))]
                                           (mapcat (fn [[dp & _]]
                                                  [[:db/add (project-ids dp) :project/name (str dp)]
                                                   [:db/add pid :project/dependency (project-ids dp)]])
                                                (:dependencies p))))))]
    (concat project-datoms dependency-datoms)))


(defn re-index-all [base-dir]
  ;;(d/reset-conn! db-conn (d/empty-db))
  (->> (get-all-projects base-dir)
       all-projects-datoms
       (d/transact! db-conn)))

(defn db-edn []
  (pr-str @db-conn))

(comment
(re-index-all "/home/jmonetta/my-projects/district0x")
  
  (d/reset-conn! db-conn (d/empty-db))

  
  (take 1 (get-all-projects "/home/jmonetta/my-projects/district0x"))
  (.getParent (File. (:absolute-path (first (get-all-projects "/home/jmonetta/my-projects/district0x")))))

  (d/q '[:find ?pid ?pname ?pversion
         :where
         [?pid :project/name ?pname]
         [?pid :project/version ?pversion]]
       @conn)
  (:project/dependency (d/entity @db-conn 15))
  (d/pull @db-conn '[:project/name {:project/dependency 6}] 10)
  (pr-str @conn)

  (d/pull @db-conn '[:project/name :project/dependency] 2)
  )
