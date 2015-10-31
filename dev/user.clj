(ns user
  (:require
    [blobble.core :as blob]
    [blobble.store.memory :refer [memory-store]]
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
    [merkledag.types :as merkle-types]
    [multihash.core :as multihash]
    [puget.dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.graph.MerkleLink
    multihash.core.Multihash))


(def print-options
  {:print-handlers
   (puget.dispatch/chained-lookup
     {Multihash (puget/tagged-handler 'data/hash multihash/base58)
      MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))}
     puget/common-handlers)})


(def graph-repo
  {:types merkle-types/core-types
   :store (memory-store)})
