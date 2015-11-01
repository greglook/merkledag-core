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
    [blobble.store.memory :refer [memory-store]]
    (merkledag
      [codec :as codec]
      [link :as link :refer [*link-table*]])
    [multihash.core :as multihash])
  (:import
    blobble.core.Blob
    multihash.core.Multihash))


;; ## Graph Repository

;; The graph repository wraps a content-addressable blob store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord GraphRepo
  [store types])


;; Remove automatic constructor functions.
(ns-unmap *ns* '->GraphRepo)
(ns-unmap *ns* 'map->GraphRepo)


(defn graph-repo
  "Constructs a new merkledag graph repository. If no store is given, defaults
  to a new in-memory blob store. Any types given will override the core type
  plugins."
  ([]
   (graph-repo (memory-store)))
  ([store]
   (graph-repo store nil))
  ([store types]
   ; FIXME: can't use core-types here, causes circular dependency.
   (GraphRepo. store types #_(merge core-types types))))


(def ^:dynamic *graph-repo*
  "Current graph repository context."
  nil)


(defmacro with-repo
  "Execute the body with `*graph-repo*` bound to the given value."
  [repo & body]
  `(binding [*graph-repo* ~repo]
     ~@body))



;; ## Merkle Graph Nodes

;; Nodes are `Blob` records which contain a link table with named multihashes
;; referring to other nodes, and a data segment with either an opaque byte
;; sequence or a parsed data structure value. A node is a Blob which has been
;; successfully decoded into (or encoded from) the protobuf encoding.
;;
;; - `:id`      multihash reference to the blob the node serializes to
;; - `:content` the canonical representation of this node
;; - `:links`   vector of MerkleLink values
;; - `:data`    the contained data value, structure, or raw bytes


; This is pre-declared because the `IDeref` interface must be inlined in the
; `MerkleLink` type definition below, but the implementation of `get-node` must
; know how to construct `MerkleLink` values.
(declare get-node)


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



;; ## Merkle Graph Link

;; Links have three main properties. Note that **only** link-name and target
;; are used for equality and comparison checks!
;;
;; - `:name` is a string giving the link's name from an object link table.
;; - `:target` is the merklehash to which the link points.
;; - `:tsize` is the total number of bytes reachable from the linked blob.
;;   This should equal the sum of the target's links' tsizes, plus the size
;;   of the object itself.
;;
;; In the context of a repo, links can be dereferenced to look up their
;; contents from the store.
(deftype MerkleLink
  [_name _target _tsize _meta]

  Object

  (toString
    [this]
    (format "link:%s:%s:%s" _name (multihash/hex _target) (or _tsize "-")))

  (equals
    [this that]
    (cond
      (identical? this that) true
      (instance? MerkleLink that)
        (and (= _name   (._name   ^MerkleLink that))
             (= _target (._target ^MerkleLink that)))
      :else false))

  (hashCode
    [this]
    (hash-combine _name _target))


  Comparable

  (compareTo
    [this that]
    (if (= this that)
      0
      (compare [_name _target]
               [(:name that) (:target that)])))


  clojure.lang.IMeta

  (meta [_] _meta)


  clojure.lang.IObj

  (withMeta
    [_ meta-map]
    (MerkleLink. _name _target _tsize meta-map))


  clojure.lang.ILookup

  (valAt
    [this k not-found]
    (case k
      :name _name
      :target _target
      :tsize _tsize
      not-found))

  (valAt
    [this k]
    (.valAt this k nil))


  clojure.lang.IDeref

  (deref
    [this]
    (get-node *graph-repo* _target))


  clojure.lang.IPending

  (isRealized
    [this]
    false))


;; Remove automatic constructor function.
(ns-unmap *ns* '->MerkleLink)



;; ## Raw Constructors

(defn ->link
  "Constructs a `MerkleLink` value, validating the inputs."
  [name target tsize]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: "
                  (pr-str name)))))
  (when-not (instance? Multihash target)
    (throw (IllegalArgumentException.
             (str "Link target must be a multihash, got: "
                  (pr-str target)))))
  (when (and tsize (not (integer? tsize)))
    (throw (IllegalArgumentException.
             (str "Link size must be an integer, got: "
                  (pr-str tsize)))))
  (MerkleLink. name target tsize nil))


(defn ->node
  "Constructs a new node from a sequence of merkle links and a data value. The
  repo is used to control serialization and other security features."
  [repo links data]
  (when-not (or (nil? links)
                (and (sequential? links)
                     (every? (partial instance? MerkleLink) links)))
    (throw (IllegalArgumentException.
             (str "Node links must be a sequence of merkle links, got: "
                  (pr-str links)))))
  (when-let [node-bytes (codec/encode (:types repo) links data)]
    (assoc (blob/read! node-bytes)
           :links (some-> links vec)
           :data data)))



;; ## Magic Constructors

(defn link
  "Constructs a new merkle link. The name should be a string. If no target is
  given, the name is looked up in the `*link-table*`. If it doesn't resolve to
  anything, the target will be `nil` and the link will be _broken_. If the
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


(defmacro node
  "Constructs a new merkle node. Uses the contextual `*graph-repo*` to serialize
  the node data."
  ([data]
   `(node nil ~data))
  ([links data]
   ; FIXME: potential bug where realizing ~links may register links in an enclosing
   ; blob because *link-table* is non-nil.
   `(binding [*link-table* (vec ~links)]
      (let [data# ~data]
        (->node *graph-repo* *link-table* data#)))))



;; ## Graph Repo Functions

(defn get-node
  "Retrieve a node from the given repository's blob store, parsed by the repo's
  codec."
  [repo id]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot look up node for " (pr-str id)
                  " with no repo"))))
  (when-let [node (some->>
                    id
                    (blob/get (:store repo))
                    (codec/decode (:types repo)))]
    (update node :links (partial mapv #(->link (:name %) (:hash %) (:tsize %))))))


(defn put-node!
  "Store a node (or map with links and data) in the repository. Returns an
  updated blob record with the serialized node."
  [repo node]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot store node for " (pr-str (:id node))
                  " with no repo"))))
  (when node
    (let [{:keys [id content links data]} node]
      (if (and id content)
        (blob/put! (:store repo) node)
        (when (or links data)
          (blob/put! (:store repo) (->node repo links data)))))))
