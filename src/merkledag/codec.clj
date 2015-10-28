(ns merkledag.codec
  "MerkleDAG types and serialization functions."
  (:require
    [flatland.protobuf.core :as pb]
    [merkledag.data.edn :as edn]
    [multihash.core :as multihash])
  (:import
    (com.google.protobuf
      ByteString)
    (merkledag.proto
      Merkledag$MerkleLink
      Merkledag$MerkleNode)
    (multihash.core
      Multihash)
    (java.nio
      ByteBuffer)))


;; ## Protobuffer Encoding

(def LinkEncoding (pb/protodef Merkledag$MerkleLink))
(def NodeEncoding (pb/protodef Merkledag$MerkleNode))


(defn- serialize-data-segment
  "Encodes a data segment from some input. If the input is a byte array or a
  `ByteBuffer`, it is used directly as the segment. If it is `nil`, no data
  segment is returned. Otherwise, the value is serialized using the provided
  handlers."
  ^ByteString
  [types data]
  (cond
    (nil? data)
      nil
    (instance? (Class/forName "[B") data)
      (ByteString/copyFrom ^bytes data)
    (instance? ByteBuffer data)
      (ByteString/copyFrom ^ByteBuffer data)
    :else
      (ByteString/copyFrom (edn/print-data types data))))


(defn- encode-proto-link
  [link]
  (pb/protobuf
    LinkEncoding
    :hash (-> (:target link)
              (multihash/encode)
              (ByteString/copyFrom))
    :name (:name link)
    :tsize (:tsize link)))


(defn- encode-proto-node
  "Encodes a list of links and a data value into a protobuffer representation."
  [types links data]
  (let [node (pb/protobuf NodeEncoding)
        data-bs (serialize-data-segment types data)]
    (cond-> (pb/protobuf NodeEncoding)
      (seq links)
        (assoc :links (map encode-proto-link (sort-by :name links)))
      data-bs
        (assoc :data data-bs))))


(defn node->blob
  [links data]
  (-> (encode-proto-node links data)
      (pb/protobuf-dump)
      (blob/read!)))


(defn blob->node
  [blob]
  ; try to parse content as protobuf node
  ;   try to parse data in link table context
  ;     else return raw data
  ;   else return "raw" node
  (throw (RuntimeException. "Not Yet Implemented")))
