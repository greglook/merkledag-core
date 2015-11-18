(ns merkledag.graph
  "Core merkle graph types and protocol functions.

  A _block_ is a record that represents a binary sequence and a cryptographic
  digest identifying it. Blocks have two main attributes:

  - `:content` a `ByteBuffer` containing the block contents.
  - `:id` a `Multihash` identifying the content.

  A _node_ is a block which follows the merkledag protobuffer format.
  Nodes in the merkledag may have links to other nodes and some internal data.
  In addition to an id and content, nodes have two additional attributes:

  - `:links` a sequence of `MerkleLink`s to other nodes in the merkledag.
  - `:data` the data segment, which may be a parsed value or a raw `ByteBuffer`.

  Nodes _may_ have additional attributes which are not part of the serialized
  content, such as stat metadata."
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-store]]
    (merkledag
      [codec :as codec]
      [data :as data]
      [link :as link :refer [*link-table*]])
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(defmethod link/target Block
  [block]
  (:id block))


(def base-codec
  "Basic codec for creating nodes."
  {:types data/core-types})


(def ^:dynamic *codec*
  "Current node serialization codec to use."
  base-codec)



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
  codec is used to control serialization and other security features."
  [codec links data]
  (when-not (or (nil? links)
                (and (sequential? links)
                     (every? (partial instance? MerkleLink) links)))
    (throw (IllegalArgumentException.
             (str "Node links must be a sequence of merkle links, got: "
                  (pr-str links)))))
  (codec/encode codec links data))


(defmacro node
  "Constructs a new merkle node. Uses the contextual `*codec*` to serialize the
  node data."
  ([data]
   `(node nil ~data))
  ([links data]
   `(let [links# (binding [*link-table* nil] ~links)]
      (binding [*link-table* (vec links#)]
        (let [data# ~data]
          (node* *codec* *link-table* data#))))))


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
(defrecord GraphRepo
  [store codec])


;; Remove automatic constructor functions.
(ns-unmap *ns* '->GraphRepo)
(ns-unmap *ns* 'map->GraphRepo)


(defn graph-repo
  "Constructs a new merkledag graph repository. If no store is given, defaults
  to a new in-memory block store. Any types given will override the core type
  plugins."
  ([]
   (graph-repo (memory-store)))
  ([store]
   (graph-repo store nil))
  ([store codec]
   (GraphRepo. store (merge base-codec codec))))


(defn get-node
  "Retrieve a node from the given repository's block store, parsed by the repo's
  codec."
  [repo id]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot look up node for " (pr-str id)
                  " with no repo"))))
  (some->>
    id
    (block/get (:store repo))
    (codec/decode (:codec repo))))


(defn put-node!
  "Store a node (or map with links and data) in the repository. Returns an
  updated block record with the serialized node."
  [repo node]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot store node for " (pr-str (:id node))
                  " with no repo"))))
  (when node
    (let [{:keys [id content links data]} node]
      (if (and id content)
        (block/put! (:store repo) node)
        (when (or links data)
          (block/put! (:store repo) (node* (:codec repo) links data)))))))


(defmacro with-repo
  "Executes `body` in the context of the given repository. Links will be
  resolved against the store and nodes constructed from the repo codec."
  [repo & body]
  `(let [repo# ~repo]
     (binding [link/*get-node* (partial get-node repo#)
               *codec* (:codec repo#)]
       ~@body)))
