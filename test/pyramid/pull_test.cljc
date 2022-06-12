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
