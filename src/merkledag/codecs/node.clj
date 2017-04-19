(ns merkledag.codecs.node
  "Functions to serialize merkledag nodes."
  (:require
    [blocks.core :as block]
    [merkledag.codecs.cbor :refer [cbor-codec]]
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]])
  (:import
    java.io.ByteArrayInputStream))


;; ## Node Codec

(defrecord NodeCodec
  [header mux]

  codec/Encoder

  (encodable?
    [this value]
    (boolean (and (map? value)
                  (or (seq (:links value))
                      (:data value)))))


  (encode!
    [this output node]
    (when-not (codec/encodable? this node)
      (throw (ex-info (str "Cannot encode node data: " (pr-str node))
                      {:node node})))
    (let [links' (when (seq (:links node)) (vec (:links node)))
          data' (link/replace-links links' (:data node))]
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
      {:links links
       :data (link/resolve-indexes links data)})))


;; Privatize automatic constructor functions.
(alter-meta! #'->NodeCodec assoc :private true)
(alter-meta! #'map->NodeCodec assoc :private true)


(defn node-codec
  [types]
  #_
  (let [edn (edn-codec types)
        cbor (cbor-codec types)
        data-mux (mux-codec :cbor cbor :edn edn)]
    (->NodeCodec
      "/merkledag/v1"
      (mux-codec
        ;:snappy (snappy-codec data-mux)
        :gzip (gzip-codec data-mux)
        :cbor cbor
        :edn edn)))
  (->NodeCodec
    "/merkledag/v1"
    (mux-codec
      :edn (edn-codec types))))



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
      (try
        (assoc (block/read! value)
               :encoding nil)
        (catch Exception ex
          (throw (ex-info "Value is not valid node data and can't be read as raw bytes"
                          {:value value}
                          ex)))))))


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
