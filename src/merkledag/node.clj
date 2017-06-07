(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [clojure.future :refer [pos-int?]]
    [clojure.spec :as s]
    [merkledag.link :as link]
    [multihash.core :as multihash])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(s/def ::id #(instance? Multihash %))
(s/def ::size pos-int?)
(s/def ::encoding (s/nilable (s/coll-of string? :kind vector?)))
(s/def ::links ::link/table)
(s/def ::data coll?)

(s/def :merkledag/node
  (s/keys :req [::id ::size ::encoding]
          :opt [::links ::data]))


(def node-keys
  [::id ::size ::encoding ::links ::data])



;; ## Storage Protocol

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



;; ## Utility Functions

(defn reachable-size
  "Calculates the total size of data reachable from the given node. Expects a
  node map with `::size` and `::links` entries.

  Raw blocks and nodes with no links have a total size equal to their `::size`.
  Each link in the node's link table adds its `::link/rsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (::size node)]
    (->> (::links node)
         (keep ::link/rsize)
         (reduce + size))))
