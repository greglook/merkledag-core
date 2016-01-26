(ns merkledag.codecs.node
  "Functions to handle merkledag nodes serialized using a separate subcodec."
  (:require
    [merkledag.codecs.edn :refer [edn-codec]]
    [multicodec.core :as codec]
    (multicodec.codecs
      [mux :as mux])))


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
    (let [links' (when (seq (:links node))
                   ; TODO: canonical link table
                   (vec (:links node)))
          ; TODO: data should be rendered with links replaced with numeric indexes into link table
          data' (:data node)
          value (cond-> {}
                  links'
                    (assoc :links links')
                  data'
                    (assoc :data data'))]
      ; TODO: bind link table?
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
        (assoc value :encoding [header encoding])))))


(defn node-codec
  [types]
  (NodeCodec.
    "/merkledag/v1"
    (mux/mux-codec
      :edn (edn-codec types))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->NodeCodec)
(ns-unmap *ns* 'map->NodeCodec)
