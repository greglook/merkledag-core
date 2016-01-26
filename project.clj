(defproject mvxcvi/merkledag "0.2.0-SNAPSHOT"
  :description "Graph datastore built on content-addressed merkle hash links"
  :url "http://github.com/greglook/clj-mdag-repo"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :deploy-branches ["master"]

  :plugins
  [[lein-protobuf "0.4.3"]]

  :dependencies
  [[byte-streams "0.2.0"]
   [clj-time "0.11.0"]
   [mvxcvi/blocks "0.6.1"]
   [mvxcvi/multicodec "0.5.0"]
   [mvxcvi/multihash "1.1.0"]
   [mvxcvi/puget "1.0.0"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/tools.logging "0.3.1"]
   [org.flatland/protobuf "0.8.1"]
   [rhizome "0.2.5"]]

  :hiera
  {:vertical false
   :cluster-depth 2
   :ignore-ns #{clojure}
   :show-external false}

  :whidbey
  {:tag-types {'blocks.data.Block {'blocks.data.Block (partial into {})}
               'merkledag.link.MerkleLink {'data/link (juxt :name :target :tsize)}
               'multihash.core.Multihash {'data/hash 'multihash.core/base58}
               'org.joda.time.DateTime {'inst str}}}

  :profiles
  {:repl {:source-paths ["dev"]
          :dependencies [[org.clojure/tools.namespace "0.2.11"]]}})
