(ns merkledag.test-utils
  (:require
    [merkledag.graph :as merkle]
    [merkledag.edn :as edn]
    [merkledag.data :as data]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.graph.MerkleLink))


(def print-opts
  {:print-handlers
   (dispatch/chained-lookup
     {MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))}
     (edn/types->print-handlers data/core-types)
     puget/common-handlers)})


(defmacro dprint
  [v]
  `(do
     (print "DEBUG: ")
     (puget/cprint (quote ~v) print-opts)
     (puget/cprint ~v print-opts)))
