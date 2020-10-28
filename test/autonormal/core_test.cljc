(ns autonormal.core-test
  (:require
   [autonormal.core :as a]
   [clojure.test :as t]))


(t/deftest normalization
  (t/is (= {:person/id {0 {:person/id 0}}}
           (a/db [{:person/id 0}]))
        "a single entity")
  (t/is (= {:person/id {0 {:person/id 0
                           :person/name "asdf"}
                        1 {:person/id 1
                           :person/name "jkl"}}}
           (a/db [{:person/id 0
                   :person/name "asdf"}
                  {:person/id 1
                   :person/name "jkl"}]))
        "multiple entities with attributes")
  (t/is (= {:person/id {0 {:person/id 0
                           :person/name "asdf"}
                        1 {:person/id 1
                           :person/name "jkl"}}
            :people [[:person/id 0]
                     [:person/id 1]]}
           (a/db [{:people [{:person/id 0
                             :person/name "asdf"}
                            {:person/id 1
                             :person/name "jkl"}] } ]))
        "nested under a key")
  (t/is (= #:person{:id
                    {123
                     {:person/id 123,
                      :person/name "Will",
                      :contact {:phone "000-000-0001"},
                      :best-friend [:person/id 456],
                      :friends
                      [[:person/id 9001]
                       [:person/id 456]
                       [:person/id 789]
                       [:person/id 1000]]},
                     456
                     {:person/id 456,
                      :person/name "Jose",
                      :account/email "asdf@jkl",
                      :best-friend [:person/id 123]},
                     9001 #:person{:id 9001, :name "Georgia"},
                     789 #:person{:id 789, :name "Frank"},
                     1000 #:person{:id 1000, :name "Robert"}}}
           (a/db [{:person/id 123
                   :person/name "Will"
                   :contact {:phone "000-000-0001"}
                   :best-friend
                   {:person/id 456
                    :person/name "Jose"
                    :account/email "asdf@jkl"}
                   :friends
                   [{:person/id 9001
                     :person/name "Georgia"}
                    {:person/id 456
                     :person/name "Jose"}
                    {:person/id 789
                     :person/name "Frank"}
                    {:person/id 1000
                     :person/name "Robert"}]}
                  {:person/id 456
                   :best-friend {:person/id 123}}]))
        "refs"))
