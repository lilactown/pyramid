(ns pyramid.query
  "Experimental!

  A datalog query engine for normalized maps in the pyramid.core/db fashion"
  (:require
   [clojure.string :as string]))

;; TODO
;;  * wildcards
;;  * boolean logic / filters
;;  * pull in :find
;;  * single result in :find


(def ? '?)
(def $ '$)
(def _ '_)


(defn parse
  [query db & params]
  (let [[bindings clauses] (split-with #(not= % :where) query)
        find (->> bindings
                    (take-while #(not= % :in))
                    (drop 1))
        inputs (->> bindings
                    (drop-while #(not= % :in))
                    ;; users have to pass in $
                    (drop 2)
                    (cons $))
        clauses (drop 1 clauses)
        anomalies (cond-> []
                    (not= (count inputs) (inc (count params)))
                    (conj {:query/anomaly
                           "Inputs in query do not match those passed in"})

                    (empty? clauses)
                    (conj {:query/anomaly
                           "No :where clauses given"}))]
    {:find find
     :in (zipmap inputs (cons db params))
     :where clauses
     :anomalies anomalies}))


(comment
  ;; simple
  (parse '[:find ?id ?value
           :where
           [?e :foo/id ?id]
           [?e :foo/value ?value]]
         {:foo/id {"1234" {:foo/id "1234"
                           :foo/value "asdf"}}})

  (parse '[:find ?id ?value
           :where
           [?e :foo/id ?id]
           [?e :foo/value]]
         {:foo/id {"1234" {:foo/id "1234"
                           :foo/value "asdf"}}})


  ;; invalid
  (parse '[:find ?id ?value
           :where
           [:foo/id ?id]]
         {:foo/id {"1234" {:foo/id "1234"
                           :foo/value "asdf"}}})

  ;; inputs
  (parse '[:find ?id ?value
           :in $ ?a ?b
           :where
           [?e :foo/id ?id]
           [?e :foo/value ?value]]
         {:foo/id {"1234" {:foo/id "1234"
                           :foo/value "asdf"}}}
         "a"
         "b")

  ;; anomaly, mismatch inputs
  (parse '[:find ?id ?value
           :in $ ?a ?b
           :where
           [?e :foo/id ?id]
           [?e :foo/value ?value]]
         {:foo/id {"1234" {:foo/id "1234"
                           :foo/value "asdf"}}})
  )


(defn variable?
  [x]
  (and (symbol? x) (string/starts-with? (name x) "?")))


(defn pattern
  [x]
  (if (variable? x) ? :v))


(defprotocol IQueryable
  :extend-via-metadata true
  (entities [o] "Returns a seq of all entities which can be queried."))


(defn- map-entities
  [m]
  (->> m
       (tree-seq coll? #(if (map? %) (vals %) (seq %)))
       (filter map?)))


(defn- coll-entities
  [c]
  (mapcat #(when (satisfies? IQueryable %) (entities %)) c))


(extend-protocol IQueryable
  #?(:clj clojure.lang.IPersistentMap :cljs IMap)
  (entities [m] (map-entities m))

  #?(:clj clojure.lang.IPersistentCollection :cljs ICollection)
  (entities [c] (coll-entities c))

  #?@(:cljs [default
             (entities
              [o]
              (cond
                (satisfies? IMap o) (map-entities o)
                (satisfies? ICollection o) (coll-entities o)
                :else nil))]))


(defn- contains-in?
  [m ks]
  (let [path (drop-last ks)
        k (last ks)]
    (contains? (get-in m path) k)))


(defn- resolve-entity
  ([db e]
   (if (map? e)
     e
     (get-in db e)))
  ([db e a] (resolve-entity db e a nil))
  ([db e a nf]
   (if (map? e)
     (get e a nf)
     (get-in db (conj e a) nf))))


(defn- entity-exists?
  ([db e]
   (or (map? e) (contains-in? db e)))
  ([db e a]
   (if (map? e)
     (contains? e a)
     (contains-in? db (conj e a)))))


(defn- resolve-triple
  [db triple]
  (let [[e a v] triple
        entities (entities db)]
    #_(prn triple (map pattern triple))
    (case (map pattern triple)
      [:v :v :v]
      (if (= v (resolve-entity db e a))
        [[]]
        [])

      [? :v :v]
      (into
       []
       (comp
        (filter #(= v (resolve-entity db % a ::not-found)))
        (map vector))
       entities)

      [:v ? :v]
      (into
       []
       (comp
        (filter #(= v (val %)))
        (map key))
       (resolve-entity db e))

      [:v :v ?]
      (if (entity-exists? db e a)
        (if (:many (meta v))
          (map vector (resolve-entity db e a))
          [[(resolve-entity db e a)]])
        [])

      [? ? :v]
      (mapcat
       (fn [entity]
         (->> (resolve-entity db entity)
              (filter #(= v (val %)))
              (map (fn [entry]
                     [entity (key entry)]))))
       entities)

      [? :v ?]
      (into
       []
       (comp
        (filter #(entity-exists? db % a))
        (if (:many (meta v))
          (mapcat
           (fn [entity]
             (map
              (fn [value] [entity value])
              (resolve-entity db entity a))))
          (map
           (fn [entity]
             [entity (resolve-entity db entity a)]))))
       entities)

      [:v ? ?]
      (if (:many (meta v))
        (into
         []
         (mapcat
          (fn [entry]
            (let [k (key entry)]
              (map #(vector k %) (val entry))))
          (resolve-entity db e)))
        (mapv
         (fn [entry]
           [(key entry) (val entry)])
         (resolve-entity db e)))

      [? ? ?]
      (if (:many (meta v))
        (for [entity-or-ident entities
              :let [entity (resolve-entity db entity-or-ident)]
              entry entity
              values (val entry)]
          [entity-or-ident (key entry) values])
        (for [entity-or-ident entities
              :let [entity (resolve-entity db entity-or-ident)]
              entry entity]
          [entity-or-ident (key entry) (val entry)])))))


(comment
 (def db {:foo/id {"123" {:foo/id "123"
                          :foo/bar "baz"}
                   "456" {:foo/id "456"
                          :foo/bar "asdf"}}
          :foo {:bar "baz"}
          :asdf "jkl"})

 ;; [:v :v :v] found
 (resolve-triple db [[:foo/id "123"] :foo/bar "baz"])

 ;; [:v :v :v] not-found
 (resolve-triple db ['[:foo/id "456"] :foo/bar "bar"])

 (resolve-triple db ['[:foo/id "123"] :foo/bat "bar"])

 (resolve-triple db ['[:foo/id "123"] :foo/bar "bat"])

 (resolve-triple db ['[:foo/id "asdf"] :foo/bar "bar"])


 ;; [? :v :v] found
 (resolve-triple db '[?e :foo/bar "baz"])

 (resolve-triple db (with-meta '[?e :foo/bar "baz"]
                      {:original '[?e :foo/bar ?bar]}))

 ;; [? :v :v] not-found
 (resolve-triple db '[?e :foo/bar "bat"])

 (resolve-triple db '[?e :foo/bat "baz"])


 ;; [:v ? :v] found
 (resolve-triple db '[[:foo/id "123"] ?a "baz"])

 ;; [:v ? :v] not-found
 (resolve-triple db '[[:foo/id "123"] ?a "bat"])

 (resolve-triple db '[[:foo/id "456"] ?a "bar"])

 (resolve-triple db '[[:foo/id "asdf"] ?a "bar"])


 ;; [:v :v ?] found
 (resolve-triple db '[[:foo/id "123"] :foo/bar ?v])

 ;; [:v :v ?] not-found
 (resolve-triple db '[[:foo/id "asdf"] :foo/bar ?v])

 (resolve-triple db '[[:foo/id "123"] :foo/bat ?v])


 ;; [? ? :v] found
 (resolve-triple db '[?e ?a "baz"])

 ;; [? ? :v] not-found
 (resolve-triple db '[?e ?a "bat"])


 ;; [? :v ?] found
 (resolve-triple db '[?e :foo/id ?id])

 (resolve-triple db '[?e :foo/bar ?id])

 ;; [? :v ?] not-found
 (resolve-triple db '[?e :foo/bat ?bat])


 ;; [:v ? ?] found
 (resolve-triple db '[[:foo/id "123"] ?a ?v])

 ;; [:v ? ?] not-found
 (resolve-triple db '[[:foo/id "asdf"] ?a ?v])


 ;; [? ? ?]
 (resolve-triple db '[?e ?a ?v])
 )



(defn- rewrite-and-resolve-triple
  [db results [e a v :as triple]]
  ;; TODO for some reason this is nesting too many collections
  (for [left results
        :let [pattern (:pattern (meta left))
              ;; get mapping of var to result index
              var->idx (into
                        {}
                        (comp
                         (map-indexed #(vector %2 %1))
                         (filter #(variable? (first %))))
                        pattern)
              [ei ai vi] (map var->idx triple)
              triple' [(if ei (nth left ei) e)
                       (if ai (nth left ai) a)
                       (if vi (nth left vi) v)]
              pattern' (->> triple'
                            (remove (complement variable?))
                            (remove (set pattern))
                            (concat pattern)
                            (vec))]
        right (resolve-triple db triple')]
    (with-meta
      (concat left right)
      {:pattern pattern'})))


(comment
  (rewrite-and-resolve-triple
   {:foo/id {1 {:foo/id 1
                :foo/name "bar"}
             2 {:foo/id 2
                :foo/name "baz"}}}
   [(with-meta [[:foo/id 1] 1]
      {:pattern '[?e ?id]})
    (with-meta [[:foo/id 2] 2]
      {:pattern '[?e ?id]})]
   '[?e :foo/name ?name])

  (map meta
       (rewrite-and-resolve-triple
        {:foo/id {1 {:foo/id 1
                     :foo/name "bar"}
                  2 {:foo/id 2
                     :foo/name "baz"}}}
        [(with-meta [[:foo/id 1] 1]
           {:pattern '[?e ?id]})]
        '[?e :foo/name ?name]))

  (rewrite-and-resolve-triple
   {:foo/id {"123" #:foo{:id "123", :bar "baz"}
             "456" #:foo{:id "456", :bar "asdf"}
             "789" #:foo{:id "456"}}
    :foo {:bar "baz"}, :asdf "jkl"}
   nil
   '[?e :foo/id ?id])

  (rewrite-and-resolve-triple
   {:foo/id {"123" #:foo{:id "123", :bar "baz"}
             "456" #:foo{:id "456", :bar "asdf"}
             "789" #:foo{:id "456"}}, :foo {:bar "baz"}, :asdf "jkl"}
   [(with-meta [[:foo/id "123"] "123"]
      '{:pattern (?e ?id)})
    (with-meta [[:foo/id "456"] "456"]
      '{:pattern (?e ?id)})
    (with-meta [[:foo/id "789"] "456"]
      '{:pattern (?e ?id)})]
   '[?e :foo/bar ?bar]))


(defn- project-results
  [results bindings]
  (for [result results
        :let [pattern (:pattern (meta result))
              var->idx (into
                        {}
                        (comp
                         (map-indexed #(vector %2 %1))
                         (filter #(variable? (first %))))
                        pattern)
              indices (->> bindings
                           (map var->idx))]
        :when (some some? indices)]
    (mapv #(when (some? %) (nth result %)) indices)))


(defn in->results
  [in]
  #_(prn in)
  ;; convert map {?var value|[value]} to ([?value] [?value]) with pattern meta
  (let [expanded-in (for [[k v] in]
                      (map
                       #(with-meta [%] {:pattern [k]})
                       (if (:many (meta k))
                         v
                         [v])))]
    (loop [results [[]]
           in expanded-in]
      (if-let [hd (first in)]
        (recur
         (for [left results
               right hd]
           (with-meta
             (concat left right)
             {:pattern (concat (:pattern (meta left))
                               (:pattern (meta right)))}))
         (rest in)) ;; peace
        results))))


(comment
  (in->results '{$ {} ?foo "bar"})

  (in->results '{$ {} ^:many ?foo ["bar" "baz"] ?asdf [1 2 3]})

  (in->results '{$ {} ^:many ?foo ["bar" "baz"] ^:many ?asdf [1 2 3]})
  )


(defn execute
  [{:keys [find in where]}]
  (let [db (get in $)]
    (loop [clauses where
           results (in->results in)]
      ;; (prn (first results))
      ;; (prn (meta (first results)))
      (if-let [clause (first clauses)]
        (recur
         (rest clauses)
         (rewrite-and-resolve-triple db results clause))
        (do
          #_(prn (meta (first results)))
          #_(clojure.pprint/pprint results)
          (project-results results find))))))


(comment
  ;; found
  (-> (parse
       '[:find ?id ?bar
         :where
         [?e :foo/id ?id]
         [?e :foo/bar ?bar]]
       {:foo/id {"123" {:foo/id "123"
                        :foo/bar "baz"}
                 "456" {:foo/id "456"
                        :foo/bar "asdf"}
                 "789" {:foo/id "456"}}
        :foo {:bar "baz"}
        :asdf "jkl"})
      (execute)
      #_(->> (map meta)))

  (-> (parse
       '[:find ?id
         :in $
         :where
         [?e :foo/id ?id]
         [?e :foo/bar "baz"]]
       {:foo/id {"123" {:foo/id "123"
                        :foo/bar "baz"}
                 "456" {:foo/id "456"
                        :foo/bar "asdf"}}
        :foo {:bar "baz"}
        :asdf "jkl"})
      (execute)
      #_(->> (map meta)))

  (-> (parse
       '[:find ?e0 ?friend ?bar
         :where
         [?e0 :foo/id ?id]
         [?e0 :foo/friend ?friend]
         [?friend :foo/bar ?bar]]
       {:foo/id {"123" {:foo/id "123"
                        :foo/friend [:foo/id "456"]
                        :foo/bar "asdf"}
                 "456" {:foo/id "456"
                        :foo/bar "baz"}}
        :foo {:bar "baz"}
        :asdf "jkl"})
      (execute)
      #_(->> (map meta)))

  ;; not-found
  (execute
   '{:find [?e ?id]
     :in {$ [{:foo/id {"123" {:foo/id "123"
                              :foo/bar "baz"}
                       "456" {:foo/id "456"
                              :foo/bar "asdf"}}
              :foo {:bar "baz"}
              :asdf "jkl"}]
          ?bar ["bat"]}
     :where
     ([?e :foo/id ?id]
      [?e :foo/bar ?bar])})

  )


(defn q
  [query db & params]
  (-> (apply parse query db params)
      (execute)))
