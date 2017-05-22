(ns ^:no-doc merkledag.store.core
  "Core node storage API.")


(defprotocol NodeStore
  "Node stores provide an interface for creating, persisting, and retrieving
  node data."

  (-get-node
    [store id]
    "Retrieve a node map from the store by id.")

  (-store-node!
    [store links data]
    "Create a new node by serializing the links and data. Returns a new node
    map.")

  (-delete-node!
    [store id]
    "Remove a node from the store."))
