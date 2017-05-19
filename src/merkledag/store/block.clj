(ns merkledag.store.block
  "Node store backed by content-addressable blocks, serialized with a codec."
  (:require
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multihash.digest :as digest]))


(defrecord BlockNodeStore
  [codec store cache]

  node/NodeStore

  (-get-node
    [this id]
    (throw (RuntimeException. "NYI")))


  (-store-node!
    [this links data]
    (throw (RuntimeException. "NYI")))


  (-delete-node!
    [this id]
    (throw (RuntimeException. "NYI"))))


(alter-meta! #'->BlockNodeStore assoc :private true)
(alter-meta! #'map->BlockNodeStore assoc :private true)


(defn block-node-store
  [& {:as opts}]
  (map->BlockNodeStore opts))
