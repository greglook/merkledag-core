(ns merkledag.graph
  "A graph is a collection of nodes with interconnected links."
  (:require
    [blocks.core :as block]
    [merkledag.core :as merkle]
    [merkledag.link :as link :refer [*link-table*]])
  (:import
    blocks.data.Block))


(defprotocol MerkleGraph
  "Protocol for interacting with a graph of merkle nodes."

  (get-node
    [graph id]
    "Retrieves and parses the block identified by the given multihash.")

  (put-node!
    [graph node]
    "Stores a node in the graph for later retrieval. Should accept a pre-built
    node block or a map with `:links` and `:data` entries."))



(defmethod link/target Block
  [block]
  (:id block))



;; ## Block Graph Store

;; The graph store wraps a content-addressable block store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord BlockGraph
  [store format]

  MerkleGraph

  (get-node
    [this id]
    (when-let [block (block/-get this id)]
      (merkle/parse-node format block)))


  (put-node!
    [this node]
    (when-let [{:keys [id links data]} node]
      (if id
        (block/put! store node)
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
     (binding [link/*get-node* (partial get-node graph#)
               merkle/*format* (:format graph#)]
       ~@body)))
