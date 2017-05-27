(ns merkledag.store.memory
  "In-memory node store that provides a simple backing for node data. Nodes are
  not actually serialized to blocks; this avoids the codec, block, and cache
  components of a normal node store."
  (:require
    [merkledag.link :as link]
    [merkledag.node :as node]
    [merkledag.store :as store]
    [multihash.digest :as digest]))


(defrecord MemoryNodeStore
  [memory]

  store/NodeStore

  (-get-node
    [this id]
    (get @memory id))


  (-store-node!
    [this node]
    (let [data (::node/data node)
          links (link/collect-table (::node/links node) data)
          content (pr-str [links data])
          id (digest/sha2-256 content)
          node {::node/id id
                ::node/size (count content)
                ::node/links links
                ::node/data data}]
      (swap! memory assoc id node)
      node))


  (-delete-node!
    [this id]
    (let [existed? (contains? @memory id)]
      (swap! memory dissoc id)
      existed?)))


(alter-meta! #'->MemoryNodeStore assoc :private true)
(alter-meta! #'map->MemoryNodeStore assoc :private true)


(defn memory-node-store
  [& {:as opts}]
  (map->MemoryNodeStore (assoc opts :memory (atom (sorted-map)))))
