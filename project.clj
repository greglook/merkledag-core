(defproject mvxcvi/merkledag-core "0.5.0-SNAPSHOT"
  :description "Graph datastore built on content-addressed merkle hash links"
  :url "http://github.com/greglook/merkledag-core"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :aliases
  {"coverage" ["with-profile" "+coverage" "cloverage"]}

  :deploy-branches ["master"]
  :pedantic? :warn

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/core.cache "0.8.2"]
   [org.clojure/tools.logging "0.5.0"]

   [manifold "0.1.8"]
   ;[byte-streams "0.2.4"]
   [mvxcvi/blocks "2.0.3"]
   [mvxcvi/clj-cbor "0.7.2"]
   [mvxcvi/multiformats "0.2.1"]

   ;; Conflict resolution
   [org.clojure/data.priority-map "0.0.10"]]

  :test-selectors
  {:default (complement :integration)
   :integration :integration}

  :hiera
  {:vertical false
   :cluster-depth 2
   :ignore-ns #{clojure clj-cbor puget}
   :show-external true}

  :whidbey
  {:tag-types
   {'blocks.data.Block {'blocks.data.Block (partial into {})}
    'java.time.Instant {'inst str}
    'merkledag.link.LinkIndex {'merkledag.link/index :index}
    'merkledag.link.MerkleLink {'merkledag/link 'merkledag.link/link->form}
    'multiformats.hash.Multihash {'data/hash str}}}

  :profiles
  {:dev
   {:dependencies
    [[commons-logging "1.2"]
     [mvxcvi/test.carly "0.4.1"]
     [mvxcvi/puget "1.2.0"]]}

   :repl
   {:source-paths ["dev"]
    :repl-options {:init-ns merkledag.repl}
    :dependencies
    [[org.clojure/tools.namespace "0.3.1"]
     [rhizome "0.2.9"]]}

   :test
   {:jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog"]}

   :coverage
   {:plugins [[lein-cloverage "1.1.2"]]
    :jvm-opts ["-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog"
               "-Dorg.apache.commons.logging.simplelog.defaultlog=trace"]}})
