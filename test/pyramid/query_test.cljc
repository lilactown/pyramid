(ns pyramid.query-test
  (:require
   [pyramid.core :as core]
   [pyramid.query :as p.q :refer [q]]
   [clojure.test :as t]))


(def db
  ^{`p.q/entities core/entities}
  {:person/id {"123" {:person/id "123"
                      :person/name "foo"
                      :person/best-friend [:person/id "789"]
                      :person/friends [[:person/id "456"]
                                       [:person/id "789"]]}
               "456" {:person/id "456"
                      :person/name "bar"
                      :person/friends [[:person/id "123"]
                                       [:person/id "789"]]}
               "789" {:person/id "789"
                      :person/name "baz"
                      :person/friends [[:person/id "123"]
                                       [:person/id "456"]]}
               "1011" {:person/id "1011"
                       :meta {:asdf [{:jkl 42}
                                     {:jkl 84}
                                     {:jkl 128}]}}}
   :person {:bar "baz"}
   :asdf "jkl"})


(t/deftest joins
  (t/is (= '([42] [84] [128])
           (q '[:find ?jkl
                :where
                [?e :person/id "1011"]
                [?e :meta ?meta]
                [?meta :asdf ^:many ?asdf]
                [?asdf :jkl ?jkl]]
              db))
        "map entities")

  (t/is (= '(["123"] ["456"] ["789"] ["1011"])
           (q '[:find ?id
                :where
                [?e :person/id ?id]]
              db)))

  (t/is (= '(["123" "foo"] ["456" "bar"] ["789" "baz"])
           (q '[:find ?id ?name
                :where
                [?e :person/id ?id]
                [?e :person/name ?name]]
              db)))

  (t/is (= '(["123" "foo" [:person/id "789"]])
           (q '[:find ?id ?name ?friend
                :where
                [?e :person/id ?id]
                [?e :person/name ?name]
                [?e :person/best-friend ?friend]]
              db)))

  (t/is (= '(["foo" "baz"])
           (q '[:find ?name ?friend-name
                :where
                [?e :person/name ?name]
                [?e :person/best-friend ?friend]
                [?friend :person/name ?friend-name]]
              db)))

  (t/is (= '()
           (q '[:find ?id
                :where
                [?e :person/name "asdf"]
                [?e :person/id ?id]]
              db))
        "not found")

  (t/is (= '(["123" "foo"])
           (q '[:find ?id ?name
                :in $ ?name
                :where
                [?e :person/name ?name]
                [?e :person/id ?id]]
              db
              "foo"))
        "join on :in")

  (t/is (= '(["foo" "bar"]
             ["foo" "baz"]
             ["bar" "foo"]
             ["bar" "baz"]
             ["baz" "foo"]
             ["baz" "bar"])
           (q '[:find ?name ?friend-name
                :where
                [?e :person/name ?name]
                [?e :person/friends ^:many ?friend]
                [?friend :person/name ?friend-name]]
              db))
        "multiple cardinality value")

  (t/is (= '(["123" "foo"]
             ["456" "bar"])
           (q '[:find ?id ?name
                :in $ ^:many ?name
                :where
                [?e :person/name ?name]
                [?e :person/id ?id]]
              db
              ["foo" "bar"]))
        "multi cardinality join on :in")

  (t/is (= '(["foo" "foo"]
             ["foo" "bar"]
             ["foo" "baz"]
             ["bar" "foo"]
             ["bar" "bar"]
             ["bar" "baz"]
             ["baz" "foo"]
             ["baz" "bar"]
             ["baz" "baz"])
           (q '[:find ?name1 ?name2
                :where
                [?e1 :person/name ?name1]
                [?e2 :person/name ?name2]]
              db))
        "cross product"))


(t/deftest query-map
  (t/is (= [[42]]
           (q '[:find ?baz
                :where
                [[:foo] :bar ?bar]
                [?bar :baz ?baz]]
              {:foo {:bar {:baz 42}}})))
  (t/is (= [[:foo {:bar {:baz {:asdf 42}}}]
            [:bar {:baz {:asdf 42}}]
            [:baz {:asdf 42}]
            [:asdf 42]]
           (q '[:find ?a ?v
                :where
                [?e ?a ?v]]
              {:foo {:bar {:baz {:asdf 42}}}})))
  (t/is (= [[{:asdf "jkl"}] [{:asdf "qwerty"}] [{:asdf "uiop"}]]
           (q '[:find ?bar
                :where
                [[:foo] :bar ^:many ?bar]
                [?bar ?a ?v]]
              {:foo {:bar [{:asdf "jkl"}
                           {:asdf "qwerty"}
                           {:asdf "uiop"}]}}))))
