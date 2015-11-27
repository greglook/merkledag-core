(ns merkledag.format
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data.

  This namespace also provides the sole format implementation, using
  protocol buffers."
  (:require
    [blocks.core :as block]
    [byte-streams :as bytes]
    [flatland.protobuf.core :as proto]
    [merkledag.link :as link]
    (merkledag.codec
      [bin :refer [bin-codec]]
      [edn :refer [edn-codec]])
    (multicodec
      [codecs :as codecs]
      [core :as multicodec])
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    (com.google.protobuf
      ByteString
      InvalidProtocolBufferException)
    (merkledag.format
      Merkledag$MerkleLink
      Merkledag$MerkleNode)))


(defprotocol NodeFormat
  "Protocol for formatters which can construct and decode node records."

  (build-node
    [formatter links data]
    "Encodes the links and data of a node into a block value.")

  (parse-node
    [formatter block]
    "Decodes the block to determine the node structure. Returns an updated block
    value with `:links` and `:data` set appropriately."))



;; ## Utility Functions

;; Extend link targeting to blocks for convenience.
(defmethod link/target Block
  [block]
  (:id block))


(defn binary?
  "Predicate which returns true if the argument is a byte array, `ByteBuffer`,
  or `PersistentBytes` value."
  [x]
  (or (instance? java.nio.ByteBuffer x)
      (instance? blocks.data.PersistentBytes x)
      (instance? (Class/forName "[B") x)))


(defn select-encoder
  "Chooses text codec for strings, bin codec for raw bytes, and EDN for
  everything else."
  [_ value]
  (cond
    (string? value)
      :text
    (binary? value)
      :bin
    :else
      :edn))



;; ## Protocol Buffer Format

;; Schema Types
(def LinkEncoding (proto/protodef Merkledag$MerkleLink))
(def NodeEncoding (proto/protodef Merkledag$MerkleNode))



;; ### Encoding Functions

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
  [codec links data]
  (let [links' (some->> (seq links)
                        (sort-by :name)
                        (mapv encode-protobuf-link))
        data' (some->> data
                       (multicodec/encode codec)
                       (ByteString/copyFrom))]
    (when (or links' data')
      (cond-> (proto/protobuf NodeEncoding)
        links'
          (assoc :links links')
        data'
          (assoc :data data')))))



;; ### Decoding Functions

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



;; ### Protobuffer Node Format

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
    (try
      ; Try to parse content as protobuf node.
      (let [node (with-open [content (block/open block)]
                   (proto/protobuf-load-stream NodeEncoding content))
            links (some->> node :links seq (mapv decode-link))]
        ; Try to parse data in link table context.
        (assoc block
               :links links
               :data (decode-data codec links (:data node))))
      (catch InvalidProtocolBufferException e
        ; Data is not protobuffer-encoded, return raw block.
        block))))


;; Remove automatic constructor function.s
(ns-unmap *ns* '->ProtobufFormat)
(ns-unmap *ns* 'map->ProtobufFormat)


(defn protobuf-format
  "Creates a new protobuf node formatter with a multiplexing data codec.

  By default, this uses binary and text encodings for bytes and strings, and
  EDN for everything else. The first argument should provide data type
  definitions for the codecs to use."
  [types]
  (-> (codecs/mux-codec
        :edn  (edn-codec types)
        :bin  (bin-codec)
        :text (codecs/text-codec))
      (assoc :select-encoder select-encoder)
      (ProtobufFormat.)))
