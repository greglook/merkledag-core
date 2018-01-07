(ns merkledag.store
  "Node store backed by content-addressable blocks, serialized with a codec."
  (:require
    [blocks.core :as block]
    [clojure.core.cache :as cache]
    [merkledag.codec.cbor :refer [cbor-codec]]
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.codec.compress :refer [gzip-codec]]
    [multicodec.codec.label :refer [label-codec]]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)
    (merkledag.link
      LinkIndex
      MerkleLink)
    multihash.core.Multihash))


(def ^:const node-header
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


(extend-protocol link/Target

  Block

  (identify
    [block]
    (:id block))

  (reachable-size
    [block]
    (or (node/reachable-size block)
        (:size block))))



;; ## Formatting Functions

(defn node-codecs
  "Construct a new set of standard node codecs."
  [types]
  (let [types* (merge-with merge core-types types)]
    (codec/mux
      :mdag (label-codec node-header)
      :gzip (gzip-codec)
      :cbor (cbor-codec types*)
      :edn (edn-codec types*))))


(defn format-block
  "Serialize the given data value into a block using the codec. The `value`
  should be a node map, and must contain either a `::links` table or a `::data`
  entry.

  Returns a block containing the formatted content and extra node attributes,
  or nil if value is nil."
  [store value]
  (when value
    (when-not (or (seq (::node/links value))
                  (::node/data value))
      (throw (IllegalArgumentException.
               "Cannot format node without links or data!")))
    (let [data (::node/data value)
          links (link/collect-table (::node/links value) data)
          data* (link/replace-links links data)
          selectors (:selectors store [:mdag :edn])
          baos (ByteArrayOutputStream.)]
      (binding [header/*headers* []]
        (with-open [stream (codec/encoder-stream (:codecs store) baos selectors)]
          (codec/write! stream links)
          (codec/write! stream data*))
        (let [block (block/read! (.toByteArray baos))]
          (-> {::node/id (:id block)
               ::node/size (:size block)
               ::node/encoding header/*headers*}
              (cond->
                (seq links)  (assoc ::node/links links)
                (some? data) (assoc ::node/data data))
              (->> (into block))))))))


(defn parse-block
  "Attempt to parse the contents of a block with the given node codec. The
  codec should return a map of attributes to merge into the block;
  typically including `::links` and `::data` fields with the decoded node
  information.

  Returns an updated version of the block with node keys set, or nil if block
  is nil."
  [store block]
  (when block
    (binding [header/*headers* []]
      (with-open [content (block/open block)
                  stream (codec/decoder-stream (:codecs store) content)]
        (let [links (codec/read! stream)
              data* (codec/read! stream)]
          (when-not (or (seq links) data*)
            (throw (ex-info "Decoded bad node value without links or data"
                            {:links links, :data data*})))
          (-> {::node/id (:id block)
               ::node/size (:size block)
               ::node/encoding header/*headers*}
              (cond->
                (seq links)
                  (assoc ::node/links (vec links))
                data*
                  (assoc ::node/data (link/resolve-indexes links data*)))
              (->> (into block))))))))



;; ## Node Store

(defrecord BlockNodeStore
  [codecs store cache]

  node/NodeStore

  (-get-node
    [this id]
    ; TODO: measure node size
    (if (and cache (cache/has? @cache id))
      ; Return cached value.
      (when-let [node (cache/lookup @cache id)]
        (swap! cache cache/hit id)
        ; TODO: measure cache hits
        node)
      ; Node is not cached.
      ; TODO: measure parse time
      (when-let [parsed (parse-block this (block/get store id))]
        (let [node (select-keys parsed node/node-keys)]
          (when cache
            ; TODO: measure cache misses
            (swap! cache cache/miss id node))
          node))))


  (-store-node!
    [this node]
    (when node
      (let [block (format-block this (select-keys node [::node/links ::node/data]))
            node' (select-keys block node/node-keys)]
        ; TODO: measure node creation and size
        (block/put! store block)
        (when cache
          (swap! cache cache/miss (:id block) node'))
        node')))


  (-delete-node!
    [this id]
    ; TODO: measure node deletion
    (when cache
      (swap! cache cache/evict id))
    (block/delete! store id)))


(alter-meta! #'->BlockNodeStore assoc :private true)
(alter-meta! #'map->BlockNodeStore assoc :private true)


(defn block-node-store
  [& {:as opts}]
  (map->BlockNodeStore opts))
