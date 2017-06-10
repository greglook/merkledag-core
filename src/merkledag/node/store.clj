(ns merkledag.node.store
  "Node store backed by content-addressable blocks, serialized with a codec."
  (:require
    [blocks.core :as block]
    [clojure.core.cache :as cache]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    blocks.data.Block
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


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

(defn format-block
  "Serialize the given data value into a block using the codec. The `value`
  should be a node map, and must contain either a `::links` table or a `::data`
  entry.

  Returns a block containing the formatted content and extra node attributes,
  or nil if value is nil."
  [codec value]
  (when value
    (binding [header/*headers* []]
      (let [data (::node/data value)
            links (link/collect-table (::node/links value) data)
            node {::node/links links
                  ::node/data data}
            baos (ByteArrayOutputStream.)
            size (codec/encode-with-header! codec baos node)
            content (.toByteArray baos)
            block (block/read! content)]
        (-> {::node/id (:id block)
             ::node/size (:size block)
             ::node/encoding header/*headers*}
            (cond->
              (seq links)  (assoc ::node/links links)
              (some? data) (assoc ::node/data data))
            (->> (into block)))))))


(defn parse-block
  "Attempt to parse the contents of a block with the given node codec. The
  codec should return a map of attributes to merge into the block;
  typically including `::links` and `::data` fields with the decoded node
  information.

  Returns an updated version of the block with node keys set, or nil if block
  is nil."
  [codec block]
  (when block
    (with-open [content (block/open block)]
      (binding [header/*headers* []]
        (-> (codec/decode-with-header! codec content)
            (select-keys [::node/links ::node/data])
            (assoc ::node/id (:id block)
                   ::node/size (:size block)
                   ::node/encoding header/*headers*)
            (->> (into block)))))))



;; ## Node Store

(defrecord BlockNodeStore
  [codec store cache]

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
      (when-let [parsed (parse-block codec (block/get store id))]
        (let [node (select-keys parsed node/node-keys)]
          (when cache
            ; TODO: measure cache misses
            (swap! cache cache/miss id node))
          node))))


  (-store-node!
    [this node]
    (when node
      (let [block (format-block codec (select-keys node [::node/links ::node/data]))]
        ; TODO: measure node creation and size
        (block/put! store block)
        (when cache
          (swap! cache cache/miss (::node/id node) (select-keys block node/node-keys)))
        block)))


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
