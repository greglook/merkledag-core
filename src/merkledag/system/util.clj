(ns ^:no-doc merkledag.system.util
  "Construction functions for component systems."
  (:require
    [blocks.store.memory :refer [memory-block-store]]
    [merkledag.codec.node-v1 :refer [edn-node-codec]]
    [merkledag.node.store :refer [block-node-store]]
    [merkledag.node.cache :refer [node-cache]]))


(defn init-store
  "Constructs an in-memory node store."
  [& {:as opts}]
  (block-node-store
    :store (or (:store opts) (memory-block-store))
    :codec (or (:codec opts) (edn-node-codec (:types opts)))
    :cache (when (:cache opts)
             (atom (apply node-cache {} (apply concat (:cache opts)))))))
