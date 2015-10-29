(ns merkledag.data.codec
  "MerkleDAG types and serialization functions."
  (:require
    [blobble.core :as blob]
    [flatland.protobuf.core :as pb]
    [merkledag.data.edn :as edn]
    [multihash.core :as multihash])
  (:import
    com.google.protobuf.ByteString
    (merkledag.proto
      Merkledag$MerkleLink
      Merkledag$MerkleNode)
    multihash.core.Multihash
    java.nio.ByteBuffer))


;; Protobuffer Schema Types
(def LinkEncoding (pb/protodef Merkledag$MerkleLink))
(def NodeEncoding (pb/protodef Merkledag$MerkleNode))



;; ## Serialization / Encoding

(defn- serialize-data-segment
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
  (pb/protobuf
    LinkEncoding
    :hash (-> (:target link)
              (multihash/encode)
              (ByteString/copyFrom))
    :name (:name link)
    :tsize (:tsize link)))


(defn- encode-protobuf-node
  "Encodes a list of links and a data value into a protobuf representation."
  [types links data]
  (let [node (pb/protobuf NodeEncoding)
        data-bs (serialize-data-segment types data)]
    (cond-> (pb/protobuf NodeEncoding)
      (seq links)
        (assoc :links (map encode-protobuf-link (sort-by :name links)))
      data-bs
        (assoc :data data-bs))))


(defn encode
  "Encodes a merkle node as a blob with extra `:links` and `:data` entries. The
  `:content` will be a canonical serialized binary representation and the `:id`
  will be a multihash of the contents."
  [types links data]
  (-> (encode-protobuf-node links data)
      (pb/protobuf-dump)
      (blob/read!)
      (assoc :links links :data data)))



;; ## Deserialization / Decoding

(defn decode
  [types {:keys [id content] :as blob}]
  ; try to parse content as protobuf node
  ;   try to parse data in link table context
  ;     else return raw data
  ;   else return "raw" node
  (throw (RuntimeException. "Not Yet Implemented")))
