(ns autonormal.query
  (:require
   [clojure.set :as set]
   [clojure.string :as string]))


(def ? '?)
(def $ '$)
(def _ '_)


(defn parse
  [query db & params]
  (let [[bindings clauses] (split-with #(not= % :where) query)
        variables (->> bindings
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
    {:find variables
     :in (-> inputs
             (zipmap (cons db params))
             ;; ensure inputs are always sequential collections
             (->> (reduce-kv
                   (fn [m k v]
                     (if (sequential? v)
                       (assoc m k v)
                       (assoc m k [v])))
                   {})))
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


(defn- idents
  [db]
  (->> db
       (filter (comp map? val))
       (mapcat
        (fn [[k id->v]]
          (->> id->v
               (filter (comp map? val))
               (map key)
               (map #(vector k %)))))))


(defn- contains-in?
  [m ks]
  (let [path (drop-last ks)
        k (last ks)]
    (contains? (get-in m path) k)))


(defn- resolve-triple
  [db triple]
  (let [[e a v] triple
        idents (idents db)
        result-pattern (:pattern (meta triple) triple)
        result-meta {:pattern result-pattern}]
    ;; TODO handle multi cardinality values
    (case (map #(pattern %) triple)
      [:v :v :v]
      (if (= v (get-in db (conj e a)))
        [[]]
        [])

      [? :v :v]
      (into
       []
       (comp
        (filter #(= v (get-in db (conj % a))))
        (map #(with-meta [% a v] result-meta)))
       idents)

      [:v ? :v]
      (into
       []
       (comp
        (filter #(= v (val %)))
        (map key)
        (map #(with-meta [e % v] result-meta)))
       (get-in db e))

      [:v :v ?]
      (if (contains-in? db (conj e a))
        [(with-meta [e a (get-in db (conj e a))]
           result-meta)]
        [])

      [? ? :v]
      (mapcat
       (fn [ident]
         (->> (get-in db ident)
              (filter #(= v (val %)))
              (map (fn [entry]
                     (with-meta [ident (key entry) v]
                       result-meta)))))
       idents)

      [? :v ?]
      (into
       []
       (comp
        (filter #(contains-in? db (conj % a)))
        (map (fn [ident]
               (with-meta [ident a (get-in db (conj ident a))]
                 result-meta))))
       idents)

      [:v ? ?]
      (mapv
       (fn [entry]
         (with-meta [e (key entry) (val entry)]
           result-meta))
       (get-in db e))

      [? ? ?]
      (for [ident idents
            :let [entity (get-in db ident)]
            entry entity]
        (with-meta [ident (key entry) (val entry)]
          result-meta)))))


(comment
 (def db {:foo/id {"123" {:foo/id "123"
                          :foo/bar "baz"}
                   "456" {:foo/id "456"
                          :foo/bar "asdf"}}
          :foo {:bar "baz"}
          :asdf "jkl"})

 ;; [:v :v :v] found
 (resolve-triple db [[:foo/id "123"] :foo/bar "baz"])

 (map meta
      (resolve-triple db (with-meta
                       [[:foo/id "123"] :foo/bar "baz"]
                       {:pattern '[?e :foo/bar ?bar]})))

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



(defn- rewrite-triple
  [results [e a v :as triple]]
  (-> (for [result results
            :let [pattern (:pattern (meta result))
                  ;; get mapping of var to result index
                  var->idx (into
                            {}
                            (comp
                             (map-indexed #(vector %2 %1))
                             (filter #(variable? (first %))))
                            pattern)
                  [e' a' v'] (map var->idx triple)]
            :when (or e' a' v')]
        (with-meta
          [(or (get result e') e)
           (or (get result a') a)
           (or (get result v') v)]
          {:pattern triple}))
      (seq)
      ;; if we didn't find any matches at all, return the original triple
      (or [triple])))


(comment
  (rewrite-triple
   [(with-meta [[:foo/id 1] :foo/id 1]
      {:pattern '[?e :foo/id ?id]})
    (with-meta [[:foo/id 2] :foo/id 2]
      {:pattern '[?e :foo/id ?id]})]
   '[?e :foo/name ?name])


  (rewrite-triple
   [(with-meta [[:foo/id 1] :foo/id 1]
      {:pattern '[?e :foo/id ?id]})
    (with-meta [[:foo/id 2] :foo/id 2]
      {:pattern '[?e :foo/id ?id]})]
   '[?foo :foo/name ?name])



  (map meta
   (rewrite-triple
    [(with-meta [[:foo/id 1] :foo/id 1]
       {:pattern '[?e :foo/id ?id]})]
    '[?e :foo/name ?name]))
  )


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
    (map #(when (some? %) (nth result %)) indices)))



(defn execute
  [{:keys [find in where]}]
  (let [db (first (get in $))]
    (loop [clauses where
           results []]
      (if-let [clause (doto (first clauses) (prn "--- "))]
        (recur
         (rest clauses)
         (for [clause' (rewrite-triple results clause)
               :let [results (resolve-triple db clause')]
               result results]
           result))
        (project-results results find)))))


(comment
  ;; found
  (-> (parse
       '[:find ?e ?id
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
       '[:find ?e ?id
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
      (execute))

  (-> (parse
       '[:find ?e ?id
         :in $ ?bar
         :where
         [?e :foo/id ?id]
         [?e :foo/bar ?bar]]
       {:foo/id {"123" {:foo/id "123"
                        :foo/bar "baz"}
                 "456" {:foo/id "456"
                        :foo/bar "asdf"}}
        :foo {:bar "baz"}
        :asdf "jkl"}
       "baz")
      (execute))

  ;; not-found
  (execute
   '{:find [?e ?id]
     :in {$ {:foo/id {"123" {:foo/id "123"
                             :foo/bar "baz"}
                      "456" {:foo/id "456"
                             :foo/bar "asdf"}}
             :foo {:bar "baz"}
             :asdf "jkl"}
          ?bar ["bat"]}
     :where
     ([?e :foo/id ?id]
      [?e :foo/bar ?bar])})

  )


