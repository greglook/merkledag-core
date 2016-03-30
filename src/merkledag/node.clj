(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.codecs.mux :as mux]
    [schema.core :as s :refer [defschema]])
  (:import
    blocks.data.Block
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
    (when-not (or (seq (:links node)) (:data node))
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
    (mux/mux-codec
      :edn (edn-codec types))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->NodeCodec)
(ns-unmap *ns* 'map->NodeCodec)
