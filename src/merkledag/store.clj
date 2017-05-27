(ns ^:no-doc merkledag.store
  "Core node storage API.")


(defprotocol NodeStore
  "Node stores provide an interface for creating, persisting, and retrieving
  node data."

  (-get-node
    [store id]
    "Retrieve a node map from the store by id.")

  (-store-node!
    [store node]
    "Create a new node by serializing the links and data. Returns a new node
    map. The `node` should be a map containing at least a link table or node
    data under the `:merkledag.node/links` or `:merkledag.node/data` keys,
    respectively.")

  (-delete-node!
    [store id]
    "Remove a node from the store."))
