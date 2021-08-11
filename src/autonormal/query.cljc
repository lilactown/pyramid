(ns autonormal.query
  (:require
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
    #_(prn triple (map pattern triple))
    ;; TODO handle multi cardinality values
    (case (map pattern triple)
      [:v :v :v]
      (if (= v (get-in db (conj e a)))
        [[]]
        [])

      [? :v :v]
      (into
       []
       (comp
        (filter #(= v (get-in db (conj % a) ::not-found)))
        (map vector))
       idents)

      [:v ? :v]
      (into
       []
       (comp
        (filter #(= v (val %)))
        (map key))
       (get-in db e))

      [:v :v ?]
      (if (contains-in? db (conj e a))
        (if (:many (meta v))
          (map vector (get-in db (conj e a)))
          [[(get-in db (conj e a))]])
        [])

      [? ? :v]
      (mapcat
       (fn [ident]
         (->> (get-in db ident)
              (filter #(= v (val %)))
              (map (fn [entry]
                     [ident (key entry)]))))
       idents)

      [? :v ?]
      (into
       []
       (comp
        (filter #(contains-in? db (conj % a)))
        (if (:many (meta v))
          (mapcat
           (fn [ident]
             (map
              (fn [value] [ident value])
              (get-in db (conj ident a)))))
          (map
           (fn [ident]
             [ident (get-in db (conj ident a))]))))
       idents)

      [:v ? ?]
      (if (:many (meta v))
        (into
         []
         (mapcat
          (fn [entry]
            (let [k (key entry)]
              (map #(vector k %) (val entry))))
          (get-in db e)))
        (mapv
         (fn [entry]
           [(key entry) (val entry)])
         (get-in db e)))

      [? ? ?]
      (if (:many (meta v))
        (for [ident idents
              :let [entity (get-in db ident)]
              entry entity
              values (val entry)]
          [ident (key entry) values])
        (for [ident idents
              :let [entity (get-in db ident)]
              entry entity]
          [ident (key entry) (val entry)])))))


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
              [ei ai vi] (map var->idx triple)]
        :let [triple' [(if ei (nth left ei) e)
                       (if ai (nth left ai) a)
                       (if vi (get left vi) v)]
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
    '[?e :foo/bar ?bar])
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
    (mapv #(when (some? %) (nth result %)) indices)))


(defn execute
  [{:keys [find in where]}]
  (let [db (get in $)]
    (loop [clauses where
           results [(with-meta
                      (vec (vals in))
                      {:pattern (vec (keys in))})]]
;;      (prn (first results))
 ;;     (prn (meta (first results)))
      (if-let [clause (first clauses)]
        (recur
         (rest clauses)
         (rewrite-and-resolve-triple db results clause))
        (do
   ;;       (prn (first results))
  ;;        (prn (meta (first results)))
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
             :asdf "jkl"} ]
          ?bar ["bat"]}
     :where
     ([?e :foo/id ?id]
      [?e :foo/bar ?bar])})

  )


(defn q
  [query db & params]
  (-> (apply parse query db params)
      (execute)))
