(ns autonormal.eql
  (:require
   [autonormal.core :as a]
   [edn-query-language.core :as eql]))


(def not-found ::not-found)


;; TODO unions
;; TODO recursion
(defn- db+ast->data
  [db node {:keys [data]}]
  (case (:type node)
    :root (into
           {}
           (comp
            (map #(db+ast->data db % {:data db}))
            ;; remove not-found values
            (filter (comp not #{not-found} second)))
           (:children node))

    :prop
    (cond
      (map? data) [(:key node)
                   (if (a/ident? (:key node))
                     (get-in db (:key node) not-found)
                     (get data (:key node) not-found))]

      (coll? data) (into
                    (empty data)
                    (comp
                     (map #(vector (:key node)
                                   (get % (:key node) not-found)))
                     (filter (comp not #{not-found} second)))
                    data))

    :join
    (let [key-result (if (a/ident? (:key node))
                       (get-in db (:key node) not-found)
                       (get data (:key node) not-found))]
      [(:key node)
       (let [data (cond
                    (a/ident? key-result)
                    (get-in db key-result)

                    (and (coll? key-result) (every? a/ident? key-result))
                    (into
                     (empty key-result)
                     (map #(get-in db %))
                     key-result)

                    :else key-result)]
         (cond
           (map? data) (into
                        {}
                        (comp
                         (map #(db+ast->data db % {:data data}))
                         (filter (comp not #{not-found} second)))
                        (:children node))
           (coll? data) (into
                         (empty data)
                         (map (fn [datum]
                                (into
                                 (empty datum)
                                 (comp
                                  (map #(db+ast->data db % {:data datum}))
                                  (filter (comp not #{not-found} second)))
                                 (:children node))))
                         data)
           :else not-found))])))


(defn pull
  [db query]
  (db+ast->data db (eql/query->ast query) nil))


(comment
  (def db (a/db [{:people/all [{:person/id 0
                                :person/name "Alice"
                                :person/age 17
                                :best-friend {:person/id 1}}
                               {:person/id 1
                                :person/name "Bob"
                                :person/age 23}]}
                 {[:person/id 0] {:person/id 0
                                  :person/favorites
                                  {:favorite/ice-cream "vanilla"}}}]))

  (pull db [{:people/all [:person/name :person/id]}])

  (pull db [#:people{:all [:person/name :person/id {:best-friend [:person/name]}]}])

  (pull db [#:people{:all [:person/name :person/id :best-friend]}])

  (pull db [{[:person/id 0] [:person/id :person/name :person/favorites]}])

  (pull db [{:people/all [:person/name :person/id :best-friend]}
            {[:person/id 1] [:person/age]}])

  (pull db [[:person/id 1]]))
