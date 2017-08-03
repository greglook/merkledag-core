(defproject mvxcvi/merkledag-core "0.3.0"
  :description "Graph datastore built on content-addressed merkle hash links"
  :url "http://github.com/greglook/merkledag-core"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :aliases
  {"coverage" ["with-profile" "+test,+coverage" "cloverage"]}

  :deploy-branches ["master"]
  :pedantic? :abort

  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [org.clojure/core.cache "0.6.5"]
   [org.clojure/tools.logging "0.4.0"]
   [clojure-future-spec "1.9.0-alpha14"]
   [byte-streams "0.2.3"]
   [mvxcvi/blocks "0.9.1"]
   [mvxcvi/clj-cbor "0.4.1"]
   [mvxcvi/multicodec "0.5.1"]
   [mvxcvi/multihash "2.0.1"]
   [mvxcvi/puget "1.0.1"]]

  :test-selectors
  {:unit (complement :integration)
   :integration :integration}

  :hiera
  {:vertical false
   :cluster-depth 2
   :ignore-ns #{clojure clj-cbor puget}
   :show-external true}

  :codox
  {:metadata {:doc/format :markdown}
   :source-uri "https://github.com/greglook/merkledag-core/blob/master/{filepath}#L{line}"
   :output-path "target/doc/api"}

  :whidbey
  {:tag-types
   {'blocks.data.Block {'blocks.data.Block (partial into {})}
    'java.time.Instant {'inst str}
    'merkledag.link.LinkIndex {'merkledag.link/index :index}
    'merkledag.link.MerkleLink {'merkledag/link 'merkledag.link/link->form}
    'multihash.core.Multihash {'data/hash 'multihash.core/base58}}}

  :profiles
  {:dev
   {:dependencies
    [[commons-logging "1.2"]
     [mvxcvi/test.carly "0.3.0"]]}

   :repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.2.11"]
     [rhizome "0.2.7"]]}

   :test
   {:jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog"]}

   :coverage
   {:plugins [[lein-cloverage "1.0.9"]]
    :dependencies [[riddley "0.1.14"]]
    :jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog"
               "-Dorg.apache.commons.logging.simplelog.defaultlog=trace"]}})
