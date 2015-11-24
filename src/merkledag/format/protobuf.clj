(ns merkledag.format.protobuf
  "MerkleDAG protobuffer serialization functions.

  Codecs should have two properties, `:types` and `:link-constructor`."
  (:require
    [blocks.core :as block]
    [byte-streams :as bytes]
    [flatland.protobuf.core :as proto]
    [merkledag.link :as link]
    [multicodec.core :as multicodec]
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


(defn- encode-protobuf-data
  "Encodes some data into a protobuf `ByteString`. If the input is a byte array
  or a `ByteBuffer`, it is used directly as the segment.  If it is `nil`, no
  data segment is returned. Otherwise, the value is encoded using the given
  codec."
  ^ByteString
  [codec data]
  (cond
    (nil? data)
      nil
    (instance? (Class/forName "[B") data)
      (ByteString/copyFrom ^bytes data)
    (instance? ByteBuffer data)
      (ByteString/copyFrom ^ByteBuffer data)
    ; TODO: PersistentBytes?
    :else
      (ByteString/copyFrom (multicodec/encode codec data))))


(defn- encode-protobuf-node
  "Encodes a list of links and a data value into a protobuf representation."
  [codec links data]
  (let [links' (some->> (seq links)
                        (sort-by :name)
                        (mapv encode-protobuf-link))
        data' (encode-protobuf-data codec data)]
    (when (or links' data')
      (cond-> (proto/protobuf NodeEncoding)
        links'
          (assoc :links links')
        data'
          (assoc :data data')))))



;; ## Protobuffer Decoding Functions

(defn- decode-link
  "Decodes a protobuffer link value into a map representing a MerkleLink."
  [proto-link]
  (link/->link
    (:name proto-link)
    (multihash/decode (.toByteArray ^ByteString (:hash proto-link)))
    (:tsize proto-link)))


(defn- decode-data
  "Decodes the data segment from a protobuf-encoded node structure."
  [codec links ^ByteString data]
  (when data
    (binding [link/*link-table* links]
      (->> (.asReadOnlyByteBuffer data)
           (bytes/to-input-stream)
           (multicodec/decode! codec)))))



;; ## Protobuffer Node Format

(defrecord ProtobufFormat
  [codec]

  NodeFormat

  (build-node
    [this links data]
    (when-let [node (encode-protobuf-node codec links data)]
      (-> (proto/protobuf-dump node)
          (block/read!)
          (assoc :links (some-> links vec)
                 :data data))))


  (parse-node
    [this block]
    ; Try to parse content as protobuf node.
    (if-let [node (with-open [content (block/open block)]
                    (proto/protobuf-load-stream NodeEncoding content))]
      ; Try to parse data in link table context.
      (assoc block
             :links (some->> node :links seq (mapv decode-link))
             :data (decode-data (:data node)))
      ; Data is not protobuffer-encoded, return raw block.
      block)))
