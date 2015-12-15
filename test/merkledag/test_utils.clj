(ns merkledag.test-utils
  (:require
    [merkledag.graph :as merkle]
    [merkledag.codec.edn :as edn]
    [merkledag.data :as data]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.link.MerkleLink))


(defn random-bytes
  [length]
  (let [data (byte-array length)]
    (.nextBytes (java.security.SecureRandom.) data)
    data))


(def print-opts
  {:print-handlers
   (dispatch/chained-lookup
     {MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))}
     (edn/types->print-handlers data/edn-types)
     puget/common-handlers)})


(defmacro dprint
  [v]
  `(do
     (print "DEBUG: ")
     (puget/cprint (quote ~v) print-opts)
     (puget/cprint ~v print-opts)))
