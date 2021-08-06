(ns autonormal.query
  (:require
   [clojure.string :as string]))


(def ? '?)
(def $ '$)
(def _ '_)


(defn- normalize-clause
  [clause]
  (if (vector? clause)
    (case (count clause)
      ;; [?e ?attr ?value]
      3
      {:entity (first clause)
       :attr (second clause)
       :value (nth clause 2)}

      ;; [?e ?attr]
      2
      {:entity (first clause)
       :attr (second clause)
       :value '_})
    {}))


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
             (zipmap (cons db params)))
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


#_(defn- normalize-db
  "Takes a db like {:foo/id {\"123\" {:foo/id \"123\"}}} and transforms it into

  {[:foo/id \"123\"] {:foo/id \"123}}"
  [db]
  (into
   {}
   (comp
    (filter (comp map? val))
    (mapcat
     (fn [[k id->v]]
       (map
        #(vector [k (key %)] (val %))
        id->v))))
   db))


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
        idents (idents db)]
    (case (map #(pattern %) triple)
      [:v :v :v]
      (when (= v (get-in db (conj e a)))
        [])

      [? :v :v]
      (into
       []
       (comp
        (filter #(= v (get-in db (conj % a))))
        (map (fn [ident] {e ident})))
       idents)

      [:v ? :v]
      (into
       []
       (comp
        (filter #(= v (val %)))
        (map key)
        (map (fn [k] {a k})))
       (get-in db e))

      [:v :v ?]
      (if (contains-in? db (conj e a))
        [{v (get-in db (conj e a))}]
        [])

      [? ? :v]
      (mapcat
       (fn [ident]
         (->> (get-in db ident)
              (filter #(= v (val %)))
              (map (fn [m]
                     {e ident
                      a (key m)}))))
       idents)

      [? :v ?]
      (into
       []
       (comp
        (filter #(contains-in? db (conj % a)))
        (map (fn [ident]
               {e ident
                v (get-in db (conj ident a))})))
       idents)

      [:v ? ?]
      (mapv
       (fn [entry]
         {a (key entry) v (val entry)})
       (get-in db e))

      [? ? ?]
      (for [ident idents
            :let [entity (get-in db ident)]
            entry entity]
        {e ident
         a (key entry)
         v (val entry)}))))


(comment
 (def db {:foo/id {"123" {:foo/id "123"
                          :foo/bar "baz"}
                   "456" {:foo/id "456"
                          :foo/bar "asdf"}}
          :foo {:bar "baz"}
          :asdf "jkl"})

 ;; [:v :v :v] found
 (resolve-triple db ['[:foo/id "123"] :foo/bar "baz"])

 ;; [:v :v :v] not-found
 (resolve-triple db ['[:foo/id "456"] :foo/bar "bar"])

 (resolve-triple db ['[:foo/id "123"] :foo/bat "bar"])

 (resolve-triple db ['[:foo/id "123"] :foo/bar "bat"])

 (resolve-triple db ['[:foo/id "asdf"] :foo/bar "bar"])


 ;; [? :v :v] found
 (resolve-triple db '[?e :foo/bar "baz"])

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



(defn execute
  [{:keys [find in where]}]
  (let [db (get in $)
        idents (idents db)]
    (loop [clauses where
           resolved '()]
      (if-let [clause (first clauses)]
        (recur
         (rest clauses)
         (let [clause' (mapv
                        ;; resolve any variables that are contained in `in`
                        (fn [v]
                          (if (and (variable? v)
                                   (contains? in v))
                            (get in v)
                            v))
                        clause)]
           (conj
            resolved
            {:clause clause
             :result (resolve-triple db clause')})))
        resolved))))


#_(execute
 '{:find [?e ?id]
   :in {$ {:foo/id {"123" {:foo/id "123"
                           :foo/bar "baz"}
                    "456" {:foo/id "456"
                           :foo/bar "asdf"}}
           :foo {:bar "baz"}
           :asdf "jkl"}
        ?bar "baz"}
   :where
   ([?e :foo/id ?id]
    [?e :foo/bar ?bar])})


