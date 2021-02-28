(defproject lilactown/autonormal "1.0.3"
  :description "A library for storing and querying graph data in a Clojure map"
  :url "https://github.com/lilactown/autonormal"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v20.html"}
  :source-paths ["src"]
  :dependencies [[edn-query-language/eql "1.0.1"]]
  :deploy-repositories [["snapshots" {:sign-releases false :url "https://clojars.org"}]])
