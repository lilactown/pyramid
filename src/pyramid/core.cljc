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
   [cascade.hike :as h]
   [pyramid.ident :as ident]
   [pyramid.pull :as pull]
   [pyramid.query :as query]
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
  #?(:bb (first {mk mv})
     :clj (clojure.lang.MapEntry/create mk mv)
     :cljs (cljs.core/MapEntry. mk mv nil)))


;;
;; performance-specific code ahead
;;


#?(:clj
   (defn- fast-assoc
     {:inline
      (fn [m k v]
        (if (symbol? m)
          `(.assoc ~(with-meta m {:tag "clojure.lang.Associative"}) ~k ~v)
          `(let [m# ~m] (fast-assoc m# ~k ~v))))}
     [^clojure.lang.Associative m k v]
     (.assoc m k v)))


(defn- update-ref
  ([m [k ek] f x]
   (let [em (get m k {})
         v (get em ek)]
     #?(:bb (assoc m k
                   (assoc em ek
                          (f v x)))
        :clj (fast-assoc m k
                         (fast-assoc em ek
                                     (f v x)))
        :cljs (assoc m k
                     (assoc em ek
                            (f v x)))))))


(defn- merge-entity
  [e #?(:clj ^clojure.lang.IKVReduce m :cljs ^IKVReduce m)]
  (if (nil? e)
    m
    (if (nil? m)
      e
      #?(:bb (reduce-kv assoc m e)
         :clj (.kvreduce m fast-assoc e)
         :cljs (-kv-reduce m assoc e)))))


(defn add-report*
  "For normal usage, see `pyramid.core/add-report`.

  Takes a normalized map `db` and some new `data`.
  Returns a 0-arity function (thunk) which, when called, will return either
  another thunk or a map containing the keys:
  - :db - the data normalized and merged into `db`
  - :entities - a set of entities found in `data`

  Each thunk should be called to continue the process until the result is
  returned. See `clojure.core/trampoline`."
  [db data]
  (let [identify (:db/ident (meta db) default-ident)
        *db (volatile! db)
        *entities (volatile! (transient #{}))
        process! (fn process! [x]
                   (if (map? x)
                     (if-some [lookup-ref (lookup-ref-of identify x)]
                       (do
                         #_(prn :lookup-ref lookup-ref)
                         (vswap! *db update-ref lookup-ref merge-entity x)
                         #_(prn :*db)
                         (vswap! *entities conj! lookup-ref)
                         #_(prn :*entities)
                         lookup-ref)
                       x)
                     x))]
    #_(prn :add-report*)
    (h/walk
     (fn inner [k x]
       (if (map-entry? x)
         ;; skip processing map keys
         (h/walk
          inner
          (fn outer-map-entry
            [v]
            (k (map-entry
                (key x)
                (process! v))))
          (val x))
         ;; regular c/postwalk
         (h/walk inner (comp k process!) x)))
     ;; we've traversed all other elements, now process the top-level map
     ;; and return the results.
     (fn outer [d]
       #_(prn :outer d)
       d
       (let [data' (process! d)
             em? (entity-map? identify data)]
         #_(prn :done-process!)
         {:entities (persistent! @*entities)
          :indices (if em?
                     #{}
                     (set (keys data')))
          :db (if em?
                @*db
                ;; data isn't an entity map, so we assoc each key in data.
                ;; they act like one-off custom indexes and can be pulled later
                (merge @*db data'))}))
     data)))


(defn add-report
  "Takes a normalized map `db`, and some new `data`.
  Returns a map containing keys:
   :db - the data normalized and merged into `db`.
   :entities - a set of entities found in `data`"
  [db data]
  (trampoline add-report* db data))


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


(defn entities
  "Returns a lazy seq of all entity maps in the DB"
  [db]
  (let [identify (get (meta db) :db/ident default-ident)]
    (->> (vals db) ; skip the first layer of table IDs
         (mapcat #(when (satisfies? query/IQueryable %)
                    (query/entities %)))
         (filter identify))))


(defn db
  "Takes an optional collection of `entities`.

  Returns a new map with the `entities` normalized."
  ([] {})
  ([entities]
   (db entities default-ident))
  ([entities identify]
   (reduce
    add
    (with-meta {} {:db/ident identify
                   `query/entities entities})
    entities)))


(defn identify
  [db data]
  (let [identify (:db/ident (meta db) default-ident)]
    (lookup-ref-of identify data)))


(defn pull-report
  "Executes an EQL query against a normalized map `db`. Returns a map with the
  following keys:
    :data - the result of the query
    :entities - a set of lookup refs that were visited during the query"
  [db query]
  (trampoline pull/pull-report db query))


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
