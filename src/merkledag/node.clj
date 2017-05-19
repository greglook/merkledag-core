(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [blocks.core :as block]
    [clojure.future :refer :all]
    [clojure.spec :as s]
    [merkledag.codec.cbor :refer [cbor-codec]]
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.codec.node :refer [node-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    java.io.ByteArrayInputStream
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(s/def ::id #(instance? Multihash %))
(s/def ::size pos-int?)
(s/def ::encoding (s/nilable (s/coll-of string? :kind vector?)))
(s/def ::links (s/coll-of link/merkle-link? :kind vector?))
(s/def ::data coll?)

(s/def :merkledag/node
  (s/keys :req-un [::id ::size ::encoding]
          :opt-un [::links ::data]))


(def default-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :cbor/tag 68
    :cbor/writers {Multihash multihash/encode}
    :edn/writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}
    :cbor/tag 69}

   'data/link-index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}
    :cbor/tag 72}})



;; ## Identity Protocol

(defprotocol Identifiable
  "Protocol for values which can be resolved to a multihash value in order to
  uniquely identify a node in the graph."

  (identify
    [value]
    "Return the multihash identifying the value."))


(extend-protocol Identifiable

  nil
  (identify [_] nil)

  multihash.core.Multihash
  (identify [m] m)

  blocks.data.Block
  (identify [b] (:id b))

  merkledag.link.MerkleLink
  (identify [l] (:target l)))



;; ## Node Storage API

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

; TODO: flush?


(defn get-node
  "Retrieve the identified node from the store. Returns a node map.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (-get-node store id)))


(defn get-links
  "Retrieve the links for the identified node. Returns the node's link table as
  a vector of `MerkleLink` values.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (:links node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node))))))


(defn get-data
  "Retrieve the data for the identified node. Returns the node's data body.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (-get-node store id))]
      (some->
        (:data node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node)
          ::links (:links node))))))


(defn store-node!
  "Create a new node by serializing the links and data. Returns the stored node
  map, or nil if both links and data are nil."
  ([store data]
   (store-node! store nil data))
  ([store links data]
   (when (or links data)
     (-store-node! store links data))))


(defn delete-node!
  "Remove a node from the store.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (-delete-node! store id)))



;; ## Node Utilities

(defn total-size
  "Calculates the total size of data reachable from the given node. Expects a
  node map with `::size` and `::links` entries.

  Raw blocks and nodes with no links have a total size equal to their `::size`.
  Each link in the node's link table adds its `::link/tsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (::size node)]
    (->> (::links node)
         (keep ::link/tsize)
         (reduce + size))))
