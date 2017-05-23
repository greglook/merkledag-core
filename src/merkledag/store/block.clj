(ns merkledag.store.block
  "Node store backed by content-addressable blocks, serialized with a codec."
  (:require
    [blocks.core :as block]
    [clojure.core.cache :as cache]
    [merkledag.node :as node]
    [merkledag.store.cache :as msc]
    [merkledag.store.core :as store]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [multihash.core :as multihash])
  (:import
    java.io.ByteArrayInputStream))


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
        (let [content (codec/encode codec value)  ; TODO: encode-with-header
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



;; ## Node Store

(defrecord BlockNodeStore
  [codec store cache]

  store/NodeStore

  (-get-node
    [this id]
    ; TODO: measure node size
    (if (cache/has? @cache id)
      ; Return cached value.
      (when-let [node (cache/lookup @cache id)]
        (swap! cache cache/hit id)
        ; TODO: measure cache hits
        node)
      ; Node is not cached.
      ; TODO: measure parse time
      (when-let [parsed (parse-block codec (block/get store id))]
        (let [node (select-keys parsed node/node-keys)]
          (swap! cache cache/miss id node)
          ; TODO: measure cache misses
          node))))


  (-store-node!
    [this links data]
    (when-let [block (format-block codec {::node/links links, ::node/data data})]
      (let [node (select-keys block node/node-keys)]
        (block/put! store block)
        (swap! cache cache/miss (::node/id node) node)
        ; TODO: measure node creation and size
        node)))


  (-delete-node!
    [this id]
    ; TODO: measure node deletion
    (swap! cache cache/evict id)
    (block/delete! store id)))


(alter-meta! #'->BlockNodeStore assoc :private true)
(alter-meta! #'map->BlockNodeStore assoc :private true)


(defn block-node-store
  [& {:as opts}]
  (let [cache (atom (merge (msc/node-cache {}) (:cache opts)))]
    (map->BlockNodeStore
      (assoc opts :cache cache))))
