(ns user
  (:require
    [blocks.core :as block]
    (clj-time
      [core :as time]
      [coerce :as coerce-time]
      [format :as format-time])
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [merkledag.graph :as merkle]
    [multihash.core :as multihash]
    [puget.dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(def print-options
  {:print-color true
   :print-handlers
   (puget.dispatch/chained-lookup
     {Multihash (puget/tagged-handler 'data/hash multihash/base58)
      MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))}
     puget/common-handlers)})


(def graph
  (merkle/graph-repo))
