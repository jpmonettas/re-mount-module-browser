(ns re-mount-module-browser.core
  (:require [ike.cljj.file :as files]
            [ike.cljj.stream :as stream]
            [clojure.string :as str]
            [clojure.tools.analyzer :as ann]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
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
             :feature/namespace {:db/valueType :db.type/ref}
             :feature/project {:db/valueType :db.type/ref}
             :smart-contract/project {:db/valueType :db.type/ref}})

(def db-conn (d/create-conn schema))

(defn cljs-file-data [^File f]
  (let [{:keys [requires name]} (:ast (ana/parse-ns f))]
    (binding [reader/*data-readers* tags/*cljs-data-readers*
              reader/*alias-map* requires
              *ns* name]
      {:ns-name name
       :absolute-path (.getAbsolutePath f)
       :alias-map requires
       :source-forms (reader/read {} (reader-types/indexing-push-back-reader (str "[" (slurp f) "]")))})))

(defn get-all-files [base-dir pred?]
  (with-open [childs (files/walk (files/as-path base-dir))]
    (->> (stream/stream-seq childs)
         (filter pred?)
         (mapv (fn [p] (File. (str p)))))))

(defn re-frame-features [cljs-forms]
  (let [form-feature (fn [form]
                       (if (seq? form)
                         (let [[sym arg1] form
                               {:keys [line]} (meta form)]
                           (cond
                             (#{"reg-event-fx" "reg-event-db"} (name sym))
                             {:type :event
                              :name arg1
                              :line line}
                             
                             (= "reg-sub" (name sym))
                             {:type :subscription
                              :name arg1
                              :line line}

                             (= "reg-fx" (name sym))
                             {:type :fx
                              :name arg1
                              :line line}
                             
                             (= "reg-cofx" (name sym))
                             {:type :cofx
                              :name arg1
                              :line line}))))]
    (keep form-feature cljs-forms)))

(defn get-all-projects [base-dir]
  (->> (get-all-files base-dir #(str/ends-with? (str %) "project.clj"))
       (map (fn [f] {:absolute-path (.getAbsolutePath f)
                     :content (read-string (files/read-str f))}))
       (map (fn [{:keys [absolute-path content] :as p}]
              (let [[_ project-name project-version & {:keys [dependencies]}] content
                    project-folder (.getParent (File. absolute-path))
                    cljs-files (->> (get-all-files project-folder #(str/ends-with? (str %) ".cljs"))
                                    (mapv cljs-file-data))
                    solidity-files (->> (get-all-files project-folder #(str/ends-with? (str %) ".sol"))
                                        (map (fn [f] {:absolute-path (.getAbsolutePath f)
                                                      :content (files/read-str f)})))]
                {:project-folder project-folder
                 :project-name project-name
                 :project-version project-version
                 :dependencies dependencies
                 :cljs-files cljs-files
                 :solidity-files solidity-files})))))

(defn transact-projects [conn all-projects]
  (println "--------- Transacting projects and dependencies ---------")
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
    (d/transact! conn (concat project-datoms dependency-datoms))))

(defn get-project-by-name [db project-name]
  (d/entity db (d/q '[:find ?pid .
                      :in $ ?pname
                      :where [?pid :project/name ?pname]]
                    db
                    project-name)))

(defn transact-namespaces [conn all-projects]
  (println "--------- Transacting namespaces ---------")
  (let [db @conn]
   (doseq [{:keys [project-name cljs-files]} all-projects
           {:keys [ns-name absolute-path source-forms]} cljs-files]
     (d/transact! conn [[:db/add -1 :namespace/name (str ns-name)]
                        [:db/add -1 :namespace/path absolute-path]
                        [:db/add -1 :namespace/project (:db/id (get-project-by-name db (str project-name)))]]))))

(defn get-namespace-by-name [db namespace-name]
  (d/entity db (d/q '[:find ?nid .
                      :in $ ?nname
                      :where [?nid :namespace/name ?nname]]
                    db
                    namespace-name)))

#_(transact-namespaces db-conn (get-all-projects "/home/jmonetta/my-projects/district0x"))
#_(:project/name (get-project-by-name @db-conn "district0x/district-time"))

(defn transact-re-frame-features [conn all-projects]
  (println "--------- Transacting re frame features ---------")
  (let [db @conn]
   (doseq [{:keys [project-name cljs-files]} all-projects]
     (let [pid (:db/id (get-project-by-name db (str project-name)))]
       (doseq [{:keys [ns-name source-forms]} cljs-files]
         (let [nid (:db/id (get-namespace-by-name db (str ns-name)))
               rf-features (re-frame-features source-forms)]
           (doseq [{:keys [type name line]} rf-features]
             (d/transact! conn [[:db/add -1 :feature/namespace nid]
                                [:db/add -1 :feature/project pid]
                                [:db/add -1 :feature/name name]
                                [:db/add -1 :feature/line line]
                                [:db/add -1 :feature/type type]]))))))))

(defn transact-solidity [conn all-projects]
  (println "--------- Transacting solidity contracts ---------")
  (let [db @conn]
    (doseq [{:keys [project-name solidity-files]} all-projects]
     (let [pid (:db/id (get-project-by-name db (str project-name)))]
       (doseq [{:keys [absolute-path]} solidity-files]
         (d/transact! conn [[:db/add -1 :smart-contract/path absolute-path]
                            [:db/add -1 :smart-contract/project pid]]))))))

#_(transact-re-frame-features db-conn (get-all-projects "/home/jmonetta/my-projects/district0x"))


(defn re-index-all [base-dir]
  ;;(d/reset-conn! db-conn (d/empty-db))
  (let [all-projects (get-all-projects base-dir)]
    (transact-projects db-conn all-projects)
    (transact-namespaces db-conn all-projects)
    (transact-re-frame-features db-conn all-projects)
    (transact-solidity db-conn all-projects)))

(defn db-edn []
  (pr-str @db-conn))

(comment
  (d/q '[:find ?pid ?pname
         :where
         [?pid :project/name ?pname]]
       @db-conn)
  
  (d/pull @db-conn '[:project/name
                     #_{:namespace/_project [:namespace/name]}
                     #_{:feature/_project [:feature/name :feature/type]}
                     {:smart-contract/_project [:smart-contract/path]}
                     #_{:project/dependency 6}]
          10)

  (d/q '[:find ?pid ?sid ?sname
         :where
         [?sid :smart-contract/project ?pid]
         [?sid :smart-contract/path ?sname]
         ]
       @db-conn)
  
(re-index-all "/home/jmonetta/my-projects/district0x")

  (d/reset-conn! db-conn (d/empty-db))

  (def all-projects (get-all-projects "/home/jmonetta/my-projects/district0x"))
  (keys (first all-projects))
  (->> (get-all-projects "/home/jmonetta/my-projects/district0x")
       (map :solidity-files)
       (mapcat (fn [sf] (map :absolute-path sf))))
  (.getParent (File. (:absolute-path (first (get-all-projects "/home/jmonetta/my-projects/district0x")))))

  (d/q '[:find ?pid ?pname ?pversion
         :where
         [?pid :project/name ?pname]
         [?pid :project/version ?pversion]]
       @conn)
  
  (-> (cljs-file-data (File. "/home/jmonetta/my-projects/district0x/name-bazaar/src/name_bazaar/ui/events.cljs"))
    :source-forms
    (nth 4)
    meta
    )
  )
