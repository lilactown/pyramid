(ns autonormal.core-test
  (:require
   [autonormal.core :as a]
   [autonormal.ident :as ident]
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
                             :person/name "jkl"}]}]))
        "nested under a key")
  (t/is (= {:person/id {0 {:person/id 0
                           :some-data {1 "hello"
                                       3 "world"}}}}
           (a/db [{:person/id 0
                   :some-data {1 "hello"
                               3 "world"}}]))
    "Map with numbers as keys")
  (t/is (= {:person/id
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


(t/deftest non-entities
  (t/is (= {:foo ["bar"]} (a/db [{:foo ["bar"]}])))
  (t/is (= {:person/id {0 {:person/id 0
                           :foo ["bar"]}}}
           (a/db [{:person/id 0
                   :foo ["bar"]}]))))


(t/deftest custom-schema
  (t/is (= {:color {"red" {:color "red" :hex "#ff0000"}}}
           (a/db [{:color "red" :hex "#ff0000"}]
                 (ident/by-keys :color)))
        "ident/by-keys")
  (t/is (= {:color {"red" {:color "red" :hex "#ff0000"}}}
           (a/db [^{:db/ident :color}
                  {:color "red" :hex "#ff0000"}]))
        "local schema")
  (t/testing "complex schema"
    (let [db (a/db [{:type "person"
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
      (t/is (= {:person/id
                {"1234" {:type "person", :id "1234", :purchases [[:item/id "1234"]]}},
                :item/id
                {"1234" {:type "item", :id "1234"}, "5678" {:type "item", :id "5678"}},
                :type "foo",
                :id "bar"}
               db)
            "correctly identifies entities")
      (t/is (= {[:person/id "1234"]
                {:type "person", :id "1234", :purchases [{:type "item", :id "1234"}]}}
               (a/pull db [{[:person/id "1234"] [:type :id {:purchases [:type :id]}]}]))
            "pull"))))


(t/deftest add
  (t/is (= {:person/id {0 {:person/id 0}}}
           (a/add {} {:person/id 0})))
  (t/is (= {:person/id {0 {:person/id 0 :person/name "Gill"}
                        1 {:person/id 1}}}
           (a/add
            {}
            {:person/id 0}
            {:person/id 1}
            {:person/id 0 :person/name "Gill"}))))


(t/deftest add-report
  (t/is (= {:db {:person/id {0 {:person/id 0}}}
            :entities #{[:person/id 0]}}
           (a/add-report {} {:person/id 0})))
  (t/is (= {:db {:person/id {0 {:person/id 0
                                :person/name "Gill"
                                :best-friend [:person/id 1]}
                             1 {:person/id 1
                                :person/name "Uma"}}
                 :me [:person/id 0]}
            :entities #{[:person/id 0]
                        [:person/id 1]}}
           (a/add-report {} {:me {:person/id 0
                                  :person/name "Gill"
                                  :best-friend {:person/id 1
                                                :person/name "Uma"}}})))
  #_(t/is (= {:db {:person/id {0 {:person/id 0 :person/name "Gill"}
                               1 {:person/id 1}}}
              :entities #{{:person/id 0 :person/name "Gill"}
                          {:person/id 1}}}
           (a/add-report
            {}
            {:person/id 0}
            {:person/id 1}
            {:person/id 0 :person/name "Gill"}))))


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
  (a/db [data]))


