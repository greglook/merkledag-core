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
    [merkledag.link :as link :refer [*link-table*]])
  (:import
    merkledag.link.MerkleLink))


; TODO: figure out where this should live, and whether it should wrap multicodec.
(defprotocol NodeFormat
  "Protocol for formatters which can construct and decode node records."

  (build-node
    [formatter links data]
    "Encodes the links and data of a node into a block value.")

  (parse-node
    [formatter block]
    "Decodes the block to determine the node structure. Returns an updated block
    value with `:links` and `:data` set appropriately."))



;; ## Utility Functions

(defn total-size
  "Calculates the total size of data reachable from the given node.

  Raw blocks and nodes with no links have a total size equal to their `:size`.
  Each link in the node's link table adds its `:tsize` to the total. Returns
  `nil` if no node is given."
  [node]
  (when-let [size (:size node)]
    (->> (:links node)
         (map :tsize)
         (reduce (fnil + 0 0) size))))



;; ## Constructors

(def ^:dynamic *format*
  "Current node serialization format to use."
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
          (build-node format# *link-table* data#))))))


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
