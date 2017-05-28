(ns merkledag.codec.node-v1
  "Initial version of the composite node codec."
  (:require
    [merkledag.codec.cbor :refer [cbor-codec]]
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [multihash.core :as multihash])
  (:import
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(def ^:const codec-header
  "/merkledag/v1")


(def core-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :cbor/tag 68
    :cbor/writers {Multihash multihash/encode}
    :edn/writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}
    :cbor/tag 69}

   'data/link-index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}
    :cbor/tag 72}})



;; ## Node Codec

(defrecord NodeCodec
  [header mux]

  codec/Encoder

  (encodable?
    [this value]
    (boolean (and (map? value)
                  (or (seq (::node/links value))
                      (::node/data value)))))


  (encode!
    [this output node]
    (when-not (codec/encodable? this node)
      (throw (ex-info (str "Cannot encode node data: " (pr-str node))
                      {:node node})))
    (let [links' (when (seq (::node/links node)) (vec (::node/links node)))
          data' (link/replace-links links' (::node/data node))]
      (codec/encode! mux output [links' data'])))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (let [[links data :as value] (codec/decode! mux input)]
      (when-not (and (vector? value) (or links data))
        (throw (ex-info "Decoded bad node value missing links and data"
                        {:value value})))
      (cond-> {}
        (seq links) (assoc ::node/links (vec links))
        data        (assoc ::node/data (link/resolve-indexes links data))))))


(defn node-codec
  "Construct a new node codec. The codec will serialize values using the given
  map of types, merged into `core-types`."
  [types]
  (let [types+ (merge core-types types)
        edn (edn-codec types+)
        cbor (cbor-codec types+)
        data-mux (mux-codec :edn edn :cbor cbor)]
    (->NodeCodec
      codec-header
      data-mux
      #_ ; TODO: support compression wrapers
      (mux-codec
        :snappy (snappy-codec data-mux)
        :gzip (gzip-codec data-mux)
        :cbor cbor
        :edn edn))))
