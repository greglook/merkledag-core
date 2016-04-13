(ns merkledag.test-utils
  (:require
    [merkledag.codecs.edn :as edn]
    [merkledag.data :as data]
    [multihash.core :as multihash]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(defn random-bytes
  [length]
  (let [data (byte-array length)]
    (.nextBytes (java.security.SecureRandom.) data)
    data))


(def dprint-opts
  {:print-color true
   :print-handlers
   (dispatch/chained-lookup
     {MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))
      Multihash (puget/tagged-handler 'data/hash multihash/base58)}
     (edn/types->print-handlers data/core-types)
     puget/common-handlers)})


(defmacro dprint
  [v]
  `(do
     (print "DEBUG: ")
     (puget/pprint (quote ~v) dprint-opts)
     (puget/pprint ~v dprint-opts)))
