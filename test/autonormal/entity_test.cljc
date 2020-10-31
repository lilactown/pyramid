(ns autonormal.entity-test
  (:require
   [autonormal.core :as a]
   [autonormal.entity :as a.e]
   [clojure.test :as t]))


(def will*
  {:person/id 123
   :person/name "Will"
   :best-friend {:person/id "asdf"}
   :friends [{:person/id "asdf"}
             {:person/id 456}
             {:person/id 789}]})


(def andrea*
  {:person/id "asdf"
   :person/name "Andrea"
   :best-friend {:person/id 123}})


(def ppl (a/db [will* andrea*]))


(def ppl_2 (a/db [will* andrea*]))


(def will (a.e/entity ppl [:person/id 123]))


(t/deftest equality
  (t/is (= will
           ;; duplicate entity
           (a.e/entity ppl [:person/id 123])
           ;; duplicate entity from other database value
           (a.e/entity ppl_2 [:person/id 123]))))


(t/deftest realize-top-level
  (t/is (= {:person/id 123
            :person/name "Will"
            :best-friend (a.e/entity ppl [:person/id "asdf"])
            :friends [(a.e/entity ppl [:person/id "asdf"])
                      (a.e/entity ppl [:person/id 456])
                      (a.e/entity ppl [:person/id 789])]}
           (into {} will))))


(t/deftest getting
  (t/is (= (a.e/entity ppl [:person/id "asdf"])
           (get will :best-friend))))


(t/deftest get-coll
  (t/is (= [(a.e/entity ppl [:person/id "asdf"])
            (a.e/entity ppl [:person/id 456])
            (a.e/entity ppl [:person/id 789])]
           (get will :friends))))


(t/deftest get-cycle
  (t/is (= (a.e/entity ppl [:person/id 123])
           (get-in will [:best-friend :best-friend :best-friend :best-friend]))))
