(ns merkledag.system.util
  "Construction functions for component systems."
  (:require
    [blocks.store.memory :refer [memory-block-store]]
    [merkledag.codec.node-v1 :refer [edn-node-codec]]
    [merkledag.node.store :refer [block-node-store]]
    [merkledag.node.cache :refer [node-cache]]))


(defn init-store
  "Constructs a new node store with default values.

  Options may include:

  - `:store`
    Block store to persist nodes to. Defaults to an in-memory store.
  - `:codec`
    Codec to serialize nodes with. Defaults to an EDN codec with basic types.
  - `:cache`
    Map of options to supply to construct a node cache. See
    `merkledag.node.cache/node-cache` for options."
  [& {:as opts}]
  (block-node-store
    :store (or (:store opts) (memory-block-store))
    :codec (or (:codec opts) (edn-node-codec (:types opts)))
    :cache (when (:cache opts)
             (atom (apply node-cache {} (apply concat (:cache opts)))))))
