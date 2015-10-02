(ns merkledag.codec
  "MerkleDAG types and serialization functions."
  (:require
    [clojure.data.fressian :as fress]
    [flatland.protobuf.core :as pb]
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


(defn- serialize-data
  "Encodes a data segment from some input. If the input is a byte array or a
  `ByteBuffer`, it is used directly as the segment. If it is `nil`, no data
  segment is returned. Otherwise, the value is serialized using Fressian via
  the provided handlers."
  ^ByteString
  [data & {:keys [handlers]}]
  (cond
    (nil? data)
      nil
    (instance? (Class/forName "[B") data)
      (ByteString/copyFrom ^bytes data)
    (instance? ByteBuffer data)
      (ByteString/copyFrom ^ByteBuffer data)
    :else
      ; TODO: implement custom set and map handlers to ensure fields are sorted
      (ByteString/copyFrom (fress/write data :handlers handlers))))


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
  [links data]
  (let [node (pb/protobuf NodeEncoding)
        data-bs (serialize-data data)]
    (cond-> (pb/protobuf NodeEncoding)
      (seq links)
        (assoc :links (map encode-proto-link links))
      data-bs
        (assoc :data data-bs))))


(defn node->blob
  [node]
  (-> node
      (encode-proto-node)
      (pb/protobuf-dump)
      (blob/read!)))


(defn blob->node
  [blob]
  ; try to parse content as protobuf node
  ;   try to parse data in link table context
  ;     else return raw data
  ;   else return "raw" node
  nil)
