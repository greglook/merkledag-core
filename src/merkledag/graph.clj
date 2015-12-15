(ns merkledag.graph
  "A graph is a collection of nodes with interconnected links."
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-store]]
    (merkledag
      [core :as merkle]
      [data :as data]
      [format :as format]
      [link :as link])))


;; The graph store wraps a content-addressable block store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord BlockGraph
  [store format]

  merkle/MerkleGraph

  (get-node
    [this id]
    (when-let [block (block/get store id)]
      (format/parse-node format block)))


  (put-node!
    [this node]
    (when-let [{:keys [id links data]} node]
      (if id
        (block/put! store node)
        (when (or links data)
          (block/put! store (format/format-node format links data))))))


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
  "Constructs a new merkle graph backed by a block store.

  If no store is given, this defaults to a new empty in-memory block store. Type
  extensions may be provided in an aditional argument, which will be merged into
  the core types."
  ([]
   (block-graph (memory-store)))
  ([store]
   (block-graph store nil))
  ([store types]
   (BlockGraph.
     store
     (format/protobuf-format (merge data/edn-types types)))))


(defmacro with-context
  "Executes `body` in the context of the given graph. Links will be resolved
  against the graph's store and nodes constructed from the graph's format."
  [graph & body]
  `(let [graph# ~graph]
     (binding [link/*get-node* (partial merkle/get-node graph#)
               merkle/*format* (:format graph#)]
       ~@body)))
