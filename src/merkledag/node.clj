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
  "Collection of attributes that make up a merkledag node map."
  [::id ::size ::encoding ::links ::data])



;; ## Storage API

(defprotocol ^:no-doc NodeStore
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


(defn get-node
  "Retrieve the identified node from the store. Returns a node map.

  The id may be any `link/Target` value."
  [store id]
  (when-let [id (link/identify id)]
    (-get-node store id)))


(defn get-links
  "Retrieve the links for the identified node. Returns the node's link table as
  a vector of `MerkleLink` values.

  The id may be any `link/Target` value."
  [store id]
  (let [id (link/identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (::links node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node))))))


(defn get-data
  "Retrieve the data for the identified node. Returns the node's data body.

  The id may be any `link/Target` value."
  [store id]
  (let [id (link/identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (::data node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node)
          ::links (:links node))))))


(defn store-node!
  "Create a new node by serializing the links and data. Returns the stored node
  map, or nil if both links and data are nil."
  ([store node]
   (let [{:keys [::id ::links ::data]} node]
     (when (or (seq links) data)
       (if-let [id (or id (::id (meta links)) (::id (meta data)))]
         ; See if we can re-use an already-stored node.
         (let [extant (-get-node store id)]
           (if (and (= (seq links) (seq (::links extant)))
                    (= data (::data extant)))
             ; Links and data match, re-use stored node.
             extant
             ; Missing or some value mismatch, store a new node.
             (-store-node! store node)))
         ; No id metadata, store new node.
         (-store-node! store (cond-> node
                               (nil? (::links node))
                                 (dissoc ::links)
                               (nil? (::data node))
                                 (dissoc ::data)))))))
  ([store links data]
   (store-node! store {::links links, ::data data})))


(defn delete-node!
  "Remove a node from the store.

  The id may be any `link/Target` value."
  [store id]
  (when-let [id (link/identify id)]
    (-delete-node! store id)))



;; ## Utility Functions

(defn reachable-size
  "Calculates the total size of data reachable from the given node. Expects a
  node map with `::size` and `::links` entries.

  Raw blocks and nodes with no links have a total size equal to their `::size`.
  Each link in the node's link table adds its `::link/rsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (if-let [size (or (::size node) (:size node))]
    (->> (::links node)
         (keep ::link/rsize)
         (reduce + size))))
