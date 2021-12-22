(ns pyramid.core
  "A library for storing graph data in a Clojure map that automatically
  normalizes nested data and allows querying via EQL.

  Create a new db with `db`. db values are normal maps with a tabular structure.

  Add new ones using `add`.

  Entities are identified by the first key with the name \"id\", e.g.
  :person/id. Adding data about the same entity will merge them together in
  order of addition. To replace an entity, `dissoc` it first.

  NOTE: Collections like vectors, sets and lists should not mix entities and
  non-entities. Collections are recursively walked to find entities.

  Query them w/ EQL using `pull`.

  To get meta-information about what entities were added or queried, use the
  `add-report` and `pull-report` functions."
  (:require
   [cascade.walk :as w]
   [pyramid.ident :as ident]
   [pyramid.pull :as pull]
   [clojure.set]
   [edn-query-language.core :as eql]))


(def default-ident
  (ident/by
   (fn ident-by-id [entity]
     (loop [kvs entity]
       (when-some [[k v] (first kvs)]
         (if (and
               (keyword? k)
               (= "id" (name k)))
           (ident/ident k v)
           (recur (rest kvs))))))))


(defn- lookup-ref-of
  [identify entity]
  (let [identify (if-some [ident-key (get (meta entity) :db/ident)]
                   (ident/by-keys ident-key)
                   identify)]
    (identify entity)))


(defn- entity-map?
  [identify x]
  (and (map? x)
       (some? (lookup-ref-of identify x))))


(defn- map-entry
  [mk mv]
  #?(:clj (clojure.lang.MapEntry/create mk mv)
     :cljs (cljs.core/MapEntry. mk mv nil)))


(defn add-report
  [db data]
  (let [identify (:db/ident (meta db) default-ident)
        *db (volatile! db)
        *entities (volatile! (transient #{}))
        process! (fn process! [x]
                   (if (map? x)
                     (if-some [lookup-ref (lookup-ref-of identify x)]
                       (do
                         (vswap! *db update-in lookup-ref merge x)
                         (vswap! *entities conj! lookup-ref)
                         lookup-ref)
                       x)
                     x))
        data' (trampoline
               w/walk
               (fn inner [k x]
                 (if (map-entry? x)
                   ;; skip processing map keys
                   (w/walk
                    inner
                    (fn outer-map-entry
                      [v]
                      (k (map-entry
                          (key x)
                          (process! v))))
                    (val x))
                     ;; regular c/postwalk
                   (w/walk inner (comp k process!) x)))
               process!
               data)]
    {:entities (persistent! @*entities)
     :db (if (entity-map? identify data)
           @*db
           (merge @*db data'))}))


#_(add-report
   {}
   {:foo {:id 1
          '(:foo {:id "bar"}) {:id 2}}
    :bar {:baz [ {:id 3} ]}})


(defn add
  "Takes a normalized map `db`, and some new `data`.

  Returns a new map with the data normalized and merged into `db`."
  ([db data]
   (:db (add-report db data)))
  ([db data & more]
   (reduce add (add db data) more)))


(defn db
  "Takes an optional collection of `entities`.

  Returns a new map with the `entities` normalized."
  ([] {})
  ([entities]
   (db entities default-ident))
  ([entities identify]
   (reduce
    add
    (with-meta {} {:db/ident identify})
    entities)))


(def not-found ::not-found)


(defn pull-report
  "Executes an EQL query against a normalized map `db`. Returns a map with the
  following keys:
    :data - the result of the query
    :entities - a set of lookup refs that were visited during the query"
  [db query]
  (pull/pull-report db query))


(defn pull
  "Executes an EQL query against a normalized map `db` and returns the result."
  [db query]
  (:data (pull-report db query)))


(defn- delete-nested-entity
  [lookup-ref v]
  (cond
    (map? v) (into
              (empty v)
              (comp
               (filter #(not= lookup-ref (val %)))
               (map
                (juxt key #(delete-nested-entity lookup-ref (val %)))))
              v)

    (coll? v) (into
               (empty v)
               (comp
                (filter #(not= lookup-ref %))
                (map #(delete-nested-entity lookup-ref %)))
               v)

    :else v))


(defn delete
  "Deletes an entity from the db, removing all references to it. A lookup-ref is
  a vector of [keyword id], e.g. [:person/id \"a123\"]"
  [db lookup-ref]
  (delete-nested-entity
   lookup-ref
   (update db (first lookup-ref) dissoc (second lookup-ref))))


(defn data->ast
  "Like pyramid.core/data->query, but returns the AST."
  [data]
  (cond
    (map? data) {:type     :root
                 :children (for [[k v] data
                                 :let [node (data->ast v)]]
                             (if node
                               (assoc node
                                 :type :join
                                 :key k
                                 :query (eql/ast->query node)
                                 :dispatch-key (if (coll? k)
                                                 (first k)
                                                 k))
                               {:type         :prop
                                :key          k
                                :dispatch-key k}))}
    ;; pathom uses  `sequential?` here.
    (coll? data) (transduce (map data->ast)
                            (fn
                              ([] {:type :root :children []})
                              ([result] result)
                              ([result el] (eql/merge-asts result el)))
                            data)))

(defn data->query
  "Returns an EQL query that matches the shape of `data` passed to it.
  Useful when you have some data already and want to see what an EQL query that
  returns that data would look like."
  [data]
  (-> data
      data->ast
      eql/ast->query))
