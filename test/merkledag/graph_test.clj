(ns merkledag.graph-test
  (:require
    [blobble.core :as blob]
    [blobble.store.memory :refer [memory-store]]
    [byte-streams :as bytes]
    [clojure.test :refer :all]
    [merkledag.graph :as merkle]
    [merkledag.types :refer [core-types]]
    [multihash.core :as multihash]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    merkledag.graph.MerkleLink
    multihash.core.Multihash))


(def print-options
  {:print-handlers
   (dispatch/chained-lookup
     {Multihash (puget/tagged-handler 'data/hash multihash/base58)
      MerkleLink (puget/tagged-handler 'data/link :name #_(juxt :name :target :tsize))}
     puget/common-handlers)})


(def hash-1 (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh"))
(def hash-2 (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh"))


(deftest a-test
  (let [store (memory-store)]
    (merkle/with-repo {:types core-types, :store store}
      (testing "basic node properties"
        (let [node (merkle/node
                     [(merkle/link "@context" context-hash)]
                     {:type :finance/posting
                      :uuid "foo-bar"})]
          (is (instance? Multihash (:id node)))
          (is (instance? java.nio.ByteBuffer (:content node)))
          (is (vector? (:links node)))
          (is (= 1 (count (:links node))))
          (is (every? (partial instance? MerkleLink) (:links node)))
          (is (map? (:data node)))
          (is (empty? (blob/list store))
              "node creation should not store any blobs")))
      (let [node-1 (merkle/node
                     {:type :finance/posting
                      :uuid "foo-bar"})
            node-2 (merkle/node
                     {:type :finance/posting
                      :uuid "baz-qux"})
            node-3 (merkle/node
                     [(merkle/link "@context" context-hash)]
                     {:type :finance/transaction
                      :title "SCHZ - Reinvest Dividend"
                      :description "Automatic dividend reinvestment."
                      :time #inst "2013-10-08T00:00:00Z"
                      :entries [(merkle/link "posting-1" node-1)
                                (merkle/link "posting-2" node-2)]})]
        ;(puget/cprint node-3 print-options)
        ;(bytes/print-bytes (:content node-3))
        ))))
