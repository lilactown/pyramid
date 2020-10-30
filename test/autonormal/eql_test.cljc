(ns autonormal.eql-test
  (:require
   [autonormal.core :as a]
   [autonormal.eql :as a.eql]
   [clojure.test :as t]))

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
  (t/is (= {:people/all [{:person/name "Alice"
                          :person/id 0}
                         {:person/name "Bob"
                          :person/id 1}]}
           (a.eql/pull db [{:people/all [:person/name :person/id]}]))
        "basic join + prop")
  (t/is (= #:people{:all [{:person/name "Alice"
                           :person/id 0
                           :best-friend #:person{:name "Bob", :id 1 :age 23}}
                          #:person{:name "Bob", :id 1}]}
           (a.eql/pull db [#:people{:all [:person/name :person/id :best-friend]}]))
        "join + prop + join ref lookup")
  (t/is (= #:people{:all [{:person/name "Alice"
                           :person/id 0
                           :best-friend #:person{:name "Bob"}}
                          #:person{:name "Bob", :id 1}]}
           (a.eql/pull db [#:people{:all [:person/name
                                          :person/id
                                          {:best-friend [:person/name]}]}]))
        "join + prop, ref as prop does not lookup")
  (t/is (= {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
           (a.eql/pull db [[:person/id 1]]))
        "ident acts as ref lookup")
  (t/is (= {[:person/id 0] #:person{:id 0
                                    :name "Alice"
                                    :favorites #:favorite{:ice-cream "vanilla"}}}
           (a.eql/pull db [{[:person/id 0] [:person/id
                                            :person/name
                                            :person/favorites]}]))
        "join on ident")
  (t/is (= {:people/all [{:person/name "Alice"
                          :person/id 0
                          :best-friend #:person{:name "Bob", :id 1 :age 23}}
                         #:person{:name "Bob", :id 1}]
            [:person/id 1] #:person{:age 23}}
           (a.eql/pull db [{:people/all [:person/name :person/id :best-friend]}
                           {[:person/id 1] [:person/age]}]))
        "multiple joins")

  (t/testing "ignores params"
    (t/is (= {[:person/id 1] #:person{:id 1, :name "Bob", :age 23}}
             (a.eql/pull db '[([:person/id 1] {:with "params"})])))
    (t/is (= #:people{:all [#:person{:name "Alice"
                                     :id 0}
                            #:person{:name "Bob", :id 1}]}
             (a.eql/pull db '[{(:people/all {:with "params"})
                               [:person/name :person/id]}]))))

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

                 :asdf/jkl [:asdf/jkl]
                 }}]]
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
             (a.eql/pull db1 query)))))
