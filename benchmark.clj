(ns benchmark
  (:require
   [pyramid.core :as p]
   [criterium.core :as c]
   [clj-async-profiler.core :as prof]))


(prof/serve-files 8080)


(prof/profile (dotimes [i 10000]
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
                        :best-friend {:person/id 123}}])))


(c/quick-bench
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


(def big-data
  [{:foo/data
   (vec
    (for [i (range 1000)]
      {:foo/id (str "id" i)
       :foo/name (str "bar" i)
       :foo/metadata {:some ["dumb" "data"]}})) } ])


(c/quick-bench (p/db big-data))


(prof/profile
 (dotimes [i 1000]
   (p/db big-data)))


;; this throws w/ StackOverflow on my computer when running p/db
;; numbers 10000 and up throw when generating the tree
(def limit 7946)

(def nested-data
  (letfn [(create [i]
            (if (zero? i)
              nil
              {:id i
               :child (create (dec i))}))]
    {:foo (create limit) }))


(do (p/db [nested-data])
    nil)


(c/quick-bench (p/db [nested-data]))


;; testing out different trampoline strategies
(defn create [k i]
  (if (zero? i)
    (k)
    (fn []
      (create
       (fn []
         {:id i
          :child (k)})
       (dec i)))))


(def really-nested-data
  {:foo (trampoline create (constantly {:id 0}) 50000) })


(do (p/db [really-nested-data])
    nil)

(c/quick-bench (p/db [really-nested-data]))


(prof/profile (dotimes [i 1000]
                (p/db [really-nested-data])))



(def ppl-db
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


(p/pull ppl-db [{[:person/id 123] [:person/id
                                   :person/name
                                   {:friends [:person/id :person/name]}]}])

(def query [{[:person/id 123] [:person/id
                               :person/name
                               {:friends [:person/id :person/name]}]}])

(c/quick-bench
 (p/pull ppl-db query))

(prof/profile (dotimes [i 1000]
                (p/pull ppl-db query)))
