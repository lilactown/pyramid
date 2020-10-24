(ns autonormal.core
  (:refer-clojure :exclude [ident?]))


(defn lookup
  [entity]
  (loop [kvs entity]
    (when-some [[k v] (first kvs)]
      (if (and (keyword? k)
               (= (name k) "id"))
        [k v]
        (recur (rest kvs))))))


(defn lookup?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (-> (first x)
           (name)
           (= "id"))))


(declare entity)


(defn- maybe-lookup
  ([db x]
   (cond
     (lookup? x) (entity db x)

     (and (coll? x) (every? lookup? x))
     (into (empty x) (map #(entity db %)) x)

     :else x)))


;; a lot of this is based off of tonksy/datascript's Entity type
(deftype Entity [db entity-to-be]
  #?@(:clj [Object
            (toString [e] (pr-str entity-to-be))
            (hashCode [e] (hash entity-to-be))
            (equals [e o] (.equals entity-to-be o))

            clojure.lang.Seqable
            (seq [e] (for [[k v] entity-to-be]
                       [k (maybe-lookup db v)]))

            clojure.lang.Associative
            (equiv [e o] (.equiv entity-to-be o))
            (containsKey [e k] (.containsKey entity-to-be k))
            (entryAt [e k] (let [entry (.entryAt entity-to-be k)
                                 v (val entry)]
                             (clojure.lang.MapEntry.
                              k
                              (maybe-lookup db v))))

            (empty [e] (hash-map))
            (assoc [e k v] (Entity. db (assoc entity-to-be k v)))
            (cons [e [k v]] (throw (UnsupportedOperationException.)))
            (count [e] (count entity-to-be))

            clojure.lang.ILookup
            (valAt [e k] (.valAt e k nil))
            (valAt [e k not-found]
                   (maybe-lookup db (.valAt entity-to-be k not-found)))

            clojure.lang.IFn
            (invoke [e k] (maybe-lookup (entity-to-be k)))
            (invoke [e k not-found]
                    (maybe-lookup (entity-to-be k not-found)))]))


(defn entity
  [db ident]
  (->Entity db (get-in db ident)))


(defn entity?
  [x]
  (and (map? x)
       (some? (lookup x))))


(defn- replace-all-nested-entities
  [v]
  (cond
    (entity? v)
    (lookup v)

    (map? v) ;; map not an entity
    (into (empty v) (map (juxt
                          first
                          (comp replace-all-nested-entities second)))
          v)

    (and (coll? v) (every? entity? v))
    (into (empty v) (map lookup) v)

    (or (sequential? v) (set? v))
    (into (empty v) (map replace-all-nested-entities) v)

    :else v))


(defn- denorm
  [data]
  (loop [kvs data
         data data
         queued []
         denormalized []]
    (if-some [[k v] (first kvs)]
      (recur
       ;; move on to the next key
       (rest kvs)
       ;; update our data with lookups
       (assoc data k (replace-all-nested-entities v))
       ;; add potential entity v to the queue
       (cond
         (map? v)
         (conj queued v)

         (coll? v)
         (apply conj queued v)

         :else queued)
       denormalized)
      (if (empty? queued)
        ;; nothing left to do, return all denormalized entities
        (if (entity? data)
          (conj denormalized data)
          denormalized)
        (recur
         (first queued)
         (first queued)
         (rest queued)
         (if (entity? data)
           (conj denormalized data)
           denormalized))))))


#_(denorm {:id "foo"
           :name "Foozle"
           :friends [{:id "bar"
                      :name "Barzle"}
                     {:id "baz"
                      :name "Bazzle"}]})


(defn add
  [db data]
  (assert (map? data))
  (loop [entities (denorm data)
         db' (if (entity? data)
               db
               ;; capture top-level aliases
               (merge db (replace-all-nested-entities data)))]
    (if-some [entity (first entities)]
      (recur
       (rest entities)
       (update-in db' (lookup entity)
                  merge entity))
      db')))


#_(add {}
       {:id "foo"
        :name "Foozle"
        :friends [{:id "bar"
                   :name "Barzle"}
                  {:id "baz"
                   :name "Bazzle"}]})


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


#?(:clj
   (defmethod print-method Entity [e, ^java.io.Writer w]
     (.write w (str e))))


(defn db
  ([] {})
  ([& entities]
   (reduce add {} entities)))


(comment
  (def ppl (db {:person/id 123
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
                :best-friend {:person/id 123}}))

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

  #_(add ppl (entity ppl [:person/id 123]))

  #_(add ppl (update (entity ppl [:person/id 123])
                   :friends
                   conj
                   {:person/id 123456789
                    :person/name "Sarah"}))

  (def will (entity ppl [:person/id 123]))

  ;; cycle
  (get-in will [:best-friend :best-friend :best-friend :best-friend])
  ;; many lookups
  (get will :friends)
  ;; realize all top-level lookups
  (into {} will))
