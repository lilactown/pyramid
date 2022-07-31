(ns bb-test-runner
  (:require
   [clojure.test :as t]
   [pyramid.core-test]
   [pyramid.pull-test]
   [pyramid.query-test]))

(defn run-tests
  [& _args]
  (let [{:keys [fail error]}
        (t/run-tests
         'pyramid.core-test
         'pyramid.pull-test
         'pyramid.query-test)]
    (when (or (pos? fail)
              (pos? error))
      (System/exit 1))))
