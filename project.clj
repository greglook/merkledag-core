(defproject mvxcvi/clj-mdag-repo "0.1.0-SNAPSHOT"
  :description "Merkle-DAG object repository"
  :url "http://github.com/greglook/clj-mdag-repo"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :plugins
  [[lein-protobuf "0.4.3"]]

  :dependencies
  [[org.clojure/clojure "1.7.0"]
   [mvxcvi/multihash "0.2.0-SNAPSHOT"]
   [org.clojure/tools.logging "0.3.1"]
   [protobuf "0.6.2"]])
