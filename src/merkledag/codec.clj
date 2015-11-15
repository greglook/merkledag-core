(ns merkledag.codec
  "MerkleDAG protobuffer serialization functions.

  Codecs should have two properties, `:types` and `:link-constructor`."
  (:require
    [blocks.core :as block]
    [byte-streams :as bytes]
    [flatland.protobuf.core :as proto]
    [merkledag.edn :as edn]
    [merkledag.link :as link]
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
  [codec links data]
  (let [links' (some->> (seq links)
                        (sort-by :name)
                        (mapv encode-protobuf-link))
        data' (encode-data-segment (:types codec) data)]
    (when (or links' data')
      (-> (encode-protobuf-node links' data')
          (proto/protobuf-dump)
          (block/read!)
          (assoc :links (some-> links vec)
                 :data data)))))



;; ## Decoding Functions

(defn- decode-link
  "Decodes a protobuffer link value into a map representing a MerkleLink."
  [proto-link]
  (link/->link
    (:name proto-link)
    (multihash/decode (.toByteArray ^ByteString (:hash proto-link)))
    (:tsize proto-link)))


(defn- decode-data
  "Decodes a data segment from an object in the context of its link table."
  [types links data]
  (when data
    (binding [link/*link-table* links]
      (or (edn/parse-data types data)
          ; TODO: convert to PersystentBytes
          data))))


(defn decode
  "Decodes a block to determine whether it's a full object or a raw block.

  Returns an updated block record with `:links` and `:data` filled in. Returns
  nil if block is nil or has no content."
  [codec block]
  ; Try to parse content as protobuf node.
  (if-let [node (with-open [content (block/open block)]
                  (proto/protobuf-load-stream NodeEncoding content))]
    (let [links (some->> node :links (seq) (mapv decode-link))
          data-segment (when-let [^ByteString bs (:data node)]
                         (.asReadOnlyByteBuffer bs))]
      ; Try to parse data in link table context.
      (assoc block
             :links links
             :data (decode-data (:types codec) links data-segment)))
    ; Data is not protobuffer-encoded, return raw block.
    block))
