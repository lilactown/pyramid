(ns autonormal.entity
  (:require
   [autonormal.core :as a]))


(declare entity)


(defn- maybe-lookup
  ([db x]
   (cond
     (a/ident? x) (entity db x)

     (and (coll? x) (every? a/ident? x))
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


#?(:clj
   (defmethod print-method Entity [e, ^java.io.Writer w]
     (.write w (str e))))


(comment
  (def ppl (a/db [{:person/id 123
                   :person/name "Will"
                   :best-friend {:person/id "asdf"}
                   :friends [{:person/id "asdf"}
                             {:person/id 456}
                             {:person/id 789}]}
                  {:person/id "asdf"
                   :person/name "Andrea"
                   :best-friend {:person/id 123}}]))

  (def will (entity ppl [:person/id 123]))

  ;; cycle
  (get-in will [:best-friend :best-friend :best-friend :best-friend])
  ;; many idents
  (get will :friends)
  ;; realize all top-level idents
  (into {} will))
