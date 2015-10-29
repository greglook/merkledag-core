(ns merkledag.core-test
  (:require
    [clojure.test :refer :all]
    [merkledag.core :as merkle]
    [multihash.core :as multihash]))


(deftest a-test
  (let [context (multihash/decode "10520850258...")
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
    (is (instance? merkledag.core.MerkleNode node-3))
    ))
