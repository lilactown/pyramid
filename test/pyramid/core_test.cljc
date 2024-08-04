(ns pyramid.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pyramid.core :as p]
   [pyramid.ident :as ident]))


(deftest normalization
  (is (= {:person/id {0 {:person/id 0}}}
         (p/db [{:person/id 0}]))
      "a single entity")
  (is (= {:person/id {0 {:person/id 0
                         :person/name "asdf"}
                      1 {:person/id 1
                         :person/name "jkl"}}}
         (p/db [{:person/id 0
                 :person/name "asdf"}
                {:person/id 1
                 :person/name "jkl"}]))
      "multiple entities with attributes")
  (is (= {:person/id {0 {:person/id 0
                         :person/name "asdf"}
                      1 {:person/id 1
                         :person/name "jkl"}}
          :people [[:person/id 0]
                   [:person/id 1]]}
         (p/db [{:people [{:person/id 0
                           :person/name "asdf"}
                          {:person/id 1
                           :person/name "jkl"}]}]))
      "nested under a key")
  (is (= {:person/id {0 {:person/id 0
                         :some-data {1 "hello"
                                     3 "world"}}}}
         (p/db [{:person/id 0
                 :some-data {1 "hello"
                             3 "world"}}]))
      "Map with numbers as keys")
  (is (= {:a/id {1 {:a/id 1
                    :b [{:c [:d/id 1]}]}}
          :d/id {1 {:d/id 1
                    :d/txt "a"}}}
         (p/db [{:a/id 1
                 :b [{:c {:d/id 1
                          :d/txt "a"}}]}]))
      "Collections of non-entities still get normalized")
  (is (= {:person/id {0 {:person/id 0
                         :person/name "Bill"
                         :person/friends [{:person/name "Bob"}
                                          [:person/id 2]]}
                      2 {:person/id 2
                         :person/name "Alice"}}}
         (p/db [{:person/id 0
                 :person/name "Bill"
                 :person/friends [{:person/name "Bob"}
                                  {:person/name "Alice"
                                   :person/id 2}]}]))
      "heterogeneous collections")
  (is (= {:person/id
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
         (p/db [{:person/id 123
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


(deftest non-entities
  (is (= {:foo ["bar"]} (p/db [{:foo ["bar"]}])))
  (is (= {:person/id {0 {:person/id 0
                         :foo ["bar"]}}}
         (p/db [{:person/id 0
                 :foo ["bar"]}]))))


(deftest custom-schema
  (is (= {:color {"red" {:color "red" :hex "#ff0000"}}}
         (p/db [{:color "red" :hex "#ff0000"}]
               (ident/by-keys :color)))
      "ident/by-keys")
  (is (= {:color {"red" {:color "red" :hex "#ff0000"}}}
         (p/db [^{:db/ident :color}
                {:color "red" :hex "#ff0000"}]))
      "local schema")
  (testing "complex schema"
    (let [db (p/db [{:type "person"
                     :id "1234"
                     :purchases [{:type "item"
                                  :id "1234"}]}
                    {:type "item"
                     :id "5678"}
                    {:type "foo"}
                    {:id "bar"}]
                   (fn [entity]
                     (let [{:keys [type id]} entity]
                       (when (and (some? type) (some? id))
                         [(keyword type "id") id]))))]
      (is (= {:person/id
              {"1234" {:type "person", :id "1234", :purchases [[:item/id "1234"]]}},
              :item/id
              {"1234" {:type "item", :id "1234"}, "5678" {:type "item", :id "5678"}},
              :type "foo",
              :id "bar"}
             db)
          "correctly identifies entities")
      (is (= {[:person/id "1234"]
              {:type "person", :id "1234", :purchases [{:type "item", :id "1234"}]}}
             (p/pull db [{[:person/id "1234"] [:type :id {:purchases [:type :id]}]}]))
          "pull"))))


(deftest add
  (is (= {:person/id {0 {:person/id 0}}}
         (p/add {} {:person/id 0})))
  (is (= {:person/id {0 {:person/id 0 :person/name "Gill"}
                      1 {:person/id 1}}}
         (p/add
          {}
          {:person/id 0 :person/name "Alice"}
          {:person/id 1}
          {:person/id 0 :person/name "Gill"}))))


(deftest add-report
  (is (= {:db {:person/id {0 {:person/id 0}}}
          :entities #{[:person/id 0]}
          :indices #{}}
         (p/add-report {} {:person/id 0})))
  (is (= {:db {:person/id {0 {:person/id 0
                              :person/name "Gill"
                              :best-friend [:person/id 1]}
                           1 {:person/id 1
                              :person/name "Uma"}}
               :me [:person/id 0]}
          :entities #{[:person/id 0]
                      [:person/id 1]}
          :indices #{:me}}
         (p/add-report {} {:me {:person/id 0
                                :person/name "Gill"
                                :best-friend {:person/id 1
                                              :person/name "Uma"}}})))
  #_(is (= {:db {:person/id {0 {:person/id 0 :person/name "Gill"}
                             1 {:person/id 1}}}
            :entities #{{:person/id 0 :person/name "Gill"}
                        {:person/id 1}}}
           (p/add-report
            {}
            {:person/id 0}
            {:person/id 1}
            {:person/id 0 :person/name "Gill"}))))


(defrecord Thing [a b c])


(deftest records
  (is (= (->Thing "foo" "bar" "baz")
         (-> [{:id 0
               :thing (->Thing "foo" "bar" "baz")}]
             (p/db)
             (get-in [:id 0 :thing]))))
  #_(is (= (->Thing "foo" "bar" "baz")
           (-> [{:id 0
                 :thing (->Thing "foo" "bar" "baz")}]
               (p/db)
               (p/pull [{[:id 0] [:thing]}])
               (get-in [[:id 0] :thing])))
        "pulling a record returns the right type"))


