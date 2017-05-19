(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [blocks.core :as block]
    [clojure.future :refer [pos-int?]]
    [clojure.spec :as s]
    [merkledag.codec.cbor :refer [cbor-codec]]
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    java.io.ByteArrayInputStream
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(s/def ::id #(instance? Multihash %))
(s/def ::size pos-int?)
(s/def ::encoding (s/nilable (s/coll-of string? :kind vector?)))
(s/def ::links (s/coll-of link/merkle-link? :kind vector?))
(s/def ::data coll?)

(s/def :merkledag/node
  (s/keys :req-un [::id ::size ::encoding]
          :opt-un [::links ::data]))



;; ## Node Codec

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


(defrecord NodeCodec
  [header mux]

  codec/Encoder

  (encodable?
    [this value]
    (boolean (and (map? value)
                  (or (seq (::links value))
                      (::data value)))))


  (encode!
    [this output node]
    (when-not (codec/encodable? this node)
      (throw (ex-info (str "Cannot encode node data: " (pr-str node))
                      {:node node})))
    (let [links' (when (seq (::links node)) (vec (::links node)))
          data' (link/replace-links links' (::data node))]
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
      {::links links
       ::data (link/resolve-indexes links data)})))


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



;; ## Formatting Functions

(defn- decode-info!
  [codec input]
  (binding [header/*headers* []]
    (try
      (-> (codec/decode! codec input)
          (select-keys [::links ::data])
          (assoc ::encoding header/*headers*))
      (catch clojure.lang.ExceptionInfo ex
        (case (:type (ex-data ex))
          :multicodec/bad-header
            {::encoding nil}
          :multicodec.codecs.mux/no-codec
            {::encoding header/*headers*}
          (throw ex))))))


(defn format-block
  "Serializes the given data value into a block using the codec. Returns a
  block containing the formatted content and extra node attributes."
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
               ::encoding nil)
        (catch Exception ex
          (throw (ex-info "Value is not valid node data and can't be read as raw bytes"
                          {:value value}
                          ex)))))))


(defn parse-block
  "Attempts to parse the contents of a block with the given codec. Returns an
  updated version of the block with additional keys set. At a minimum, this
  will add an `::encoding` key with the detected codec, or `nil` for raw
  blocks.

  The dispatched codec should return a map of attributes to merge into the
  block; typically including a `::data` field with the decoded block value.
  Node codecs should also return a `::links` vector."
  [codec block]
  (when block
    (with-open [content (block/open block)]
      (into block (decode-info! codec content)))))



;; ## Identity Protocol

(defprotocol Identifiable
  "Protocol for values which can be resolved to a multihash value in order to
  uniquely identify a node in the graph."

  (identify
    [value]
    "Return the multihash identifying the value."))


(extend-protocol Identifiable

  nil
  (identify [_] nil)

  multihash.core.Multihash
  (identify [m] m)

  blocks.data.Block
  (identify [b] (:id b))

  merkledag.link.MerkleLink
  (identify [l] (:target l)))



;; ## Node Storage API

(defprotocol ^:no-doc NodeStore
  "Node stores provide an interface for creating, persisting, and retrieving
  node data."

  (-get-node
    [store id]
    "Retrieve a node map from the store by id.")

  (-store-node!
    [store links data]
    "Create a new node by serializing the links and data. Returns a new node
    map.")

  (-delete-node!
    [store id]
    "Remove a node from the store."))


(defn get-node
  "Retrieve the identified node from the store. Returns a node map.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (-get-node store id)))


(defn get-links
  "Retrieve the links for the identified node. Returns the node's link table as
  a vector of `MerkleLink` values.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (:links node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node))))))


(defn get-data
  "Retrieve the data for the identified node. Returns the node's data body.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (:data node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node)
          ::links (:links node))))))


(defn store-node!
  "Create a new node by serializing the links and data. Returns the stored node
  map, or nil if both links and data are nil."
  ([store data]
   (store-node! store nil data))
  ([store links data]
   (when (or links data)
     (-store-node! store links data))))


(defn delete-node!
  "Remove a node from the store.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (-delete-node! store id)))



;; ## Utility Functions

(defn total-size
  "Calculates the total size of data reachable from the given node. Expects a
  node map with `::size` and `::links` entries.

  Raw blocks and nodes with no links have a total size equal to their `::size`.
  Each link in the node's link table adds its `::link/tsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (::size node)]
    (->> (::links node)
         (keep ::link/tsize)
         (reduce + size))))
