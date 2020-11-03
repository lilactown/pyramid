(ns autonormal.core-test
  (:require
   [autonormal.core :as a]
   [clojure.test :as t]))


(t/deftest normalization
  (t/is (= {::a/schema a/default-schema
            :person/id {0 {:person/id 0}}}
           (a/db [{:person/id 0}]))
        "a single entity")
  (t/is (= {::a/schema a/default-schema
            :person/id {0 {:person/id 0
                           :person/name "asdf"}
                        1 {:person/id 1
                           :person/name "jkl"}}}
           (a/db [{:person/id 0
                   :person/name "asdf"}
                  {:person/id 1
                   :person/name "jkl"}]))
        "multiple entities with attributes")
  (t/is (= {::a/schema a/default-schema
            :person/id {0 {:person/id 0
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
  (t/is (= {::a/schema a/default-schema
            :person/id
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


(t/deftest schema
  (t/is (= {::a/schema #{:color}
            :color {"red" {:color "red" :hex "#ff0000"}}}
           (a/db [{:color "red" :hex "#ff0000"}]
                 #{:color}))))

(def data
  {:people/all [{:person/id 0
                 :person/name "Alice"
                 :person/age 17
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
                            :person/age 17
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

  (t/testing "ignores params"
    (t/is (= {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
             (a/pull db '[([:person/id 1] {:with "params"})])))
    (t/is (= #:people{:all [#:person{:name "Alice"
                                     :id 0}
                            #:person{:name "Bob", :id 1}]}
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
              {::a/schema a/default-schema
               :bar {:asdf 123}}
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
              [:foo {:bar [:asdf :jkl]} :baz]))))

  (t/testing "recursion"
    (let [data {:entries
                {:entry/name "foo"
                 :entry/folders
                 [{:entry/name "bar"}
                  {:entry/name "baz"
                   :entry/folders
                   [{:entry/name "asdf"
                     :entry/folders
                     [{:entry/name "qwerty"}]}
                    {:entry/name "jkl"
                     :entry/folders
                     [{:entry/name "uiop"}]}]}]} }
          db (a/db [data] #{:entry/name})]
      (t/is (= data
               (a/pull db '[{:entries [:entry/name
                                       {:entry/folders ...}]}]))))))