(def data
  {:people/all [{:person/id 0
                 :person/name "Alice"
                 :person/age 25
                 :best-friend {:person/id 1}
                 :person/favorites
                 {:favorite/ice-cream "vanilla"}}
                {:person/id 1
                 :person/name "Bob"
                 :person/age 23}]})


(def db
  (p/db [data]))


(deftest pull
  (is (= #:people{:all [{:person/id 0} {:person/id 1}]}
         (p/pull db [:people/all]))
      "simple key")
  (is (= {:people/all [{:person/name "Alice"
                        :person/id 0}
                       {:person/name "Bob"
                        :person/id 1}]}
         (p/pull db [{:people/all [:person/name :person/id]}]))
      "basic join + prop")
  (is (= #:people{:all [{:person/name "Alice"
                         :person/id 0
                         :best-friend #:person{:name "Bob", :id 1 :age 23}}
                        #:person{:name "Bob", :id 1}]}
         (p/pull db [#:people{:all [:person/name :person/id :best-friend]}]))
      "join + prop + join ref lookup")
  (is (= #:people{:all [{:person/name "Alice"
                         :person/id 0
                         :best-friend #:person{:name "Bob"}}
                        #:person{:name "Bob", :id 1}]}
         (p/pull db [#:people{:all [:person/name
                                    :person/id
                                    {:best-friend [:person/name]}]}]))
      "join + prop, ref as prop resolver")
  (is (= {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
         (p/pull db [[:person/id 1]]))
      "ident acts as ref lookup")
  (is (= {[:person/id 0] {:person/id 0
                          :person/name "Alice"
                          :person/age 25
                          :best-friend {:person/id 1}
                          :person/favorites #:favorite{:ice-cream "vanilla"}}}
         (p/pull db [[:person/id 0]]))
      "ident does not resolve nested refs")
  (is (= {[:person/id 0] #:person{:id 0
                                  :name "Alice"
                                  :favorites #:favorite{:ice-cream "vanilla"}}}
         (p/pull db [{[:person/id 0] [:person/id
                                      :person/name
                                      :person/favorites]}]))
      "join on ident")
  (is (= {:people/all [{:person/name "Alice"
                        :person/id 0
                        :best-friend #:person{:name "Bob", :id 1 :age 23}}
                       #:person{:name "Bob", :id 1}]
          [:person/id 1] #:person{:age 23}}
         (p/pull db [{:people/all [:person/name :person/id :best-friend]}
                     {[:person/id 1] [:person/age]}]))
      "multiple joins")

  (testing "includes params"
    (is (= #:people{:all [#:person{:name "Bob", :id 1}]}
           (p/pull (-> db
                       (p/add {'(:people/all {:with "params"}) [[:person/id 1]]}))
                   '[{(:people/all {:with "params"})
                      [:person/name :person/id]}])))
    (is (= '{:person/foo {:person/id 1
                          :person/name "Bob"}}
           (p/pull (-> db
                       (p/add {'(:person/foo {:person/id 2})
                               {:person/id 1}}))
                   '[{(:person/foo {:person/id 2})
                      [:person/name :person/id]}]))
        "params that include an entity-looking thing should not be normalized")
    (is (= {}
           (p/pull db '[([:person/id 1] {:with "params"})])))
    (is (= {}
           (p/pull db '[{(:people/all {:with "params"})
                         [:person/name :person/id]}]))))

  (testing "union"
    (let [data {:foo {:bar/id 2 :asdf 123 :jkl 456 :qux 789}}
          db (p/db [data])
          query [{:foo {:bar/id [:bar/id :asdf :jkl]
                        :baz/id [:baz/id :arst :nei]}}]]
      (is (= {:foo {:bar/id 2 :asdf 123 :jkl 456}}
             (p/pull db query))))
    (let [data {:chat/entries
                [{:message/id 0
                  :message/text "foo"
                  :chat.entry/timestamp "1234"}
                 {:message/id 1
                  :message/text "bar"
                  :chat.entry/timestamp "1235"}
                 {:audio/id 0
                  :audio/url "audio://asdf.jkl"
                  :audio/duration 1234
                  :chat.entry/timestamp "4567"}
                 {:photo/id 0
                  :photo/url "photo://asdf_10x10.jkl"
                  :photo/height 10
                  :photo/width 10
                  :chat.entry/timestamp "7890"}]}
          db1 (p/db [data])
          query [{:chat/entries
                  {:message/id
                   [:message/id :message/text :chat.entry/timestamp]

                   :audio/id
                   [:audio/id :audio/url :audio/duration :chat.entry/timestamp]

                   :photo/id
                   [:photo/id :photo/url :photo/width :photo/height :chat.entry/timestamp]

                   :asdf/jkl [:asdf/jkl]}}]]
      (is (= #:chat{:entries [{:message/id 0
                               :message/text "foo"
                               :chat.entry/timestamp "1234"}
                              {:message/id 1
                               :message/text "bar"
                               :chat.entry/timestamp "1235"}
                              {:audio/id 0
                               :audio/url "audio://asdf.jkl"
                               :audio/duration 1234
                               :chat.entry/timestamp "4567"}
                              {:photo/id 0
                               :photo/url "photo://asdf_10x10.jkl"
                               :photo/width 10
                               :photo/height 10
                               :chat.entry/timestamp "7890"}]}
             (p/pull db1 query)))))

  (testing "not found"
    (is (= {} (p/pull {} [:foo])))
    (is (= {} (p/pull {} [:foo :bar :baz])))
    (is (= {} (p/pull {} [:foo {:bar [:asdf]} :baz])))

    (is (= {:foo "bar"}
           (p/pull {:foo "bar"} [:foo {:bar [:asdf]} :baz])))
    (is (= {:bar {:asdf 123}}
           (p/pull
            {:bar {:asdf 123}}
            [:foo {:bar [:asdf :jkl]} :baz])))
    (is (= {:bar {}}
           (p/pull
            (p/db [{:bar {:bar/id 0}}
                   {:bar/id 0
                    :qwerty 1234}])
            [:foo {:bar [:asdf :jkl]} :baz])))
    (is (= {:bar {:asdf "jkl"}}
           (p/pull
            (p/db [{:bar {:bar/id 0}}
                   {:bar/id 0
                    :asdf "jkl"}])
            [:foo {:bar [:asdf :jkl]} :baz])))
    (is (= {:bar {}}
           (p/pull
            (p/db [{:bar {:bar/id 0}}
                   {:bar/id 1
                    :asdf "jkl"}])
            [:foo {:bar [:asdf :jkl]} :baz])))

    (is (= {:foo [{:bar/id 1
                   :bar/name "asdf"}
                  {:baz/id 1
                   :baz/name "jkl"}]}
           (p/pull
            (p/db [{:foo [{:bar/id 1
                           :bar/name "asdf"}
                          {:baz/id 1
                           :baz/name "jkl"}]}])
            [{:foo {:bar/id [:bar/id :bar/name]
                    :baz/id [:baz/id :baz/name]}}])))

    (is (= {:foo [{:bar/id 1
                   :bar/name "asdf"}
                  {:bar/id 2}
                  {:baz/id 1
                   :baz/name "jkl"}]}
           (p/pull
            (p/db [{:foo [{:bar/id 1
                           :bar/name "asdf"}
                          {:bar/id 2}
                          {:baz/id 1
                           :baz/name "jkl"}]}])
            [{:foo {:bar/id [:bar/id :bar/name]
                    :baz/id [:baz/id :baz/name]}}]))))

  (testing "bounded recursion"
    (let [data {:entries
                {:entry/id "foo"
                 :entry/folders
                 [{:entry/id "bar"}
                  {:entry/id "baz"
                   :entry/folders
                   [{:entry/id "asdf"
                     :entry/folders
                     [{:entry/id "qwerty"}]}
                    {:entry/id "jkl"
                     :entry/folders
                     [{:entry/id "uiop"}]}]}]}}
          db (p/db [data])]
      (is (= {:entries
              {:entry/id "foo"
               :entry/folders
               []}}
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders 0}]}])))
      (is (= {:entries
              {:entry/id "foo"
               :entry/folders
               [{:entry/id "bar"}
                {:entry/id "baz"
                 :entry/folders []}]}}
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders 1}]}])))
      (is (= {:entries
              {:entry/id "foo"
               :entry/folders
               [{:entry/id "bar"}
                {:entry/id "baz"
                 :entry/folders
                 [{:entry/id "asdf"
                   :entry/folders []}
                  {:entry/id "jkl"
                   :entry/folders []}]}]}}
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders 2}]}])))
      (is (= {:entries
              {:entry/id "foo"
               :entry/folders
               [{:entry/id "bar"}
                {:entry/id "baz"
                 :entry/folders
                 [{:entry/id "asdf"
                   :entry/folders
                   [{:entry/id "qwerty"}]}
                  {:entry/id "jkl"
                   :entry/folders
                   [{:entry/id "uiop"}]}]}]}}
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders 3}]}])))
      (is (= {:entries
              {:entry/id "foo"
               :entry/folders
               [{:entry/id "bar"}
                {:entry/id "baz"
                 :entry/folders
                 [{:entry/id "asdf"
                   :entry/folders
                   [{:entry/id "qwerty"}]}
                  {:entry/id "jkl"
                   :entry/folders
                   [{:entry/id "uiop"}]}]}]}}
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders 10}]}])))))

  (testing "infinite recursion"
    (let [data {:entries
                {:entry/id "foo"
                 :entry/folders
                 [{:entry/id "bar"}
                  {:entry/id "baz"
                   :entry/folders
                   [{:entry/id "asdf"
                     :entry/folders
                     [{:entry/id "qwerty"}]}
                    {:entry/id "jkl"
                     :entry/folders
                     [{:entry/id "uiop"}]}]}]}}
          db (p/db [data])]
      (is (= data
             (p/pull db '[{:entries [:entry/id
                                     {:entry/folders ...}]}])))))

  (testing "query metadata"
    (is (-> db
            (p/pull ^:foo [])
            (meta)
            (:foo))
        "root")
    (is (-> db
            (p/pull [^:foo {[:person/id 0] [:person/name]}])
            (get [:person/id 0])
            (meta)
            (:foo))
        "join")
    (let [data {:chat/entries
                [{:message/id 0
                  :message/text "foo"
                  :chat.entry/timestamp "1234"}
                 {:message/id 1
                  :message/text "bar"
                  :chat.entry/timestamp "1235"}
                 {:audio/id 0
                  :audio/url "audio://asdf.jkl"
                  :audio/duration 1234
                  :chat.entry/timestamp "4567"}
                 {:photo/id 0
                  :photo/url "photo://asdf_10x10.jkl"
                  :photo/height 10
                  :photo/width 10
                  :chat.entry/timestamp "7890"}]}
          db1 (p/db [data])
          query ^:foo [^:bar
                       {:chat/entries
                        {:message/id
                         [:message/id :message/text :chat.entry/timestamp]

                         :audio/id
                         [:audio/id :audio/url :audio/duration :chat.entry/timestamp]

                         :photo/id
                         [:photo/id :photo/url :photo/width :photo/height :chat.entry/timestamp]

                         :asdf/jkl [:asdf/jkl]}}]
          result (p/pull db1 query)]
      (is (= #:chat{:entries [{:message/id 0
                               :message/text "foo"
                               :chat.entry/timestamp "1234"}
                              {:message/id 1
                               :message/text "bar"
                               :chat.entry/timestamp "1235"}
                              {:audio/id 0
                               :audio/url "audio://asdf.jkl"
                               :audio/duration 1234
                               :chat.entry/timestamp "4567"}
                              {:photo/id 0
                               :photo/url "photo://asdf_10x10.jkl"
                               :photo/width 10
                               :photo/height 10
                               :chat.entry/timestamp "7890"}]}
             result))
      (is (-> result meta :foo))
      (is (every? #(:bar (meta %)) (get result :chat/entries)))))
  (testing "dangling entities"
    (is (= {[:id 0] {:friends [{:id 1} {:id 2}]}}
           (p/pull
            {:id {0 {:id 0 :name "asdf" :friends [[:id 1] [:id 2]]}
                  1 {:id 1 :name "jkl"}}}
            [{[:id 0] [:friends]}]))
        "dangling entity shows up in queries that do not select any props")
    (is (= {[:id 0] {:friends [{:id 1, :name "jkl"} {:id 2}]}}
           (p/pull
            {:id {0 {:id 0 :name "asdf" :friends [[:id 1] [:id 2]]}
                  1 {:id 1 :name "jkl"}}}
            [{[:id 0] [{:friends [:id :name]}]}]))
        "dangling entity shows up in queries that include ID")
    (is (= {[:id 0] {:friends [{:name "jkl"}]}}
           (p/pull
            {:id {0 {:id 0 :name "asdf" :friends [[:id 1] [:id 2]]}
                  1 {:id 1 :name "jkl"}}}
            [{[:id 0] [{:friends [:name]}]}]))
        "dangling entity does not show up in queries that do not include ID")))


(deftest pull-report
  (is (= {:data {:people/all [{:person/name "Alice"}
                              {:person/name "Bob"}]}
          :entities #{[:person/id 0] [:person/id 1]}
          :indices #{:people/all}}
         (p/pull-report db [{:people/all [:person/name]}]))
      "basic join + prop")
  (is (= {:data #:people{:all [{:person/name "Alice"
                                :best-friend #:person{:name "Bob", :id 1 :age 23}}
                               #:person{:name "Bob"}]}
          :entities #{[:person/id 0] [:person/id 1]}
          :indices #{:people/all}}
         (p/pull-report db [#:people{:all [:person/name :best-friend]}]))
      "join + prop + join ref lookup")
  (is (= {:data {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
          :entities #{[:person/id 1]}
          :indices #{}}
         (p/pull-report db [[:person/id 1]]))
      "ident acts as ref lookup")
  (is (= {:data {[:person/id 0] {:person/id 0
                                 :person/name "Alice"
                                 :person/age 25
                                 :best-friend {:person/id 1}
                                 :person/favorites #:favorite{:ice-cream "vanilla"}}}
          :entities #{[:person/id 0]}
          :indices #{}}
         (p/pull-report db [[:person/id 0]]))
      "ident does not resolve nested refs"))


(deftest delete
  (is (= {:people/all [[:person/id 0]]
          :person/id {0 {:person/id 0
                         :person/name "Alice"
                         :person/age 25
                         :person/favorites #:favorite{:ice-cream "vanilla"}}}}
         (p/delete db [:person/id 1])))
  (is (= (-> {}
             (p/delete [:person/id 1])
             (p/add {:person/id 1 :person/name "Alice"}))
         {:person/id {1 {:person/id 1 :person/name "Alice"}}})))



(deftest data->query
  (is (= [:a]
         (p/data->query {:a 42})))
  (is (= [{:a [:b]}]
         (p/data->query {:a {:b 42}})))
  (is (= [{:a [:b :c]}]
         (p/data->query {:a [{:b 42} {:c :d}]})))
  (is (= [{[:a 42] [:b]}]
         (p/data->query {[:a 42] {:b 33}}))))

(comment
  (clojure.test/run-tests))
