(ns merkledag.graph
  "A graph is a collection of nodes with interconnected links."
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-store]]
    (merkledag
      [core :as merkle]
      [data :as data]
      [link :as link])
    [merkledag.codec.edn :refer [edn-codec]]
    [merkledag.format.protobuf :refer [protobuf-format]]
    [multicodec.codecs :as codecs])
  (:import
    blocks.data.Block))


(defmethod link/target Block
  [block]
  (:id block))


(defn select-encoder
  "Choose text codec for strings, bin codec for raw bytes, and EDN for
  everything else."
  [_ value]
  (cond
    (string? value)
      :text
    (or (instance? (Class/forName "[B") value)
        (instance? java.nio.ByteBuffer value)
        (instance? blocks.data.PersistentBytes value))
      :bin
    :else
      :edn))


(defmacro with-context
  "Executes `body` in the context of the given graph. Links will be resolved
  against the graph's store and nodes constructed from the graph's format."
  [graph & body]
  `(let [graph# ~graph]
     (binding [link/*get-node* (partial merkle/get-node graph#)
               merkle/*format* (:format graph#)]
       ~@body)))



;; ## Block Graph Store

;; The graph store wraps a content-addressable block store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord BlockGraph
  [store format]

  merkle/MerkleGraph

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
          (block/put! store (merkle/build-node format links data))))))


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
  ([]
   (block-graph (memory-store)))
  ([store]
   (block-graph store nil))
  ([store types]
   (BlockGraph.
     store
     (protobuf-format
       (assoc (codecs/mux-codec
                :edn  (edn-codec (merge data/core-types types))
                :text (codecs/text-codec)
                :bin  (codecs/bin-codec))
              :select-encoder select-encoder)))))
