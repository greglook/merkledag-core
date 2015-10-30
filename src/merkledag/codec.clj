(ns merkledag.codec
  "MerkleDAG protobuffer serialization functions."
  (:require
    [flatland.protobuf.core :as proto]
    [merkledag.edn :as edn]
    [multihash.core :as multihash])
  (:import
    com.google.protobuf.ByteString
    (merkledag.proto
      Merkledag$MerkleLink
      Merkledag$MerkleNode)
    java.nio.ByteBuffer))


;; Protobuffer Schema Types
(def LinkEncoding (proto/protodef Merkledag$MerkleLink))
(def NodeEncoding (proto/protodef Merkledag$MerkleNode))



;; ## Encoding Functions

(defn- encode-data-segment
  "Encodes a data segment from some input into a protobuf `ByteString`. If the
  input is a byte array or a `ByteBuffer`, it is used directly as the segment.
  If it is `nil`, no data segment is returned. Otherwise, the value is
  serialized to EDN using the provided type plugins."
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


(defn- encode-protobuf-link
  "Encodes a merkle link into a protobuf representation."
  [link]
  (proto/protobuf
    LinkEncoding
    :hash (-> (:target link)
              (multihash/encode)
              (ByteString/copyFrom))
    :name (:name link)
    :tsize (:tsize link)))


(defn- encode-protobuf-node
  "Encodes a list of links and a data value into a protobuf representation."
  [links data]
  (cond-> (proto/protobuf NodeEncoding)
    links
      (assoc :links links)
    data
      (assoc :data data)))


(defn encode
  "Encodes a list of links and some data value as a protocol buffer binary
  sequence."
  ^bytes
  [types links data]
  (let [links' (some->> (seq links)
                        (sort-by :name)
                        (mapv encode-protobuf-link))
        data' (encode-data-segment types data)]
    (when (or links' data')
      (proto/protobuf-dump (encode-protobuf-node links' data')))))



;; ## Decoding Functions

; ...

(defn decode
  [types blob]
  ; try to parse content as protobuf node
  ;   try to parse data in link table context
  ;     else return raw data
  ;   else return raw blob
  (throw (RuntimeException. "Not Yet Implemented")))
