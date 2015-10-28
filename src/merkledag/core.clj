(ns merkledag.core
  "MerkleDAG types and serialization functions."
  (:require
    [blobble.core :as blob]
    [flatland.protobuf.core :as pb]
    [multihash.core :as multihash])
  (:import
    (com.google.protobuf
      ByteString)
    (merkledag.proto
      Merkledag$MerkleLink
      Merkledag$MerkleNode)
    (multihash.core
      Multihash)))


(def ^:dynamic *blob-store* nil)
(def ^:dynamic *link-table* nil)


#_
(merkledag/node
  [(merkledag/link "@context" (multihash/decode "10a8b72e1ff20e..."))]
  {:type :finance/transaction
   :title "SCHZ - Reinvest Dividend"
   :description "Automatic dividend reinvestment."
   :time/at #inst "2013-10-08T00:00:00Z"
   :finance.transaction/entries
   [(merkledag/link "posting-1" posting-node-1)
    (merkledag/link "posting-2" posting-node-2)]
   :finance.transaction/state :finance.transaction.state/cleared})

; Serializing this involves walking the structure to translate anything wrapped
; in a MerkleLink into a serialized object with a known hash to link to.




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


  IMeta

  (meta [_] _meta)


  IObj

  (withMeta
    [_ meta-map]
    (MerkleLink. _name _target _tsize meta-map))


  ILookup

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


  IDeref

  (deref
    [this]
    (when-not *blob-store*
      (throw (IllegalStateException.
               (str "Cannot look up node for " this
                    " with no associated repo"))))
    (blob/get *blob-store* _target)))


;; Remove automatic constructor function.
(ns-unmap *ns* '->MerkleLink)



;; ## Merkle Graph Node

;; Nodes contain a link table with named multihashes referring to other nodes,
;; and a data segment with either an opaque byte sequence or a parsed data
;; structure value. A node is essentially a Blob which has been successfully
;; decoded into (or encoded from) the protobuf encoding.
;;
;; - `:id`      multihash reference to the blob the node serializes to
;; - `:content` the canonical representation of this node
;; - `:links`   vector of MerkleLink values
;; - `:data`    the contained data value, structure, or raw bytes
;;
(defrecord MerkleNode
  [id content links data])


(defmacro node
  "Constructs a new merkle node."
  ([data]
   (node nil data))
  ([links data]
   `(binding [*link-table* (vec links)]
      (let [data# ~data
            links# (concat ~links *link-table*)]
        (-> (codec/node->blob links# data#)
            (map->MerkleNode)
            (assoc :links links#
                   :data data#))))))


(defn- resolve-link
  "Resolves a link against the current `*link-table*`, if any."
  [name]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: " (pr-str name)))))
  (some #(when (= name (:name %)) %)
        *link-table*))


(defn- resolve-target
  [target]
  (cond
    (nil? target)
      nil
    (instance? Multihash target)
      target
    (instance? Blob target)
      (do (when (and *blob-store* (nil? (blob/stat *blob-store* (:id target))))
            (blob/put! *blob-store* target))
          (:id target))
    :else
      (throw (IllegalArgumentException.
               (str "Cannot resolve type " (class target)
                    " as a merkle link target.")))))


(defn link
  "Constructs a new merkle link. The name should be a string. If no target is
  given, the name is looked up in the `*link-table*`. If it doesn't resolve to
  anything, the target will be `nil` and the link will be _broken_. If the
  target is a multihash, it is used directly. If it is a `MerkleNode`, the id
  is used."
  ([name]
   (or (resolve-link name)
       (MerkleLink. name nil nil nil)))
  ([name target]
   (link name target nil))
  ([name target tsize]
   (let [extant (resolve-link name)
         target' (resolve-target target)
         tsize' (or tsize (:tsize extant))]
     (if extant
       (if (= target' (:target extant))
         extant
         (throw (IllegalStateException.
                  (str "Can't link " name " to " target'
                       ", already points to " (:target extant)))))
       (let [link' (MerkleLink. name target' tsize' nil)]
         (when *link-table*
           (set! #'*link-table* (conj *link-table* link')))
         link')))))
