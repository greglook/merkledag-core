(ns merkledag.graph
  "Core merkle graph types and protocol functions.

  A _block_ is a record that represents a binary sequence and a cryptographic
  digest identifying it. Blocks have two primary attributes in addition to
  their byte content:

  - `:id` a _multihash_ identifying the block content.
  - `:size` an integer counting the number of bytes in the content.

  A _node_ is a block which follows the merkledag format. Nodes in the
  merkledag may have links to other nodes as well as some internal data. In
  addition to the block properties, nodes have two additional attributes:

  - `:links` a sequence of `MerkleLink`s to other nodes in the merkledag.
  - `:data` the data segment, which may be a parsed value or a raw byte
    sequence.

  Like blocks, nodes _may_ have additional attributes which are not part of the
  serialized content."
  (:require
    [blocks.core :as block]
    [merkledag.core :as merkle]
    [merkledag.link :as link :refer [*link-table*]])
  (:import
    blocks.data.Block))


(defmethod link/target Block
  [block]
  (:id block))



;; ## Block Graph Store

;; The graph store wraps a content-addressable block store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord BlockGraph
  [store format]

  merkle/MerkleGraph

  (get-node
    [this id]
    (when-let [block (block/-get this id)]
      (merkle/parse-node block)))


  (put-node!
    [this node]
    (when-let [{:keys [id links data]} node]
      (if id
        (block/put! store block)
        (when (or links data)
          (block/put! store (merkle/build-node format links data))))) )


  block/BlockStore

  (stat
    [this id]
    (block/stat store id))


  (-list
    [this opts]
    (block/-list store opts))


  (-get
    [this id]
    (block/-get store id))


  (put!
    [this block]
    (block/put! store block))


  (delete!
    [this id]
    (block/delete! store id)))


(defn block-graph
  [store format]
  ; TODO: check args
  (BlockGraph. store format))


(defmacro with-graph
  "Executes `body` in the context of the given graph. Links will be resolved
  against the store and nodes constructed from the format."
  [graph & body]
  `(let [graph# ~graph]
     (binding [link/*get-node* (partial block/get graph#)
               *format* (:format graph#)]
       ~@body)))
