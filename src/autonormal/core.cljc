(ns autonormal.core
  (:refer-clojure :exclude [ident?]))


(defn ident
  [key id]
  [key id])


(defn ident-of
  [schema entity]
  (let [schema (or schema #(= (name %) "id"))]
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
       (-> (first x)
           (name)
           (= "id"))))


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
   (assert (map? data))
   (prn schema data (entity-map? schema data))
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
