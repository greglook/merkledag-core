(ns merkledag.graph
  "Core merkle graph types and protocol functions.

  A _blob_ is a record that represents a binary sequence and a cryptographic
  digest identifying it. Blobs have two main attributes:

  - `:content` a `ByteBuffer` containing the block contents.
  - `:id` a `Multihash` identifying the content.

  A _node_ is a blob which follows the merkledag protobuffer format.
  Nodes in the merkledag may have links to other nodes and some internal data.
  In addition to an id and content, nodes have two additional attributes:

  - `:links` a sequence of `MerkleLink`s to other nodes in the merkledag.
  - `:data` the data segment, which may be a parsed value or a raw `ByteBuffer`.

  Nodes _may_ have additional attributes which are not part of the serialized
  content, such as stat metadata."
  (:require
    [blobble.core :as blob]
    (merkledag
      [codec :as codec]
      [link :as link :refer [*link-table*]])
    [multihash.core :as multihash])
  (:import
    blobble.core.Blob
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(defn total-size
  "Calculates the total size of data reachable from the given node.

  Raw blobs and nodes with no links have a total size equal to their `:content`
  length.  Each link in the node's link table adds its `:tsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (blob/size node)]
    (->> (:links node)
         (map :tsize)
         (reduce (fnil + 0) size))))


(defn link
  "Constructs a new merkle link. The name should be a string. If no target is
  given, the name is looked up in the `*link-table*`. If it doesn't resolve to
  anything, the target will be `nil` and it will be a _broken_ link. If the
  target is a multihash, it is used directly. If it is a node, the id
  is used."
  ([name]
   (or (link/resolve name)
       (MerkleLink. name nil nil nil)))
  ([name target]
   (let [tsize (when (instance? Blob target)
                 (total-size target))]
     (link name target tsize)))
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



;; ## Merkle Graph Node

;; Nodes are `Blob` records which contain a link table with named multihashes
;; referring to other nodes, and a data segment with either an opaque byte
;; sequence or a parsed data structure value. A node is a Blob which has been
;; successfully decoded into (or encoded from) the protobuf encoding.
;;
;; - `:id`      multihash reference to the blob the node serializes to
;; - `:content` the canonical representation of this node
;; - `:links`   vector of MerkleLink values
;; - `:data`    the contained data value, structure, or raw bytes


(def link-type
  {'data/link
   {:description "Merkle links within an object"
    :reader link
    :writers {MerkleLink :name}}})


(def ^:dynamic *codec*
  "Current node serialization codec to use."
  nil)


(defn ->node
  "Constructs a new node from a sequence of merkle links and a data value. The
  codec is used to control serialization and other security features."
  [codec links data]
  (when-not (or (nil? links)
                (and (sequential? links)
                     (every? (partial instance? MerkleLink) links)))
    (throw (IllegalArgumentException.
             (str "Node links must be a sequence of merkle links, got: "
                  (pr-str links)))))
  (let [codec (update-in codec [:types] merge link-type)]
    (codec/encode codec links data)))


(defmacro node
  "Constructs a new merkle node. Uses the contextual `*graph-repo*` to serialize
  the node data."
  ([data]
   `(node nil ~data))
  ([links data]
   `(let [links# (binding [*link-table* nil] ~links)]
      (binding [*link-table* (vec links#)]
        (let [data# ~data]
          (->node *codec* *link-table* data#))))))
