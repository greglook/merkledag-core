(ns merkledag.core-test
  (:require
    [byte-streams :as bytes]
    [clojure.test :refer :all]
    [merkledag.core :as merkle]
    [merkledag.types :refer [core-types]]
    [multihash.core :as multihash]
    [puget.printer :as puget])
  (:import
    merkledag.core.MerkleLink
    multihash.core.Multihash))


(def print-options
  {:print-handlers
   {Multihash (puget/tagged-handler 'data/hash multihash/base58)
    MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))}})


(deftest a-test
  (merkle/with-repo {:types core-types}
    (let [context (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh")
          node-1 (merkle/node
                   {:type :finance/posting
                    :uuid "foo-bar"})
          node-2 (merkle/node
                   {:type :finance/posting
                    :uuid "baz-qux"})
          node-3 (merkle/node
                   [(merkle/link "@context" context)]
                   {:type :finance/transaction
                    :title "SCHZ - Reinvest Dividend"
                    :description "Automatic dividend reinvestment."
                    :time #inst "2013-10-08T00:00:00Z"
                    :entries [(merkle/link "posting-1" node-1)
                              (merkle/link "posting-2" node-2)]})]
      (puget/cprint node-3 print-options)
      (bytes/print-bytes (:content node-3))
      (is (instance? multihash.core.Multihash (:id node-3)))
      )))
