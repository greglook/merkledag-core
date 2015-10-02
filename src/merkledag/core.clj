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



#_
(merkledag/node
  {:extra-links [(merkledag/link "@context" (multihash/decode "10a8b72e1ff20e..."))]}
  {:type :finance/transaction
   :title "SCHZ - Reinvest Dividend"
   :description "Automatic dividend reinvestment."
   :time/at #inst "2013-10-08T00:00:00Z"
   :finance.transaction/entries
   [(merkledag/link "posting-1"
                    (merkledag/node
                      {:type :finance/posting
                       :title ... }))
    (merkledag/link "posting-2"
                    (merkledag/node
                      {:type :finance/posting
                       :title ... }))]
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
;; If created in the context of a repo, links may be dereferenced to look up
;; their contents from the store.
(deftype MerkleLink
  [_name _target _tsize _repo _meta]

  Object

  (toString
    [this]
    (format "link:%s:%s:%d" _name (multihash/hex _target) _tsize))

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
    (MerkleLink. _name _target _tsize _repo meta-map))


  ILookup

  (valAt
    [this k not-found]
    (case k
      :name _name
      :target _target
      :tsize _tsize
      :repo _repo
      not-found))

  (valAt
    [this k]
    (.valAt this k nil))


  IDeref

  (deref
    [this]
    (when-not _repo
      (throw (IllegalStateException.
               (str "Cannot look up node for " this
                    " with no associated repo"))))
    ; TODO: fetch blob from store
    (throw (RuntimeException. "Not Yet Implemented"))))


;; Remove automatic constructor function.
(ns-unmap *ns* '->MerkleLink)


(defn link
  "Constructs a new merkle link. The name should be a string. If the target is
  not a multihash value, this is considered a _pending_ link."
  ([name target]
   (link name target nil))
  ([name target tsize]
   ; TODO: use a dynamic var to track "Current repo"
   (MerkleLink. name target tsize nil nil)))



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
(defrecord MerkleNode
  [id content links data])
