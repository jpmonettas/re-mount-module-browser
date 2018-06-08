(ns re-mount-module-browser.core
  (:require [cljs.analyzer.api :as ana]
            [cljs.tagged-literals :as tags]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [clojure.zip :as zip]
            [datascript.core :as d]
            [ike.cljj.file :as files]
            [ike.cljj.stream :as stream])
  (:import java.io.File))

(def projects-folder (atom nil))

(def schema {:project/dependency {:db/valueType :db.type/ref
                                  :db/cardinality :db.cardinality/many}
             :namespace/project {:db/valueType :db.type/ref}
             :feature/namespace {:db/valueType :db.type/ref}
             :feature/project {:db/valueType :db.type/ref}
             :spec/namespace {:db/valueType :db.type/ref}
             :spec/project {:db/valueType :db.type/ref}
             :smart-contract/project {:db/valueType :db.type/ref}})

(def db-conn (d/create-conn schema))

(defn cljs-file-data [^File f]
  (try
   (let [{:keys [requires name]} (:ast (ana/parse-ns f))]
     (binding [reader/*data-readers* tags/*cljs-data-readers*
               reader/*alias-map* requires
               *ns* name]
       {:ns-name name
        :absolute-path (.getAbsolutePath f)
        :alias-map requires
        :source-forms (reader/read {} (reader-types/indexing-push-back-reader (str "[" (slurp f) "]")))}))
   (catch Exception e nil)))

(defn get-all-files [base-dir pred?]
  (with-open [childs (files/walk (files/as-path base-dir))]
    (->> (stream/stream-seq childs)
         (filter pred?)
         (mapv (fn [p] (File. (str p)))))))

(defn all-specs [{:keys [source-forms alias-map]}]
  (->> source-forms
       (keep (fn [form]
               (when (seq? form)
                 (let [[f s r & _] form]
                   (when (and (namespace f)
                              (or (= "cljs.spec.alpha" (namespace f))
                                  (= 'cljs.spec.alpha (get alias-map (symbol (namespace f)))))
                              (= (name f) "def"))
                     {:name s
                      :form r
                      :line (-> form meta :line)})))))))

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
                    solidity-files (->> #(str/ends-with? (str %) ".sol")
                                        (get-all-files project-folder)
                                        (map (fn [f] {:absolute-path (.getAbsolutePath f)
                                                      :content (files/read-str f)})))]
                {:project-folder project-folder
                 :project-name project-name
                 :project-version project-version
                 :dependencies dependencies
                 :cljs-files cljs-files
                 :solidity-files solidity-files})))))

(defn transact-projects [conn all-projects]
  (println "--------- Indexing projects and dependencies ---------")
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
  (println "--------- Indexing namespaces ---------")
  (let [db @conn]
   (doseq [{:keys [project-name cljs-files]} all-projects
           {:keys [ns-name absolute-path source-forms]} cljs-files]
     (when ns-name
      (d/transact! conn [[:db/add -1 :namespace/name (str ns-name)]
                         [:db/add -1 :namespace/path absolute-path]
                         [:db/add -1 :namespace/project (:db/id (get-project-by-name db (str project-name)))]])))))

(defn get-namespace-by-name [db namespace-name]
  (d/entity db (d/q '[:find ?nid .
                      :in $ ?nname
                      :where [?nid :namespace/name ?nname]]
                    db
                    namespace-name)))

(defn transact-re-frame-features [conn all-projects]
  (println "--------- Indexing re frame features ---------")
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

(defn transact-specs [conn all-projects]
  (println "--------- Indexing specs ---------")
  (let [db @conn]
   (doseq [{:keys [project-name cljs-files]} all-projects]
     (let [pid (:db/id (get-project-by-name db (str project-name)))]
       (doseq [{:keys [ns-name source-forms] :as cljs-data} cljs-files]
         (let [nid (:db/id (get-namespace-by-name db (str ns-name)))
               specs (all-specs cljs-data)]
           (doseq [{:keys [name form line]} specs]
             (d/transact! conn [[:db/add -1 :spec/namespace nid]
                                [:db/add -1 :spec/project pid]
                                [:db/add -1 :spec/name name]
                                [:db/add -1 :spec/form form]
                                [:db/add -1 :spec/line line]]))))))))

(defn transact-solidity [conn all-projects]
  (println "--------- Indexing solidity contracts ---------")
  (let [db @conn]
    (doseq [{:keys [project-name solidity-files]} all-projects]
     (let [pid (:db/id (get-project-by-name db (str project-name)))]
       (doseq [{:keys [absolute-path]} solidity-files]
         (d/transact! conn [[:db/add -1 :smart-contract/path absolute-path]
                            [:db/add -1 :smart-contract/project pid]]))))))

(defn re-index-all
  ([] (re-index-all nil))
  ([folder]
   (d/reset-conn! db-conn (d/empty-db schema))
   (let [all-projects (get-all-projects (or folder @projects-folder))]
     (transact-projects db-conn all-projects)
     (transact-namespaces db-conn all-projects)
     (transact-re-frame-features db-conn all-projects)
     (transact-solidity db-conn all-projects)
     (transact-specs db-conn all-projects))))

(defn db-edn []
  (pr-str @db-conn))