(t/deftest pull
  (t/is (= #:people{:all [{:person/id 0} {:person/id 1}]}
           (a/pull db [:people/all]))
        "simple key")
  (t/is (= {:people/all [{:person/name "Alice"
                          :person/id 0}
                         {:person/name "Bob"
                          :person/id 1}]}
           (a/pull db [{:people/all [:person/name :person/id]}]))
        "basic join + prop")
  (t/is (= #:people{:all [{:person/name "Alice"
                           :person/id 0
                           :best-friend #:person{:name "Bob", :id 1 :age 23}}
                          #:person{:name "Bob", :id 1}]}
           (a/pull db [#:people{:all [:person/name :person/id :best-friend]}]))
        "join + prop + join ref lookup")
  (t/is (= #:people{:all [{:person/name "Alice"
                           :person/id 0
                           :best-friend #:person{:name "Bob"}}
                          #:person{:name "Bob", :id 1}]}
           (a/pull db [#:people{:all [:person/name
                                      :person/id
                                      {:best-friend [:person/name]}]}]))
        "join + prop, ref as prop resolver")
  (t/is (= {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
           (a/pull db [[:person/id 1]]))
        "ident acts as ref lookup")
  (t/is (= {[:person/id 0] {:person/id 0
                            :person/name "Alice"
                            :person/age 25
                            :best-friend {:person/id 1}
                            :person/favorites #:favorite{:ice-cream "vanilla"}}}
           (a/pull db [[:person/id 0]]))
        "ident does not resolve nested refs")
  (t/is (= {[:person/id 0] #:person{:id 0
                                    :name "Alice"
                                    :favorites #:favorite{:ice-cream "vanilla"}}}
           (a/pull db [{[:person/id 0] [:person/id
                                        :person/name
                                        :person/favorites]}]))
        "join on ident")
  (t/is (= {:people/all [{:person/name "Alice"
                          :person/id 0
                          :best-friend #:person{:name "Bob", :id 1 :age 23}}
                         #:person{:name "Bob", :id 1}]
            [:person/id 1] #:person{:age 23}}
           (a/pull db [{:people/all [:person/name :person/id :best-friend]}
                       {[:person/id 1] [:person/age]}]))
        "multiple joins")

  (t/testing "includes params"
    (t/is (= #:people{:all [#:person{:name "Bob", :id 1}]}
             (a/pull (assoc db '(:people/all {:with "params"})
                            [[:person/id 1]]) ;; just bob
                     '[{(:people/all {:with "params"})
                        [:person/name :person/id]}])))
    (t/is (= {}
             (a/pull db '[([:person/id 1] {:with "params"})])))
    (t/is (= {}
             (a/pull db '[{(:people/all {:with "params"})
                           [:person/name :person/id]}]))))

  (t/testing "union"
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
          db1 (a/db [data])
          query [{:chat/entries
                  {:message/id
                   [:message/id :message/text :chat.entry/timestamp]

                   :audio/id
                   [:audio/id :audio/url :audio/duration :chat.entry/timestamp]

                   :photo/id
                   [:photo/id :photo/url :photo/width :photo/height :chat.entry/timestamp]

                   :asdf/jkl [:asdf/jkl]}}]]
      (t/is (= #:chat{:entries [{:message/id 0
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
               (a/pull db1 query)))))


  (t/testing "not found"
    (t/is (= {} (a/pull {} [:foo])))
    (t/is (= {} (a/pull {} [:foo :bar :baz])))
    (t/is (= {} (a/pull {} [:foo {:bar [:asdf]} :baz])))

    (t/is (= {:foo "bar"}
             (a/pull {:foo "bar"} [:foo {:bar [:asdf]} :baz])))
    (t/is (= {:bar {:asdf 123}}
             (a/pull
              {:bar {:asdf 123}}
              [:foo {:bar [:asdf :jkl]} :baz])))
    (t/is (= {:bar {}}
             (a/pull
              (a/db [{:bar {:bar/id 0}}
                     {:bar/id 0
                      :qwerty 1234}])
              [:foo {:bar [:asdf :jkl]} :baz])))
    (t/is (= {:bar {:asdf "jkl"}}
             (a/pull
              (a/db [{:bar {:bar/id 0}}
                     {:bar/id 0
                      :asdf "jkl"}])
              [:foo {:bar [:asdf :jkl]} :baz])))
    (t/is (= {:bar {}}
             (a/pull
              (a/db [{:bar {:bar/id 0}}
                     {:bar/id 1
                      :asdf "jkl"}])
              [:foo {:bar [:asdf :jkl]} :baz])))

    (t/is (= {:foo [{:bar/id 1
                     :bar/name "asdf"}
                    {:baz/id 1
                     :baz/name "jkl"}]}
             (a/pull
              (a/db [{:foo [{:bar/id 1
                             :bar/name "asdf"}
                            {:baz/id 1
                             :baz/name "jkl"}]}])
              [{:foo {:bar/id [:bar/id :bar/name]
                      :baz/id [:baz/id :baz/name]}}])))

    (t/is (= {:foo [{:bar/id 1
                     :bar/name "asdf"}
                    {:bar/id 2}
                    {:baz/id 1
                     :baz/name "jkl"}]}
             (a/pull
              (a/db [{:foo [{:bar/id 1
                             :bar/name "asdf"}
                            {:bar/id 2}
                            {:baz/id 1
                             :baz/name "jkl"}]}])
              [{:foo {:bar/id [:bar/id :bar/name]
                      :baz/id [:baz/id :baz/name]}}]))))

  (t/testing "bounded recursion"
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
          db (a/db [data])]
      (t/is (= {:entries
                {:entry/id "foo"
                 :entry/folders
                 []}}
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders 0}]}])))
      (t/is (= {:entries
                {:entry/id "foo"
                 :entry/folders
                 [{:entry/id "bar"}
                  {:entry/id "baz"
                   :entry/folders []}]}}
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders 1}]}])))
      (t/is (= {:entries
                {:entry/id "foo"
                 :entry/folders
                 [{:entry/id "bar"}
                  {:entry/id "baz"
                   :entry/folders
                   [{:entry/id "asdf"
                     :entry/folders []}
                    {:entry/id "jkl"
                     :entry/folders []}]}]}}
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders 2}]}])))
      (t/is (= {:entries
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
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders 3}]}])))
      (t/is (= {:entries
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
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders 10}]}])))))

  (t/testing "infinite recursion"
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
          db (a/db [data])]
      (t/is (= data
               (a/pull db '[{:entries [:entry/id
                                       {:entry/folders ...}]}])))))

  (t/testing "query metadata"
    (t/is (-> db
              (a/pull ^:foo [])
              (meta)
              (:foo))
          "root")
    (t/is (-> db
              (a/pull [^:foo {[:person/id 0] [:person/name]}])
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
          db1 (a/db [data])
          query ^:foo [^:bar
                       {:chat/entries
                        {:message/id
                         [:message/id :message/text :chat.entry/timestamp]

                         :audio/id
                         [:audio/id :audio/url :audio/duration :chat.entry/timestamp]

                         :photo/id
                         [:photo/id :photo/url :photo/width :photo/height :chat.entry/timestamp]

                         :asdf/jkl [:asdf/jkl]}}]
          result (a/pull db1 query)]
      (t/is (= #:chat{:entries [{:message/id 0
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
      (t/is (-> result meta :foo))
      (t/is (every? #(:bar (meta %)) (get result :chat/entries))))))


(t/deftest pull-report
  (t/is (= {:data {:people/all [{:person/name "Alice"}
                                {:person/name "Bob"}]}
            :entities #{[:person/id 0] [:person/id 1]}}
           (a/pull-report db [{:people/all [:person/name]}]))
        "basic join + prop")
  (t/is (= {:data #:people{:all [{:person/name "Alice"
                                  :best-friend #:person{:name "Bob", :id 1 :age 23}}
                                 #:person{:name "Bob"}]}
            :entities #{[:person/id 0] [:person/id 1]}}
           (a/pull-report db [#:people{:all [:person/name :best-friend]}]))
        "join + prop + join ref lookup")
  (t/is (= {:data {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
            :entities #{[:person/id 1]}}
           (a/pull-report db [[:person/id 1]]))
        "ident acts as ref lookup")
  (t/is (= {:data {[:person/id 0] {:person/id 0
                                   :person/name "Alice"
                                   :person/age 25
                                   :best-friend {:person/id 1}
                                   :person/favorites #:favorite{:ice-cream "vanilla"}}}
            :entities #{[:person/id 0]}}
           (a/pull-report db [[:person/id 0]]))
        "ident does not resolve nested refs"))


(t/deftest delete
  (t/is (= {:people/all [[:person/id 0]]
            :person/id {0 {:person/id 0
                           :person/name "Alice"
                           :person/age 25
                           :person/favorites #:favorite{:ice-cream "vanilla"}}}}
           (a/delete db [:person/id 1]))))


(t/deftest data->query
  (t/is (= [:a]
           (a/data->query {:a 42})))
  (t/is (= [{:a [:b]}]
           (a/data->query {:a {:b 42}})))
  (t/is (= [{:a [:b :c]}]
           (a/data->query {:a [{:b 42} {:c :d}]})))
  (t/is (= [{[:a 42] [:b]}]
           (a/data->query {[:a 42] {:b 33}}))))
