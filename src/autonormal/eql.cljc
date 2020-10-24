(ns autonormal.eql
  (:require
   [autonormal.core :as a]
   [edn-query-language.core :as eql]))


(def q0 [:person/name :person/id])


(def q1 [{:people/all [:person/name :person/id]}])


(def q2 [{:people/all [:person/name :person/id {:best-friend [:person/name]}]}])


(def q3 [{[:person/id 123] [:person/id :person/name]}])


(eql/query->ast q3)


(defn query
  [db query]
  (let [children (:children (eql/query->ast query))]
    (for [child children]
      ())))
