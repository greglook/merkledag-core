(ns merkledag.codecs.node
  "Functions to handle merkledag nodes serialized using a separate subcodec."
  (:require
    [clojure.walk :as walk]
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    (multicodec.codecs
      [mux :as mux]))
  (:import
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
                  links'
                    (assoc :links links')
                  data'
                    (assoc :data data'))]
      (codec/encode! mux output value)))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (binding [mux/*dispatched-codec* nil]
      (let [value (codec/decode! mux input)
            encoding (get-in mux [:codecs mux/*dispatched-codec* :header])]
        (when-not (codec/encodable? this value)
          (throw (ex-info "Decoded bad node value with missing links and data")
                 {:encoding encoding
                  :value value}))
        (assoc value
               :encoding [header encoding]
               :data (link/resolve-links (:links value) (:data value)))))))


(defn node-codec
  [types]
  (NodeCodec.
    "/merkledag/v1"
    (mux/mux-codec
      :edn (edn-codec types))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->NodeCodec)
(ns-unmap *ns* 'map->NodeCodec)
