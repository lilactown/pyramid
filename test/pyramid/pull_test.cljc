(ns pyramid.pull-test
  (:require
   [clojure.test :as t]
   [pyramid.pull :as p]))


(def entities
  (for [i (range 1000)]
    [:id i]))


(t/deftest many-entities
  (t/is (= (set entities)
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


(t/deftest list-order
  (t/is (=
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


(defrecord Visited [query result])


(defn visit
  [q]
  (with-meta q {:visitor #(->Visited q %2)}))


(deftype Opaque [result])


(t/deftest visitors
  (t/testing "simple join"
    (let [query [{:foo (visit [:bar :baz])}]
          data {:foo {:bar 123 :baz 456}}]
      (t/is (= {:foo (->Visited
                      [:bar :baz]
                      {:bar 123 :baz 456})}
               (:data (trampoline p/pull-report data query)))
            "single item"))
    (let [query [{:foo (visit [:bar :baz])}]
          data {:foo [{:bar 123 :baz 456}
                      {:bar 789 :baz "qux"}]}]
      (t/is (= {:foo (->Visited
                      [:bar :baz]
                      [{:bar 123 :baz 456} {:bar 789 :baz "qux"}])}
               (:data (trampoline p/pull-report data query)))
            "multiple items")))
  (t/testing "nested join"
    (let [query [{:foo (visit [{:bar [:baz]}])}]
          data {:foo {:bar {:baz 123}}}]
      (t/is (= {:foo (->Visited
                      [{:bar [:baz]}]
                      {:bar {:baz 123}})}
               (:data (trampoline p/pull-report data query))))))
  (t/testing "nested visitors"
    (let [query [{:foo (visit [{:bar (visit [:baz])}])}]
          data {:foo [{:bar {:baz 123}}
                      {:bar {:baz 456}}]}]
      (t/is (= {:foo (->Visited
                      [{:bar (visit [:baz])}]
                      [{:bar (->Visited
                              [:baz]
                              {:baz 123})}
                       {:bar (->Visited
                              [:baz]
                              {:baz 456})}])}
               (:data (trampoline p/pull-report data query))))))
  (t/testing "union"
    (let [query [{:foo (visit
                        {:bar [:bar :asdf :jkl]
                         :baz [:baz :arst :nei]
                         :qux [:qux :qwfp :luy]})}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (t/is (= {:foo (->Visited {:bar [:bar :asdf :jkl]
                                 :baz [:baz :arst :nei]
                                 :qux [:qux :qwfp :luy]}
                                {:bar 2 :asdf 123 :jkl 456})}
               (:data (trampoline p/pull-report data query)))
            "whole union in visitor"))
    (let [query [{:foo {:bar (visit [:bar :asdf :jkl])
                        :baz (visit [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (t/is (= {:foo (->Visited [:bar :asdf :jkl]
                                {:bar 2 :asdf 123 :jkl 456})}
               (:data (trampoline p/pull-report data query)))
            "single item union entry"))
    (let [query [{:foo {:bar (visit [:bar :asdf :jkl])
                        :baz (visit [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo [{:qux 1 :qwfp 123 :luy 456}
                      {:bar 2 :asdf 123 :jkl 456}
                      {:baz 3 :arst 123 :nei 457}]}]
      (t/is (= {:foo [{:qux 1 :qwfp 123 :luy 456}
                      (->Visited [:bar :asdf :jkl]
                                 {:bar 2 :asdf 123 :jkl 456})
                      (->Visited [:baz :arst :nei]
                                 {:baz 3 :arst 123 :nei 457})]}
               (:data (trampoline p/pull-report data query)))
            "vector union entry")
      (t/is (= {:foo [{:qux 1 :qwfp 123 :luy 456}
                      (->Visited [:bar :asdf :jkl]
                                 {:bar 2 :asdf 123 :jkl 456})
                      (->Visited [:baz :arst :nei]
                                 {:baz 3 :arst 123 :nei 457})]}
               (:data (trampoline p/pull-report (update data :foo seq) query)))
            "seq union entry")))
  (t/testing "opaque transform"
    (let [query [{:foo
                  ^{:visitor #(->Opaque %2)}
                  [{:bar
                    ^{:visitor #(->Opaque %2)}
                    [:baz]}]}]
          data {:foo {:bar {:baz 123}}}]
      (t/is (instance?
             Opaque
             (-> (trampoline p/pull-report data query)
                 (:data)
                 (:foo))))
      (t/is (instance?
             Opaque
             (-> (trampoline p/pull-report data query)
                 (:data)
                 (:foo)
                 (.-result)
                 (:bar)))))))
