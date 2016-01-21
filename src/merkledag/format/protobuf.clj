(ns merkledag.format.protobuf
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data.

  This namespace also provides the sole format implementation, using
  protocol buffers."
  (:require
    [blocks.core :as block]
    [byte-streams :as bytes]
    [flatland.protobuf.core :as proto]
    [merkledag.link :as link]
    [merkledag.format :as format]
    [multicodec.core :as codec]
    [multihash.core :as multihash])
  (:import
    (com.google.protobuf
      ByteString
      InvalidProtocolBufferException)
    (merkledag.format.protobuf
      Merkledag$MerkleLink
      Merkledag$MerkleNode)))


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


(defn- encode-protobuf-node
  "Encodes a list of links and a data value into a protobuf representation."
  [codec links data]
  (let [links' (some->> (seq links)
                        (sort-by :name) ; TODO: how does this impact empty-named links?
                        (mapv encode-protobuf-link))
        data' (some->> data
                       (codec/encode codec)
                       (ByteString/copyFrom))]
    (when (or links' data')
      (cond-> (proto/protobuf NodeEncoding)
        links'
          (assoc :links links')
        data'
          (assoc :data data')))))



;; ## Decoding Functions

(defn- decode-link
  "Decodes a protobuffer link value into a map representing a MerkleLink."
  [proto-link]
  (link/create
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
           (codec/decode! codec)))))



;; ## ProtocolBuffer Format

(defrecord ProtobufFormat
  [codec]

  format/BlockFormat

  (format-node
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
            links (some->> (:links node) (seq) (mapv decode-link))]
        ; Try to parse data in link table context.
        (assoc block
               :links links
               :data (decode-data codec links (:data node))))
      (catch InvalidProtocolBufferException e
        ; Data is not protobuffer-encoded, return raw block.
        block))))


(defn protobuf-format
  "Creates a new protobuf node formatter with a codec for handling data
  segments.

  By default, the codec will use a multiplexing codec to select among binary
  and text encodings for bytes and strings, and EDN for everything else."
  [codec]
  (when-not (and (satisfies? codec/Encoder codec)
                 (satisfies? codec/Decoder codec))
    (throw (IllegalArgumentException.
             (str "Format codec must support both encoding and decoding, got: "
                  (pr-str codec)))))
  (ProtobufFormat. codec))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->ProtobufFormat)
(ns-unmap *ns* 'map->ProtobufFormat)
