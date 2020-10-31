(ns autonormal.eql
  (:require
   [autonormal.core :as a]
   [edn-query-language.core :as eql]))


(def not-found ::not-found)


;; TODO recursion
(defn- visit
  [{::a/keys [schema] :as db} node {:keys [data]}]
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
                   (let [result (if (a/ident? schema (:key node))
                                  (get-in db (:key node) not-found)
                                  (get data (:key node) not-found))]
                     (if (a/ident? schema result)
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
    (let [key-result (if (a/ident? schema (:key node))
                       (get-in db (:key node) not-found)
                       (get data (:key node) not-found))]
      [(:key node)
       (let [data (cond
                    (a/ident? schema key-result)
                    (get-in db key-result)

                    (and (coll? key-result) (every? #(a/ident? schema %) key-result))
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
