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


(def Link (pb/protodef Merkledag$MerkleLink))
(def Node (pb/protodef Merkledag$MerkleNode))


