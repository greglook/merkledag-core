(ns merkledag.store.block
  "Node store backed by content-addressable blocks, serialized with a codec."
  (:require
    [blocks.core :as block]
    [clojure.core.cache :as cache]
    [merkledag.node :as node]
    [merkledag.store.cache :as msc]
    [merkledag.store.core :as store]))


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
      (when-let [parsed (node/parse-block codec (block/get store id))]
        (let [node (select-keys parsed node/node-keys)]
          (swap! cache cache/miss id node)
          ; TODO: measure cache misses
          node))))


  (-store-node!
    [this links data]
    (when-let [block (node/format-block codec {::node/links links, ::node/data data})]
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
