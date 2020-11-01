(ns autonormal.core
  (:refer-clojure :exclude [ident?])
  (:require
   [edn-query-language.core :as eql]))


(defn ident
  [key id]
  [key id])


(defn- default-schema
  [key]
  (= (name key) "id"))


(defn ident-of
  [schema entity]
  (let [schema (or schema default-schema)]
    (loop [kvs entity]
      (when-some [[k v] (first kvs)]
        (if (and (keyword? k)
                 (schema k))
          (ident k v)
          (recur (rest kvs)))))))


(defn ident?
  [schema x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))
       (if (some? schema)
         (schema (first x))
         (default-schema (first x)))))


(defn entity-map?
  [schema x]
  (and (map? x)
       (some? (ident-of schema x))))


(defn- replace-all-nested-entities
  [schema v]
  (cond
    (entity-map? schema v)
    (ident-of schema v)

    (map? v) ;; map not an entity
    (into (empty v) (map (juxt
                          first
                          (comp #(replace-all-nested-entities schema %) second)))
          v)

    (and (coll? v) (every? #(entity-map? schema %) v))
    (into (empty v) (map #(ident-of schema %)) v)

    (or (sequential? v) (set? v))
    (into (empty v) (map #(replace-all-nested-entities schema %)) v)

    :else v))


(defn- normalize
  [schema data]
  (loop [kvs data
         data data
         queued []
         normalized []]
    (if-some [[k v] (first kvs)]
      (recur
       ;; move on to the next key
       (rest kvs)
       ;; update our data with idents
       (assoc data k (replace-all-nested-entities schema v))
       ;; add potential entity v to the queue
       (cond
         (map? v)
         (conj queued v)

         (coll? v)
         (apply conj queued v)

         :else queued)
       normalized)
      (if (empty? queued)
        ;; nothing left to do, return all denormalized entities
        (if (entity-map? schema data)
          (conj normalized data)
          normalized)
        (recur
         (first queued)
         (first queued)
         (rest queued)
         (if (entity-map? schema data)
           (conj normalized data)
           normalized))))))


(defn add
  ([{::keys [schema] :as db} data]
   (loop [entities (normalize schema data)
          db' (if (entity-map? schema data)
                db
                ;; capture top-level aliases
                (merge db (replace-all-nested-entities schema data)))]
     (if-some [entity (first entities)]
       (recur
        (rest entities)
        (update-in db' (ident-of schema entity)
                   merge entity))
       db')))
  ([db data & more]
   (reduce add (add db data) more)))


(defn db
  ([] {})
  ([entities]
   (db entities nil))
  ([entities schema]
   (reduce
    add
    (if (some? schema)
      {::schema schema}
      {})
    entities)))


(def not-found ::not-found)


;; TODO recursion
(defn- visit
  [{::keys [schema] :as db} node {:keys [data]}]
  (case (:type node)
    :union
    (into
     {}
     (comp
      (map #(visit db % {:data data})))
     (:children node))

    :union-entry
    (let [union-key (:union-key node)]

      (if (contains? data union-key)
        (into
         {}
         (map #(visit db % {:data data}))
         (:children node))
        nil))

    :prop
    (cond
      (map? data) [(:key node)
                   (let [result (if (ident? schema (:key node))
                                  ;; ident query
                                  (get-in db (:key node) not-found)
                                  (get data (:key node) not-found))]
                     ;; ident result
                     (if (ident? schema result)
                       (get-in db result not-found)
                       result))]

      (coll? data) (into
                    (empty data)
                    (comp
                     (map #(vector (:key node)
                                   (get % (:key node) not-found)))
                     (filter (comp not #{not-found} second)))
                    data))

    :join
    (let [key-result (if (ident? schema (:key node))
                       (get-in db (:key node) not-found)
                       (get data (:key node) not-found))]
      [(:key node)
       (let [data (cond
                    (ident? schema key-result)
                    (get-in db key-result)

                    (and (coll? key-result) (every? #(ident? schema %) key-result))
                    (into
                     (empty key-result)
                     (map #(get-in db %))
                     key-result)

                    :else key-result)]
         (cond
           (map? data) (into
                        {}
                        (comp
                         (map #(visit db % {:data data}))
                         (filter seq)
                         (filter (comp not #{not-found} second)))
                        (:children node))
           (coll? data) (into
                         (empty data)
                         (comp
                          (map (fn [datum]
                                 (into
                                  (empty datum)
                                  (comp
                                   (map #(visit db % {:data datum}))
                                   (filter (comp not #{not-found} second)))
                                  (:children node))))
                          (filter seq))
                         data)
           :else not-found))])))


(defn pull
  [db query]
  (into
   {}
   (map #(visit db % {:data db}))
   (:children (eql/query->ast query))))
