(ns pyramid.pull-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pyramid.pull :as p]))


(def entities
  (for [i (range 1000)]
    [:id i]))


(deftest many-entities
  (is (= (set entities)
         (:entities
          (trampoline
           p/pull-report
           {:id (into
                 {}
                 (map #(vector
                        (second %)
                        (hash-map (first %) (second %))))
                 entities)
            :all (vec entities)}
           [{:all [:id]}])))))


(deftest list-order
  (is (=
       '({:thing {:id 1}} {:thing {:id 2}} {:thing {:id 3}}),
       (let [db {:id {1 {:id 1}
                      2 {:id 2}
                      3 {:id 3}
                      9 {:id 9
                         :my-list '({:thing [:id 1]}
                                    {:thing [:id 2]}
                                    {:thing [:id 3]})}}}
             query [{[:id 9] [:id {:my-list [{:thing [:id]}]}]}]]
         (-> (trampoline p/pull-report db query)
             (get-in [:data [:id 9] :my-list]))))))


(deftest heterogeneous-colls
  (let [db {:person/id {0 {:person/id 0
                           :person/name "Bill"
                           :person/friends [{:person/name "Bob"}
                                            [:person/id 2]]}
                        2 {:person/id 2
                           :person/name "Alice"}}}
        query [{[:person/id 0] [:person/name {:person/friends [:person/name]}]}]]
    (is
     (= {[:person/id 0] {:person/name "Bill"
                         :person/friends [{:person/name "Bob"}
                                          {:person/name "Alice"}]}}
        (:data (trampoline p/pull-report db query))))))


(defrecord Visited [query result])


(defn visit
  [q]
  (with-meta q {:visitor #(->Visited (doto q prn) %2)}))


(deftype Opaque [result])


(deftest visitors
  (testing "simple join"
    (let [query [{:foo (visit [:bar :baz])}]
          data {:foo {:bar 123 :baz 456}}]
      (is (= {:foo (->Visited
                    [:bar :baz]
                    {:bar 123 :baz 456})}
             (:data (trampoline p/pull-report data query)))
          "single item"))
    (let [query [{:foo (visit [:bar :baz])}]
          data {:foo [{:bar 123 :baz 456}
                      {:bar 789 :baz "qux"}]}]
      (is (= {:foo [(->Visited [:bar :baz] {:bar 123 :baz 456})
                    (->Visited [:bar :baz] {:bar 789 :baz "qux"})]}
             (:data (trampoline p/pull-report data query)))
          "multiple items")))
  (testing "nested join"
    (let [query [{:foo (visit [{:bar [:baz]}])}]
          data {:foo {:bar {:baz 123}}}]
      (is (= {:foo (->Visited
                    [{:bar [:baz]}]
                    {:bar {:baz 123}})}
             (:data (trampoline p/pull-report data query))))))
  (testing "nested visitors"
    (let [query [{:foo (visit [{:bar (visit [:baz])}])}]
          data {:foo [{:bar {:baz 123}}
                      {:bar {:baz 456}}]}]
      (is (= {:foo [(->Visited
                     [{:bar [:baz]}]
                     {:bar (->Visited
                            [:baz]
                            {:baz 123})})
                    (->Visited
                     [{:bar [:baz]}]
                     {:bar (->Visited
                            [:baz]
                            {:baz 456})})]}
             (:data (trampoline p/pull-report data query))))))
  (testing "union"
    (let [query [{:foo (visit
                        {:bar [:bar :asdf :jkl]
                         :baz [:baz :arst :nei]
                         :qux [:qux :qwfp :luy]})}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (is (= {:foo (->Visited {:bar [:bar :asdf :jkl]
                               :baz [:baz :arst :nei]
                               :qux [:qux :qwfp :luy]}
                              {:bar 2 :asdf 123 :jkl 456})}
             (:data (trampoline p/pull-report data query)))
          "whole union in visitor"))
    (let [query [{:foo {:bar (visit [:bar :asdf :jkl])
                        :baz (visit [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (is (= {:foo (->Visited [:bar :asdf :jkl]
                              {:bar 2 :asdf 123 :jkl 456})}
             (:data (trampoline p/pull-report data query)))
          "single item union entry"))
    (let [query [{:foo {:bar (visit [:bar :asdf :jkl])
                        :baz (visit [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo [{:qux 1 :qwfp 123 :luy 456}
                      {:bar 2 :asdf 123 :jkl 456}
                      {:baz 3 :arst 123 :nei 457}]}]
      (is (= {:foo [{:qux 1 :qwfp 123 :luy 456}
                    (->Visited [:bar :asdf :jkl]
                               {:bar 2 :asdf 123 :jkl 456})
                    (->Visited [:baz :arst :nei]
                               {:baz 3 :arst 123 :nei 457})]}
             (:data (trampoline p/pull-report data query)))
          "vector union entry")
      (is (= {:foo [{:qux 1 :qwfp 123 :luy 456}
                    (->Visited [:bar :asdf :jkl]
                               {:bar 2 :asdf 123 :jkl 456})
                    (->Visited [:baz :arst :nei]
                               {:baz 3 :arst 123 :nei 457})]}
             (:data (trampoline p/pull-report (update data :foo seq) query)))
          "seq union entry")))
  (testing "opaque transform"
    (let [query [{:foo
                  ^{:visitor #(->Opaque %2)}
                  [{:bar
                    ^{:visitor #(->Opaque %2)}
                    [:baz]}]}]
          data {:foo {:bar {:baz 123}}}]
      (is (instance?
           Opaque
           (-> (trampoline p/pull-report data query)
               (:data)
               (:foo))))
      (is (instance?
           Opaque
           (-> (trampoline p/pull-report data query)
               (:data)
               (:foo)
               (.-result)
               (:bar)))))))


(deftest issue-36
  (let [db '{:zones ([:zone/id "b3d91e54-75e0-424b-b4c6-363cbd0ff06a"]
                     [:zone/id "4fba72c3-97ec-4458-9e78-b75bd6b323d0"]
                     [:zone/id "5a3f1bf5-aee1-40f1-9ea3-313ea3123c90"])}
        q [{:zones [:zone/id]}]]
    (is (= '{:zones
             (#:zone{:id "b3d91e54-75e0-424b-b4c6-363cbd0ff06a"}
              #:zone{:id "4fba72c3-97ec-4458-9e78-b75bd6b323d0"}
              #:zone{:id "5a3f1bf5-aee1-40f1-9ea3-313ea3123c90"})}
           (:data (trampoline p/pull-report db q))))))
