(ns autonormal.core
  (:refer-clojure :exclude [ident?]))


(defn ident
  [entity]
  (loop [kvs entity]
    (when-some [[k v] (first kvs)]
      (if (and (keyword? k)
               (= (name k) "id"))
        [k v]
        (recur (rest kvs))))))


(defn ident?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))
       (-> (first x)
           (name)
           (= "id"))))


(defn entity?
  [x]
  (and (map? x)
       (some? (ident x))))


(defn- replace-all-nested-entities
  [v]
  (cond
    (entity? v)
    (ident v)

    (map? v) ;; map not an entity
    (into (empty v) (map (juxt
                          first
                          (comp replace-all-nested-entities second)))
          v)

    (and (coll? v) (every? entity? v))
    (into (empty v) (map ident) v)

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
        (if (entity? data)
          (conj normalized data)
          normalized)
        (recur
         (first queued)
         (first queued)
         (rest queued)
         (if (entity? data)
           (conj normalized data)
           normalized))))))


#_(normalize {:id "foo"
              :name "Foozle"
              :friends [{:id "bar"
                         :name "Barzle"}
                        {:id "baz"
                         :name "Bazzle"}]})


(defn add
  ([db data]
   (assert (map? data))
   (loop [entities (normalize data)
          db' (if (entity? data)
                db
                ;; capture top-level aliases
                (merge db (replace-all-nested-entities data)))]
     (if-some [entity (first entities)]
       (recur
        (rest entities)
        (update-in db' (ident entity)
                   merge entity))
       db')))
  ([db data & more]
   (reduce add (add db data) more)))


#_(add {}
       {:id "foo"
        :name "Foozle"
        :friends [{:id "bar"
                   :name "Barzle"}
                  {:id "baz"
                   :name "Bazzle"}]}
       {:id "bar"
        :age 5})


#_(add {}
       {:person/id 123
        :person/name "Will"
        :contact {:phone "000-000-0001"}
        :best-friend
        {:person/id 456
         :person/name "Mzuri"
         :account/email "sinuopyro@gmail.com"}
        :friends
        [{:person/id 9001
          :person/name "Georgia"}
         {:person/id 456
          :person/name "Mzuri"}
         {:person/id 789
          :person/name "Frank"}
         {:person/id 1000
          :person/name "Robert"}]})

(defn db
  ([] {})
  ([entities]
   (reduce add {} entities)))


(comment
  (def ppl (db [{:person/id 123
                 :person/name "Will"
                 :contact {:phone "000-000-0001"}
                 :best-friend
                 {:person/id 456
                  :person/name "Mzuri"
                  :account/email "sinuopyro@gmail.com"}
                 :friends
                 [{:person/id 9001
                   :person/name "Georgia"}
                  {:person/id 456
                   :person/name "Mzuri"}
                  {:person/id 789
                   :person/name "Frank"}
                  {:person/id 1000
                   :person/name "Robert"}]}
                {:person/id 456
                 :best-friend {:person/id 123}}]))

  (add ppl {:person/id 123 :person/age 29})

  (-> ppl
      (add {:me {:person/id 123}})
      (add {:people/good [{:person/id 123}
                          {:person/id 456}
                          {:person/id 789}
                          {:person/id 1000}
                          {:person/id 9001}]})
      (add {:person/id 123 :asdf {:jkl {:person/id 666
                                        :person/age 10000}}}))

  #_(add ppl (entity ppl [:person/id 123])))
