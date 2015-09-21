(ns merkledag.proto
  "Protobuffer serialization for MerkleDAG objects."
  (require
    [flatland.protobuf.core :as pb])
  (import
    (com.google.protobuf
      ByteString)
    (merkledag.proto
      Merkledag$MerkleLink
      Merkledag$MerkleNode)))


(def LinkEncoding (pb/protodef Merkledag$MerkleLink))
(def NodeEncoding (pb/protodef Merkledag$MerkleNode))


; Build a datastructure from links:
#_
^{:merkledag/links [(merkledag/link "@context" (multihash/decode "10a8b72e1ff20e..."))]}
{:type :finance/transaction
 :title "SCHZ - Reinvest Dividend"
 :description "Automatic dividend reinvestment."
 :time/at #inst "2013-10-08T00:00:00Z"
 :finance.transaction/entries
 [(merkledag/link "posting-1"
                  {:type :finance/posting
                   :title ... })
  (merkledag/link "posting-2"
                  {:type :finance/posting
                   :title ... })]
 :finance.transaction/state :finance.transaction.state/cleared}


; Serializing this involves walking the structure to translate anything wrapped
; in a MerkleLink into a serialized object with a known hash to link to.

(defrecord MerkleLink
  [link-name target total-size]

  IDeref

  (deref [this]
    (if (isa? target multihash.core.Multihash)
      nil ; TODO: fetch multihash, parse
      target)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MerkleLink)
(ns-unmap *ns* 'map->MerkleLink)


(defn link
  "Constructs a new merkle link. The name should be a string. If the target is
  not a multihash value, this is considered a _pending_ link."
  ([link-name target]
   (link link-name target nil))
  ([link-name target total-size]
   (MerkleLink. link-name target total-size)))


#_
(def encode-node
  [node]
  (let [node-val (pb/protobuf NodeEncoding)])
  (pb/protobuf-dump )
  )
