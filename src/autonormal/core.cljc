(ns autonormal.core
  (:refer-clojure :exclude [ident?])
  (:require
   [clojure.set]
   [edn-query-language.core :as eql]))


(defn- lookup-ref
  [key id]
  [key id])


(defn- default-ident
  [k]
  (and (keyword? k) (= (name k) "id")))


(defn- lookup-ref-of
  [entity]
  (let [entity-ident (if-some [ident-key (get (meta entity) :db/ident)]
                       #(= ident-key %)
                       default-ident)]
    (loop [kvs entity]
      (when-some [[k v] (first kvs)]
        (if (entity-ident k)
          (lookup-ref k v)
          (recur (rest kvs)))))))


(defn- lookup-ref?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))))


(defn- entity-map?
  [x]
  (and (map? x)
       (some? (lookup-ref-of x))))


(defn- replace-all-nested-entities
  [v]
  (cond
    (entity-map? v)
    (lookup-ref-of v)

    (map? v) ;; map not an entity
    (into
     (empty v)
     (map
      (juxt first
            (comp replace-all-nested-entities second)))
     v)

    (and (coll? v) (every? entity-map? v))
    (into (empty v) (map lookup-ref-of) v)

    (or (sequential? v) (set? v))
    (into (empty v) (map replace-all-nested-entities) v)

    :else v))


(defn- normalize
  [data]
  (loop [kvs data
         data data
         queued []
         normalized []]
    (if-some [[k v] (first kvs)]
      (recur
       ;; move on to the next key
       (rest kvs)
       ;; update our data with lookup-refs
       (assoc data k (replace-all-nested-entities v))
       ;; add potential entity v to the queue
       (cond
         (map? v)
         (conj queued v)

         (coll? v)
         (apply conj queued v)

         :else queued)
       normalized)
      (if (empty? queued)
        ;; nothing left to do, return all normalized entities
        (if (entity-map? data)
          (conj normalized data)
          normalized)
        (recur
         (first queued)
         (first queued)
         (rest queued)
         (if (entity-map? data)
           (conj normalized data)
           normalized))))))


(defn add-report
  "Takes a normalized map `db`, and some new `data`.

  Returns a map containing keys:
  - `:db`, the data normalized and merged into `db`.
  - `:entities`, a set of entities found in `data`"
  ([db data]
   (let [initial-entities (normalize data)]
     (loop [entities initial-entities
            db' (if (entity-map? data)
                  db
                  ;; capture top-level aliases
                  (merge db (replace-all-nested-entities data)))]
       (if-some [entity (first entities)]
         (recur
          (rest entities)
          (update-in db' (lookup-ref-of entity)
                     merge entity))
         {:entities (set (map lookup-ref-of initial-entities))
          :db db'})))))


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
   (reduce
    add
    {}
    entities)))


(def not-found ::not-found)


(defn- replace-all-nested-lookups
  "Converts all lookup-refs like [:foo \"bar\"] to maps {:foo \"bar\"}"
  [x]
  (cond
    (map? x)
    (into
     {}
     (map #(vector
            (key %)
            (replace-all-nested-lookups (val %))))
     x)

    (lookup-ref? x)
    (assoc {} (first x) (second x))

    (coll? x)
    (into (empty x) (map replace-all-nested-lookups) x)

    :else x))


(defn- node->key
  [node]
  (if-some [params (:params node)]
    (list (:key node) params)
    (:key node)))


(defn- visit
  [db node {:keys [data parent entities]}]
  (case (:type node)
    :union
    (into
     {}
     (comp
      (map #(visit db % {:data data :entities entities})))
     (:children node))

    :union-entry
    (let [union-key (:union-key node)]

      (if (contains? data union-key)
        (into
         {}
         (map #(visit db % {:data data :entities entities}))
         (:children node))
        nil))

    :prop
    (let [key (node->key node)]
      (cond
        (map? data) [(:key node)
                     (let [result (if (lookup-ref? key)
                                    ;; ident query
                                    (do
                                      (conj! entities key)
                                      (get-in db key not-found))
                                    (get data key not-found))]
                       ;; lookup-ref result
                       (if (lookup-ref? result)
                         (do
                           (conj! entities result)
                           (get-in db result not-found))
                         (replace-all-nested-lookups result)))]

        (coll? data) (into
                      (empty data)
                      (comp
                       (map #(vector key
                                     (get % key not-found)))
                       (filter (comp not #{not-found} second)))
                      data)))

    :join
    (let [key (node->key node)
          key-result (if (lookup-ref? key)
                       (do
                         (conj! entities key)
                         (get-in db key not-found))
                       (get data key not-found))]
      [(:key node)
       (let [data (cond
                    (lookup-ref? key-result)
                    (do
                      (conj! entities key-result)
                      (get-in db key-result))

                    ;; not a coll
                    (map? key-result)
                    key-result

                    (and (coll? key-result) (every? lookup-ref? key-result))
                    (do
                      (doseq [lookup-ref key-result]
                        (conj! entities lookup-ref))
                      (into
                       (empty key-result)
                       (map #(get-in db %))
                       key-result))

                    :else key-result)
             [children new-parent] (cond
                                     (contains? node :children)
                                     [(:children node) node]

                                     ;; inifinite recursion
                                     ;; repeat this query with the new data
                                     (= (:query node) '...)
                                     [(:children parent) parent]

                                     (pos-int? (:query node))
                                     (let [parent (assoc
                                                   parent :children
                                                   (mapv
                                                    (fn [node']
                                                      (if (= key
                                                             (node->key node'))
                                                        (update
                                                         node' :query
                                                         dec)
                                                        node'))
                                                    (:children parent)))]
                                       [(:children parent)
                                        parent]))]
         (cond
           (map? data) (into
                        (with-meta {} (:meta node))
                        (comp
                         (map #(visit db % {:data data
                                            :parent new-parent
                                            :entities entities}))
                         (filter seq)
                         (filter (comp not #{not-found} second)))
                        children)
           (coll? data) (into
                         (empty data)
                         (comp
                          (map
                           (fn [datum]
                             (into
                              (with-meta (empty datum) (:meta node))
                              (comp
                               (map #(visit db % {:data datum
                                                  :parent new-parent
                                                  :entities entities}))
                               (filter (comp not #{not-found} second)))
                              children)))
                          (filter seq))
                         data)
           :else not-found))])))


(defn pull-report
  "Execute an EQL query against a normalized map `db`."
  [db query]
  (let [root (eql/query->ast query)
        entities (transient #{})
        data (into
              (with-meta {} (:meta root))
              (comp
               (map #(visit db % {:data db :entities entities}))
               (filter (comp not #{not-found} second)))
              (:children root))]
    {:data data
     :entities (persistent! entities)}))


(defn pull
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
  [db lookup-ref]
  (delete-nested-entity
   lookup-ref
   (update db (first lookup-ref) dissoc (second lookup-ref))))
