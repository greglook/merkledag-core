(defproject mvxcvi/merkledag-repo "0.1.0-SNAPSHOT"
  :description "Merkle-DAG object repository"
  :url "http://github.com/greglook/clj-mdag-repo"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :plugins
  [[lein-protobuf "0.4.3"]]

  :dependencies
  [[clj-time "0.11.0"]
   [mvxcvi/blobble "0.1.0-SNAPSHOT"]
   [mvxcvi/multihash "0.3.0-SNAPSHOT"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/tools.logging "0.3.1"]
   [org.flatland/protobuf "0.8.1"]])
