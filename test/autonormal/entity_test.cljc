(ns autonormal.entity-test
  (:require
   [autonormal.core :as a]
   [autonormal.entity :as a.e]
   [clojure.test :as t]))


(def ppl (a/db [{:person/id 123
                 :person/name "Will"
                 :best-friend {:person/id "asdf"}
                 :friends [{:person/id "asdf"}
                           {:person/id 456}
                           {:person/id 789}]}
                {:person/id "asdf"
                 :person/name "Andrea"
                 :best-friend {:person/id 123}}]))


(def will (a.e/entity ppl [:person/id 123]))


(t/deftest realize-top-level
  (t/is (= {:person/id 123
            :person/name "Will"
            :best-friend {:person/id "asdf"
                          :person/name "Andrea"
                          :best-friend [:person/id 123]}
            :friends [{:person/id "asdf"
                       :person/name "Andrea"
                       :best-friend [:person/id 123]}
                      {:person/id 456}
                      {:person/id 789}]}
           (into {} will))))


(t/deftest getting
  (t/is (= (a.e/entity ppl [:person/id "asdf"])
           (get will :best-friend))))


(t/deftest ident-coll
  (t/is (= [{:person/id "asdf"
             :person/name "Andrea"
             :best-friend [:person/id 123]}
            {:person/id 456}
            {:person/id 789}]
           (into [] (get will :friends)))))


(t/deftest get-cycle
  (t/is (= (a.e/entity ppl [:person/id 123])
           (get-in will [:best-friend :best-friend :best-friend :best-friend]))))
