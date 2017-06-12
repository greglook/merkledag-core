(ns user
  (:require
    [blocks.core :as block]
    (blocks.store
      [file :refer [file-block-store]]
      [memory :refer [memory-block-store]])
    [byte-streams :as bytes]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.tools.namespace.repl :refer [refresh]]
    (merkledag
      [core :as mdag]
      [link :as link]
      [node :as node])
    [multicodec.core :as codec]
    [multihash.core :as multihash]
    [puget.printer :as puget]))


(def graph
  (mdag/init-store
    ; TODO: codec that can read both cbor and edn
    :store (file-block-store "dev/data/blocks")
    :cache {:total-size-limit (* 16 1024)}))


#_
(defn init!
  []
  (let [context-hash (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh")
        node-1 (merkle/node
                 {:data/type :finance/posting
                  :uuid "foo-bar"})
        node-2 (merkle/node
                 {:data/type :finance/posting
                  :uuid "frobblenitz omnibus"})
        node-3 (merkle/node
                 [(merkle/link "@context" context-hash)]
                 {:data/type :finance/transaction
                  :uuid #uuid "31f7dd72-c7f7-4a15-a98b-0f9248d3aaa6"
                  :title "Gas Station"
                  :description "Bought a pack of gum."
                  :time #inst "2013-10-08T00:00:00"
                  :entries [(merkle/link "posting-1" node-1)
                            (merkle/link "posting-2" node-2)]})]
    [node-1 node-2 node-3]))
