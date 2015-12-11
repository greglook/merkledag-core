(ns merkledag.core
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
    [clojure.string :as str]
    (merkledag
      [format :as format]
      [link :as link :refer [*link-table*]]))
  (:import
    merkledag.link.MerkleLink))

;; It should be simple to:
;; - Create a "buffer" on top of an existing graph.
;; - Take some root pins into the graph.
;; - 'Mutate' the pins by updating into the nodes by resolving paths.
;; - Clean up the buffer by garbage collecting from the mutated pins.
;; - 'Flush' the buffer by writing all the blocks to the backing store.


;; ## Protocols

(defprotocol MerkleGraph
  "Protocol for interacting with a graph of merkle nodes."

  (get-node
    [graph id]
    "Retrieves and parses the block identified by the given multihash.")

  (put-node!
    [graph node]
    "Stores a node in the graph for later retrieval. Should accept a pre-built
    node block or a map with `:links` and `:data` entries."))



;; ## Utility Functions

(defn link?
  "Returns true if the value is a `MerkleLink`."
  [value]
  (instance? MerkleLink value))


(defn get-path
  "Retrieve a node by recursively resolving a path through a sequence of nodes."
  [graph root & path]
  (loop [id root
         path (->> path (str/join "/") (str/split #"/"))]
    (if (seq path)
      (if-let [node (get-node graph id)]
        (let [segment (first path)
              target (link/resolve segment (:links node))]
          (when-not target
            (throw (ex-info (str "Node " id " has no link named " (pr-str segment))
                            {:node id, :link segment})))
          (recur target (rest path)))
        (throw (ex-info (str "Linked node " id " is not available")
                        {:node id})))
      id)))


(defn total-size
  "Calculates the total size of data reachable from the given node.

  Raw blocks and nodes with no links have a total size equal to their `:size`.
  Each link in the node's link table adds its `:tsize` to the total. Returns
  `nil` if no node is given."
  [node]
  ; TODO: support links
  (when-let [size (:size node)]
    (->> (:links node)
         (map :tsize)
         (reduce (fnil + 0 0) size))))



;; ## Value Constructors

(def ^:dynamic *format*
  "Dynamic node serialization format to use."
  nil)


(defmacro node
  "Constructs a new merkle node. Handles binding of the link table to capture
  links declared as part of the data. Any links passed as an argument will be
  placed at the beginning of the link segment."
  ([data]
   `(node *format* nil ~data))
  ([extra-links data]
   `(node *format* ~extra-links ~data))
  ([format extra-links data]
   `(let [format# ~format
          links# (binding [*link-table* nil] ~extra-links)]
      (binding [*link-table* (vec links#)]
        (let [data# ~data]
          (format/build-node format# *link-table* data#))))))


(defn link
  "Constructs a new merkle link. The name should be a string. If no target is
  given, the name is looked up in the `*link-table*`. If it doesn't resolve to
  anything, the target will be `nil` and it will be a _broken_ link. If the
  target is a multihash, it is used directly. If it is a node, the id
  is used."
  ([name]
   (link/read-link name))
  ([name target]
   (link name target (total-size target)))
  ([name target tsize]
   (let [extant (link/resolve name)
         target' (link/target target)
         tsize' (or tsize (:tsize extant))]
     (if extant
       (if (= target' (:target extant))
         extant
         (throw (IllegalStateException.
                  (str "Can't link " name " to " target'
                       ", already points to " (:target extant)))))
       (let [link' (MerkleLink. name target' tsize' nil)]
         (when *link-table*
           (set! *link-table* (conj *link-table* link')))
         link')))))
