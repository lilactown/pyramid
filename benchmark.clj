(ns benchmark
  (:require
   [pyramid.core :as p]
   [criterium.core :as c]
   [clj-async-profiler.core :as prof]))


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
(defn create [k depth]
  (if (zero? depth)
    (k {:id depth})
    #(create
      (fn [c]
        (fn [] (k {:id depth :child c})))
      (dec depth))))


(def really-nested-data
  {:foo (trampoline create identity 50000) })


(update-in really-nested-data
           [:foo :child :child :child :child]
           dissoc :child)
;; => {:foo {:id 50000, :child {:id 49999, :child {:id 49998, :child {:id 49997, :child {:id 49996}}}}}}


(do (p/db [really-nested-data])
    nil)

(c/quick-bench (p/db [really-nested-data]))

