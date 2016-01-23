(ns merkledag.codecs.node
  "Functions to handle merkledag nodes serialized using a separate subcodec."
  (:require
    [merkledag.codecs.edn :refer [edn-codec]]
    [multicodec.core :as codec]
    (multicodec.codecs
      [mux :refer [mux-codec]])))


(defrecord NodeCodec
  [header codec]

  codec/Encoder

  (encodable?
    [this value]
    (and (map? value) (or (:links value) (:data value))))


  (encode!
    [this output node]
    (when-not (or (seq (:links node)) (:data node))
      (throw (IllegalArgumentException.
               "Cannot encode a node with no links or data!")))
    (let [links' (when (seq (:links node))
                   (vec (:links node)))
          data' (:data node)
          value (cond-> {}
                  links'
                    (assoc :links links')
                  data'
                    (assoc :data data'))]
      (codec/encode! codec output value)))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    '...))


(defn node-codec
  [types]
  (NodeCodec.
    "/merkledag/v1"
    (mux-codec
      :edn (edn-codec types))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->NodeCodec)
(ns-unmap *ns* 'map->NodeCodec)
