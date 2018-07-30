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
             :namespace/require {:db/valueType :db.type/ref
                                 :db/cardinality :db.cardinality/many}
             :re-frame.feature/namespace {:db/valueType :db.type/ref}
             :re-frame.feature/project {:db/valueType :db.type/ref}
             :mount.feature/namespace {:db/valueType :db.type/ref}
             :mount.feature/project {:db/valueType :db.type/ref}
             :spec/namespace {:db/valueType :db.type/ref}
             :spec/project {:db/valueType :db.type/ref}
             :smart-contract/project {:db/valueType :db.type/ref}})

(def db-conn (d/create-conn schema))

(defn cljs-file-data [^File f]
  (try
   (let [{:keys [requires name] :as ast} (:ast (ana/parse-ns f))]
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
                   (when (and (symbol? f)
                              (namespace f)
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
                           (when (symbol? sym)
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
                               :line line})))))]
    (keep form-feature cljs-forms)))

(defn mount-features [cljs-forms]
  (let [form-feature (fn [form]
                       (if (seq? form)
                         (let [[sym arg1 arg2] form
                               {:keys [line]} (meta form)]
                           (when (symbol? sym)
                            (cond
                              (#{"defstate"} (name sym))
                              {:type :defstate
                               :name (str arg1)
                               :line line})))))]
    (keep form-feature cljs-forms)))


(defn get-all-projects [base-dir]
  (->> (get-all-files base-dir #(str/ends-with? (str %) "project.clj"))
       (map (fn [f] {:absolute-path (.getAbsolutePath f)
                     :content (read-string (files/read-str f))}))
       (map (fn [{:keys [absolute-path content] :as p}]
              (let [[_ project-name project-version & {:keys [dependencies]}] content
                    project-folder (.getParent (File. absolute-path))
                    cljs-files (->> (get-all-files project-folder #(re-find  #".*/src/.+cljs$" (str %)) #_(str/ends-with? (str %) ".cljs"))
                                    (mapv cljs-file-data))]
                {:project-folder project-folder
                 :project-name project-name
                 :project-version project-version
                 :dependencies dependencies
                 :cljs-files cljs-files})))))

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
  (let [db @conn
        requires (fn [alias-map] (->> alias-map vals (into #{})))]
    (doseq [{:keys [project-name cljs-files]} all-projects]
      ;; this first pass is to generate all the ns ids for names before starting so we
      ;; don't depend on ns order processing for creating dependecies
      (let [all-namespaces (->> cljs-files
                                (mapcat (fn [{:keys [ns-name alias-map]}]
                                          (into [ns-name] (requires alias-map))))
                                (into #{}))
            ns-ids-by-name (->> all-namespaces
                                (reduce (fn [r n]
                                          (assoc r n (d/tempid :db.part/user)))
                                        {}))
            all-ns-facts (->> cljs-files
                              (mapcat (fn [{:keys [ns-name absolute-path source-forms alias-map]}]
                                        (when ns-name
                                         (let [required-facts (mapcat (fn [required-ns]
                                                                        [[:db/add (ns-ids-by-name ns-name) :namespace/require (ns-ids-by-name required-ns)]
                                                                         [:db/add (ns-ids-by-name required-ns) :namespace/name (str required-ns)]])
                                                                      (requires alias-map))
                                               ns-facts (into [[:db/add (ns-ids-by-name ns-name) :namespace/name (str ns-name)]
                                                               [:db/add (ns-ids-by-name ns-name) :namespace/path absolute-path]
                                                               [:db/add (ns-ids-by-name ns-name) :namespace/project (:db/id (get-project-by-name db (str project-name)))]
                                                               [:db/add (ns-ids-by-name ns-name) :namespace/ours? true]]
                                                              required-facts)]
                                           ns-facts)))))]
        (d/transact! conn all-ns-facts)))))

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
             (prn "Transactin features facts " [[:db/add -1 :re-frame.feature/namespace nid]
                                                [:db/add -1 :re-frame.feature/project pid]
                                                [:db/add -1 :re-frame.feature/name name]
                                                [:db/add -1 :re-frame.feature/line line]
                                                [:db/add -1 :re-frame.feature/type type]])
             (d/transact! conn [[:db/add -1 :re-frame.feature/namespace nid]
                                [:db/add -1 :re-frame.feature/project pid]
                                [:db/add -1 :re-frame.feature/name name]
                                [:db/add -1 :re-frame.feature/line line]
                                [:db/add -1 :re-frame.feature/type type]]))))))))

(defn transact-mount-features [conn all-projects]
  (println "--------- Indexing mount features ---------")
  (let [db @conn]
   (doseq [{:keys [project-name cljs-files]} all-projects]
     (let [pid (:db/id (get-project-by-name db (str project-name)))]
       (doseq [{:keys [ns-name alias-map source-forms]} cljs-files]
         (let [nid (:db/id (get-namespace-by-name db (str ns-name)))
               features (mount-features source-forms)]
           (doseq [{:keys [type name line]} features]
             (d/transact! conn [[:db/add -1 :mount.feature/namespace nid]
                                [:db/add -1 :mount.feature/project pid]
                                [:db/add -1 :mount.feature/name name]
                                [:db/add -1 :mount.feature/line line]
                                [:db/add -1 :mount.feature/type type]]))))))))

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

(defn re-index-all
  ([] (re-index-all nil))
  ([folder]
   (d/reset-conn! db-conn (d/empty-db schema))
   (let [all-projects (get-all-projects (or folder @projects-folder))]
     (transact-projects db-conn all-projects)
     (transact-namespaces db-conn all-projects)
     (transact-re-frame-features db-conn all-projects)
     (transact-mount-features db-conn all-projects)
     (transact-specs db-conn all-projects))))

(defn db-edn []
  (pr-str @db-conn))
