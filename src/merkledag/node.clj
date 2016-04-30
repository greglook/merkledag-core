(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [blocks.core :as block]
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [schema.core :as s :refer [defschema]])
  (:import
    blocks.data.Block
    java.io.ByteArrayInputStream
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(defschema NodeSchema
  "Schema for a Node value."
  {:id Multihash
   :size s/Int
   :encoding [s/Str]
   (s/optional-key :links) [MerkleLink]
   (s/optional-key :data) s/Any})



;; ## Node Codec

(defrecord NodeCodec
  [header mux]

  codec/Encoder

  (encodable?
    [this value]
    (boolean
      (and (map? value)
           (or (seq (:links value))
               (:data value)))))


  (encode!
    [this output node]
    (when-not (codec/encodable? this node)
      (throw (IllegalArgumentException.
               "Cannot encode a node with no links or data!")))
    (let [links' (when (seq (:links node)) (vec (:links node)))
          data' (link/replace-links links' (:data node))
          value (cond-> {}
                  links' (assoc :links links')
                  data'  (assoc :data  data'))]
      (codec/encode! mux output value)))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (let [value (codec/decode! mux input)]
      (when-not (codec/encodable? this value)
        (throw (ex-info "Decoded bad node value missing links and data"
                        {:value value})))
      (assoc value :data (link/resolve-indexes (:links value) (:data value))))))


(defn node-codec
  [types]
  (NodeCodec.
    "/merkledag/v1"
    (mux-codec
      :edn (edn-codec types))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->NodeCodec)
(ns-unmap *ns* 'map->NodeCodec)



;; ## Format Functions

(defn- decode-info!
  [codec input]
  (binding [header/*headers* []]
    (try
      (-> (codec/decode! codec input)
          (select-keys [:links :data])
          (assoc :encoding header/*headers*))
      (catch clojure.lang.ExceptionInfo ex
        (case (:type (ex-data ex))
          :multicodec/bad-header
            {:encoding nil}
          :multicodec.codecs.mux/no-codec
            {:encoding header/*headers*}
          (throw ex))))))


(defn format-block
  "Serializes the given data value into a block using the codec. Returns a
  block containing both the formatted content, an `:encoding` key for the
  actual codec used (if any), and additional data merged in if the value was a
  map."
  [codec value]
  (when value
    (if (codec/encodable? codec value)
      (binding [header/*headers* []]
        (let [content (codec/encode codec value)
              encoded-headers header/*headers*
              block (block/read! content)
              info (decode-info! codec (ByteArrayInputStream. content))]
          (when-not (= encoded-headers (:encoding info))
            (throw (ex-info "Decoded headers do not match written encoding"
                            {:encoded encoded-headers
                             :decoded (:encoding info)})))
          (into block info)))
      (assoc (block/read! value)
             :encoding nil))))


(defn parse-block
  "Attempts to parse the contents of a block with the given codec. Returns an
  updated version of the block with additional keys set. At a minimum, this
  will add an `:encoding` key with the detected codec, or `nil` for raw blocks.

  The dispatched codec should return a map of attributes to merge into the
  block; typically including a `:data` field with the decoded block value. Node
  codecs should also return a `:links` vector."
  [codec block]
  (when block
    (with-open [content (block/open block)]
      (into block (decode-info! codec content)))))
