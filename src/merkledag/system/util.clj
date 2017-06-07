(ns ^:no-doc merkledag.system.util
  "Construction functions for component systems."
  (:require
    [blocks.store.memory :refer [memory-block-store]]
    [merkledag.codec.node-v1 :as v1]
    [merkledag.node.store :refer [block-node-store]]
    [merkledag.node.cache :refer [node-cache]]))


(defn memory-store
  "Constructs an in-memory node store."
  [& {:as opts}]
  (block-node-store
    :store (memory-block-store)
    :codec (v1/edn-node-codec (:types opts))
    :cache (when (:cache opts)
             (atom (apply node-cache {} (apply concat (:cache opts)))))))
