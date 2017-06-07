(ns ^:no-doc merkledag.system.util
  "Construction functions for component systems."
  (:require
    [blocks.store.memory :refer [memory-block-store]]
    [merkledag.codec.node-v1 :as v1]
    [merkledag.store.block :refer [block-node-store]]
    [merkledag.store.cache :refer [node-cache]]))


(defn memory-store
  "Constructs an in-memory node store."
  [& {:as opts}]
  (block-node-store
    :store (memory-block-store)
    :codec (v1/edn-node-codec (:types opts))
    :cache (when (:cache opts)
             (apply node-cache {} (apply concat (:cache opts))))))
