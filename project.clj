(defproject mvxcvi/merkledag "0.2.0-SNAPSHOT"
  :description "Graph datastore built on content-addressed merkle hash links"
  :url "http://github.com/greglook/clj-mdag-repo"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :aliases
  {"coverage" ["with-profile" "+test,+coverage" "cloverage"]}

  :deploy-branches ["master"]
  :pedantic? :abort

  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [org.clojure/core.cache "0.6.5"]
   [org.clojure/tools.logging "0.3.1"]
   [clojure-future-spec "1.9.0-alpha14"]
   [byte-streams "0.2.2"]
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
   :ignore-ns #{clojure}
   :show-external false}

  :whidbey
  {:tag-types
   {'blocks.data.Block {'blocks.data.Block (partial into {})}
    'java.time.Instant {'inst str}
    'merkledag.link.LinkIndex {'data/link-index :index}
    'merkledag.link.MerkleLink {'data/link 'merkledag.link/link->form}
    'multihash.core.Multihash {'data/hash 'multihash.core/base58}}}

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.2.11"]
     [rhizome "0.2.7"]]}

   :test
   {:dependencies
    [[commons-logging "1.2"]
     [mvxcvi/test.carly "0.1.0"]]
    :jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog"]}

   :coverage
   {:plugins [[lein-cloverage "1.0.6"]]
    :jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog"
               "-Dorg.apache.commons.logging.simplelog.defaultlog=trace"]}})
