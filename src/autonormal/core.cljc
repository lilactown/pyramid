(ns autonormal.core
  (:refer-clojure :exclude [ident?]))


(defn ident
  [key id]
  [key id])


(defn ident-of
  [entity]
  (loop [kvs entity]
    (when-some [[k v] (first kvs)]
      (if (and (keyword? k)
               (= (name k) "id"))
        (ident k v)
        (recur (rest kvs))))))


(defn ident?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))
       (-> (first x)
           (name)
           (= "id"))))


(defn entity-map?
  [x]
  (and (map? x)
       (some? (ident-of x))))


(defn- replace-all-nested-entities
  [v]
  (cond
    (entity-map? v)
    (ident-of v)

    (map? v) ;; map not an entity
    (into (empty v) (map (juxt
                          first
                          (comp replace-all-nested-entities second)))
          v)

    (and (coll? v) (every? entity-map? v))
    (into (empty v) (map ident-of) v)

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
       ;; update our data with idents
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
        ;; nothing left to do, return all denormalized entities
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


(defn add
  ([db data]
   (assert (map? data))
   (loop [entities (normalize data)
          db' (if (entity-map? data)
                db
                ;; capture top-level aliases
                (merge db (replace-all-nested-entities data)))]
     (if-some [entity (first entities)]
       (recur
        (rest entities)
        (update-in db' (ident-of entity)
                   merge entity))
       db')))
  ([db data & more]
   (reduce add (add db data) more)))


(defn db
  ([] {})
  ([entities]
   (reduce add {} entities)))
