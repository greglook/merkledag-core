(ns merkledag.v1
  "Initial version of the composite node codec."
  (:require
    [merkledag.codec.cbor :refer [cbor-codec]]
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.codec.compress :refer [gzip-codec]]
    [multicodec.codec.label :refer [filter-codec]]
    [multicodec.core :as codec]
    ;[multicodec.header :as header]
    [multihash.core :as multihash])
  (:import
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


; TODO: move this out of codec subns


(def ^:const codec-header
  "/merkledag/v1")


(def core-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :cbor/tag 422
    :cbor/writers {Multihash multihash/encode}
    :edn/writers {Multihash multihash/base58}}

   'merkledag/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}
    :cbor/tag 423}

   'merkledag.link/index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}
    :cbor/tag 72}})



;; ## Node Codec

#_
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
    (let [links (::node/links node)
          data* (link/replace-links links (::node/data node))]
      (codec/encode! mux output [links data*])))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (let [[links data* :as value] (codec/decode! mux input)]
      (when-not (and (vector? value) (or links data*))
        (throw (ex-info "Decoded bad node value without links or data"
                        {:value value})))
      (cond-> {}
        (seq links)   (assoc ::node/links (vec links))
        (some? data*) (assoc ::node/data (link/resolve-indexes links data*))))))


(defn node-codec
  ([]
   (node-codec nil))
  ([types]
   (let [types* (merge-with merge core-types types)]
     (codec/mux
       :mdag (label-codec codec-header)
       :gzip (gzip-codec)
       :cbor (cbor-codec types*)
       :edn (edn-codec types*)))))
