(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.codecs.mux :as mux])
  (:import
    blocks.data.Block
    merkledag.link.MerkleLink))


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
    (let [links' (when-let [links (seq (:links node))]
                   ; TODO: dedupe link table?
                   (vec links))
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



;; ## Helper Functions

; TODO: are these useful?

(defn node-value
  "Returns the data parsed from a node block. The node's links and block id are
  added as metadata."
  [node]
  (when-let [data (:data node)]
    (vary-meta data assoc
               ::id (:id node)
               ::links (:links node))))


(defn node-links
  "Returns the links associated with a given value. May be a block or a value
  returned from `node-value`."
  [value]
  (if (instance? Block value)
    (:links value)
    (::links (meta value))))
