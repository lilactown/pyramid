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


(defrecord Component [query result]
  p/IComponent
  (-with-result [this result] (assoc this :result result)))


(defn ->comp [q] (with-meta q
                   {:component (->Component q nil)}))


(comment
  (edn-query-language.core/query->ast [{:foo (->comp [:bar :baz])}])
  (edn-query-language.core/query->ast [{:foo {:bar (->comp [:bar :asdf :jkl])
                                              :baz (->comp [:baz :arst :nei])
                                              :qux [:qux :qwfp :luy]}}])

  (= {:query nil :result nil} (->Component nil nil)))


(t/deftest components
  (t/testing "simple join"
    (let [query [{:foo (->comp [:bar :baz])}]
         data {:foo {:bar 123 :baz 456}}]
     (t/is (= {:foo (->Component
                     [:bar :baz]
                     {:bar 123 :baz 456})}
              (:data (trampoline p/pull-report data query)))
           "single item"))
    (let [query [{:foo (->comp [:bar :baz])}]
          data {:foo [{:bar 123 :baz 456}
                      {:bar 789 :baz "qux"}]}]
      (t/is (= {:foo (->Component
                      [:bar :baz]
                      [{:bar 123 :baz 456} {:bar 789 :baz "qux"}])}
               (:data (trampoline p/pull-report data query)))
            "multiple items")))
  (t/testing "nested join"
    (let [query [{:foo (->comp [{:bar [:baz]}])}]
          data {:foo {:bar {:baz 123}}}]
      (t/is (= {:foo (->Component
                      [{:bar [:baz]}]
                      {:bar {:baz 123}})}
               (:data (trampoline p/pull-report data query))))))
  (t/testing "nested components"
    (let [query [{:foo (->comp [{:bar (->comp [:baz])}])}]
          data {:foo [{:bar {:baz 123}}
                      {:bar {:baz 456}}]}]
      (t/is (= {:foo (->Component
                      [{:bar (->comp [:baz])}]
                      [{:bar (->Component
                              [:baz]
                              {:baz 123})}
                       {:bar (->Component
                              [:baz]
                              {:baz 456})}])}
               (:data (trampoline p/pull-report data query))))))
  (t/testing "union"
    (let [query [{:foo (->comp
                        {:bar [:bar :asdf :jkl]
                         :baz [:baz :arst :nei]
                         :qux [:qux :qwfp :luy]})}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (t/is (= {:foo (->Component {:bar [:bar :asdf :jkl]
                                   :baz [:baz :arst :nei]
                                   :qux [:qux :qwfp :luy]}
                                  {:bar 2 :asdf 123 :jkl 456})}
               (:data (trampoline p/pull-report data query)))
            "whole union in component"))
    (let [query [{:foo {:bar (->comp [:bar :asdf :jkl])
                        :baz (->comp [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo {:bar 2 :asdf 123 :jkl 456}}]
      (t/is (= {:foo (->Component [:bar :asdf :jkl]
                                  {:bar 2 :asdf 123 :jkl 456})}
               (:data (trampoline p/pull-report data query)))
            "single item union entry"))
    (let [query [{:foo {:bar (->comp [:bar :asdf :jkl])
                        :baz (->comp [:baz :arst :nei])
                        :qux [:qux :qwfp :luy]}}]
          data {:foo [{:qux 1 :qwfp 123 :luy 456}
                      {:bar 2 :asdf 123 :jkl 456}
                      {:baz 3 :arst 123 :nei 457}]}]
      (t/is (= {:foo [{:qux 1 :qwfp 123 :luy 456}
                      (->Component [:bar :asdf :jkl]
                                   {:bar 2 :asdf 123 :jkl 456})
                      (->Component [:baz :arst :nei]
                                   {:baz 3 :arst 123 :nei 457})]}
               (:data (trampoline p/pull-report data query)))
            "multiple item union entry"))))
