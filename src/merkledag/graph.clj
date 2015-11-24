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
    [merkledag.link :as link :refer [*link-table*]]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    merkledag.link.MerkleLink
    multihash.core.Multihash))


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


(defmethod link/target Block
  [block]
  (:id block))


(def ^:dynamic *format*
  "Current node serialization format to use."
  nil)



;; ## Utility Functions

(defn total-size
  "Calculates the total size of data reachable from the given node.

  Raw blocks and nodes with no links have a total size equal to their `:content`
  length.  Each link in the node's link table adds its `:tsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (:size node)]
    (->> (:links node)
         (map :tsize)
         (reduce (fnil + 0) size))))



;; ## Constructors

(defn node*
  "Constructs a new node from a sequence of merkle links and a data value. The
  formatter is used to control serialization and other security features."
  [formatter links data]
  (when-not (or (nil? links)
                (and (sequential? links)
                     (every? (partial instance? MerkleLink) links)))
    (throw (IllegalArgumentException.
             (str "Node links must be a sequence of merkle links, got: "
                  (pr-str links)))))
  (build-node formatter links data))


(defmacro node
  "Constructs a new merkle node. Uses the contextual `*format*` to serialize the
  node data."
  ([data]
   `(node nil ~data))
  ([links data]
   `(let [links# (binding [*link-table* nil] ~links)]
      (binding [*link-table* (vec links#)]
        (let [data# ~data]
          (node* *format* *link-table* data#))))))


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



;; ## Graph Repository

;; The graph repository wraps a content-addressable block store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defn graph-repo
  [store format]
  {:store store
   :format format})


(defn get-node
  "Retrieve a node from the given repository's block store, parsed by the repo's
  format."
  [repo id]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot look up node for " (pr-str id)
                  " with no repo"))))
  (some->>
    id
    (block/get (:store repo))
    (parse-node (:format repo))))


(defn put-node!
  "Store a node (or map with links and data) in the repository. Returns an
  updated block record with the serialized node."
  [repo node]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot store node for " (pr-str (:id node))
                  " with no repo"))))
  (when-let [{:keys [id links data]} node]
    (if id
      (block/put! (:store repo) node)
      (when (or links data)
        (block/put! (:store repo) (node* (:format repo) links data))))))


(defmacro with-repo
  "Executes `body` in the context of the given repository. Links will be
  resolved against the store and nodes constructed from the repo format."
  [repo & body]
  `(let [repo# ~repo]
     (binding [link/*get-node* (partial get-node repo#)
               *format* (:format repo#)]
       ~@body)))
