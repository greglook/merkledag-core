(ns merkledag.codec
  "MerkleDAG protobuffer serialization functions."
  (:require
    [blobble.core :as blob]
    [byte-streams :as bytes]
    [flatland.protobuf.core :as proto]
    [merkledag.edn :as edn]
    [merkledag.link :as link :refer [*link-table*]]
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

(defn- decode-link
  "Decodes a protobuffer link value into a map representing a MerkleLink."
  [link]
  (array-map
    :name (:name link)
    :hash (multihash/decode (.toByteArray ^ByteString (:hash link)))
    :tsize (:tsize link)))


(defn- decode-data
  "Decodes a data segment from an object in the context of its link table."
  [types links data]
  (when data
    (binding [*link-table* links]
      (or (edn/parse-data types data)
          data))))


(defn decode
  "Decodes a blob to determine whether it's a full object or a raw block.

  Returns an updated blob record with `:links` and `:data` filled in. Returns
  nil if blob is nil or has no content."
  [types blob]
  (when-let [content (blob/open blob)]
    ; Try to parse content as protobuf node.
    (if-let [node (proto/protobuf-load-stream NodeEncoding content)]
      (let [links (some->> node :links (seq) (mapv decode-link))
            data-segment (when-let [^ByteString bs (:data node)] (.asReadOnlyByteBuffer bs))]
        ; Try to parse data in link table context.
        (assoc blob
               :links links
               :data (decode-data types links data-segment)))
      ; Data is not protobuffer-encoded, return raw block.
      blob)))
