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
