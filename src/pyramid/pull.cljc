(ns pyramid.pull
  (:require
   [cascade.core :as cc]
   [pyramid.ident :as ident]
   [edn-query-language.core :as eql]))


(defprotocol IPullable
  (resolve-ref [p lookup-ref] [p lookup-ref not-found]
    "Given a ref [:key val], return the entity map it refers to"))


(extend-protocol IPullable
  #?(:clj clojure.lang.IPersistentMap :cljs IMap)
  (resolve-ref
    ([m lookup-ref] (get-in m lookup-ref))
    ([m lookup-ref not-found]
     (get-in m lookup-ref not-found)))

  #?@(:cljs [PersistentArrayMap
             (resolve-ref ([m ref] (get-in m ref))
                          ([m ref nf] (get-in m ref nf)))])
  #?@(:cljs [PersistentHashMap
             (resolve-ref ([m ref] (get-in m ref))
                          ([m ref nf] (get-in m ref nf)))])
  #?@(:cljs [PersistentTreeMap
             (resolve-ref ([m ref] (get-in m ref))
                          ([m ref nf] (get-in m ref nf)))])
  #?@(:cljs [default
             (resolve-ref
              ([o ref]
               (if (map? o)
                 (get-in o ref)
                 (throw (ex-info "no resolve-ref implementation found" {:value o}))))
              ([o ref nf]
               (if (map? o)
                 (get-in o ref nf)
                 (throw (ex-info "no resolve-ref implementation found" {:value o})))))]))


(def not-found ::not-found)


(defn- lookup-ref?
  [x]
  (ident/ident? x))


(defn- node->key
  [node]
  (if-some [params (:params node)]
    (list (:key node) params)
    (:key node)))


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


(defn- visit
  [k db node {:keys [data parent entities]}]
  (case (:type node)
    :union
    (cc/into
     k
     {}
     (comp
      (cc/map (fn [k x]
                #(visit k db x {:data data :entities entities})))
      (cc/filter (cc/cont-with (comp not #{not-found} second))))
     (:children node))

    :union-entry
    (let [union-key (:union-key node)]
      (if (contains? data union-key)
        (cc/into
         k
         {}
         (comp
          (cc/map (fn [k x]
                    #(visit k db x {:data data :entities entities})))
          (cc/filter (cc/cont-with (comp not #{not-found} second))))
         (:children node))
        #(k nil)))

    :prop
    (let [key (node->key node)]
      (cond
        (map? data) #(k [(:key node)
                         (let [result (if (lookup-ref? key)
                                    ;; ident query
                                        (do
                                          (conj! entities key)
                                          (resolve-ref db key not-found))
                                        (get data key not-found))]
                       ;; lookup-ref result
                           (if (lookup-ref? result)
                             (do
                               (conj! entities result)
                               (resolve-ref db result not-found))
                             (replace-all-nested-lookups result)))])

        (coll? data) (cc/into
                      k
                      (empty data)
                      (comp
                       (cc/map (cc/cont-with
                                #(vector key (get % key not-found))))
                       (cc/filter (cc/cont-with (comp not #{not-found} second))))
                      data)
        :else #(k nil)))

    :join
    (let [key (node->key node)
          key-result (if (lookup-ref? key)
                       (do
                         (conj! entities key)
                         (get-in db key not-found))
                       (get data key not-found))
          data (cond
                 (lookup-ref? key-result)
                 (do
                   (conj! entities key-result)
                   (resolve-ref db key-result))

                    ;; not a coll
                 (map? key-result)
                 key-result

                 (and (coll? key-result) (every? lookup-ref? key-result))
                 (do
                   (doseq [lookup-ref key-result]
                     (conj! entities lookup-ref))
                   (into
                    (empty key-result)
                    (map #(resolve-ref db %))
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
        (map? data) (cc/into
                     (comp k #(vector (:key node) %))
                     (with-meta {} (:meta node))
                     (comp
                      (cc/map (fn [k x]
                                (visit k db x {:data data
                                               :parent new-parent
                                               :entities entities})))
                      (cc/filter (cc/cont-with seq))
                      (cc/filter (cc/cont-with (comp not #{not-found} second))))
                     children)
        (coll? data) (cc/into
                      (comp k #(vector (:key node) %))
                      (empty data)
                      (comp
                       (cc/map
                        (fn [k datum]
                          (cc/into
                           k
                           (with-meta (empty datum) (:meta node))
                           (comp
                            (cc/map (fn [k x]
                                      (visit k db x {:data datum
                                                     :parent new-parent
                                                     :entities entities})))
                            (cc/filter (cc/cont-with (comp not #{not-found} second))))
                           children)))
                       (cc/filter (cc/cont-with seq)))
                      data)
        :else #(k nil)))))


(defn pull-report
  "Executes an EQL query against a normalized map `db`. Returns a map with the
  following keys:
    :data - the result of the query
    :entities - a set of lookup refs that were visited during the query"
  [db query]
  (let [root (eql/query->ast query)
        entities (transient #{})]
    (trampoline
     cc/into
     (fn [data]
       {:data data
        :entities (persistent! entities)})
     (with-meta {} (:meta root))
     (comp
      (cc/map (fn [k x]
                (visit k db x {:data db :entities entities})))
      (cc/filter (cc/cont-with (comp not #{not-found} second))))
     (:children root))))
