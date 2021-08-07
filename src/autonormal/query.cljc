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


(defn- join-type
  [m1 m2]
  (let [ks1 (set (keys m1))
        ks2 (set (keys m2))
        common (set/intersection ks1 ks2)]
    (cond
      (and (seq common) (= (select-keys m1 common) (select-keys m2 common)))
      :join

      (seq common)
      :mismatch

      :else :disjoint)))


(comment
  (join-type
   {:foo "bar" :baz 123}
   {:foo "bar" :asdf 456})

  (join-type
   {:foo "bar" :baz 123}
   {:foo "asdf"})

  (join-type
   {:foo "bar" :baz 123}
   {:asdf "jkl"})
  )

(defn- join-results
  [res1 res2]
  #_(->> (for [r1 res1
               r2 res2
               :let [no-join? (every? #(not (contains? r1 (key %))) r2)
                     matches? (some #(= (val %) (get r1 (key %))) r2)]
               :when (or no-join? matches?)]
           (if no-join?
             r1
             (merge r1 r2)))
         set)
  (let [left->join-types+r2 (->> (for [r1 res1]
                                   [r1 (for [r2 res2
                                             :let [jt (join-type r1 r2)]]
                                         [jt r2])])
                                 (into {}))]
    (->> (for [[r1 join-types+r2] left->join-types+r2
               :when (every? #(not= :mismatch (first %)) join-types+r2)
               [_ r2] join-types+r2]
           [r1 r2])
         (apply concat)
         (set))))


(comment
  ;; matches
  (join-results
   '[{?e [:foo/id "123"], ?id "123"}
     {?e [:foo/id "456"], ?id "456"}
     {?asdf "jkl"}]
   '[{?e [:foo/id "123"] ?foo "123"}])

  (join-results
   '[{?e [:foo/id "123"], ?id "123"}
     {?e [:foo/id "456"], ?id "456"}
     {?asdf "jkl"}]
   '[{?e [:foo/id "123"] ?foo "bar"}
     {?e [:foo/id "456"] ?foo "baz"}])

  (join-results
   '[{?e [:foo/id "123"], ?id "123"}
     {?e [:foo/id "456"], ?id "456"}
     {?asdf "jkl"}]
   '[{?foo "bar"}
     {?foo "baz"}])

  ;; no matches
  (join-results
   '[{?e [:foo/id "123"], ?id "123"}
     {?e [:foo/id "456"], ?id "456"}
     {?asdf "jkl"}]
   '[{?e [:foo/id "123"] ?id "456"}])
  )



(defn execute
  [{:keys [find in where]}]
  (let [db (get in $)
        idents (idents db)]
    (loop [clauses where
           results [in]]
      (if-let [_clause (first clauses)]
        (recur
         (rest clauses)
         (for [res results]
           []))
        results))))


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


