(ns merkledag.link
  (:require
    [blocks.core :as block]
    [clojure.walk :as walk]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    multihash.core.Multihash))


;; ## Link Type

;; Links have three main properties. Note that **only** link-name and target
;; are used for equality and comparison checks!
;;
;; - `:name` is a string giving the link's name from an object link table.
;; - `:target` is the merklehash to which the link points.
;; - `:tsize` is the total number of bytes reachable from the linked block.
;;   This should equal the sum of the target's links' tsizes, plus the size
;;   of the object itself.
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
    (hash-combine (hash _name) (hash _target)))


  Comparable

  (compareTo
    [this that]
    (if (= this that)
      0
      (compare [_name _target]
               [(:name that) (:target that)])))


  clojure.lang.IObj

  (meta [_] _meta)

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
    (.valAt this k nil)))


;; Remove automatic constructor function.
(ns-unmap *ns* '->MerkleLink)


(defn create
  "Constructs a `MerkleLink` value, validating the inputs."
  [name target tsize]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: "
                  (pr-str name)))))
  (when (and target (not (instance? Multihash target)))
    (throw (IllegalArgumentException.
             (str "Link target must be a multihash, got: "
                  (pr-str target)))))
  (when (and tsize (not (integer? tsize)))
    (throw (IllegalArgumentException.
             (str "Link size must be an integer, got: "
                  (pr-str tsize)))))
  (MerkleLink. name target tsize nil))


(defn write-link
  "Returns a vector representing the given link, suitable for serialization as
  part of a tagged literal value."
  [link]
  [(:name link) (:target link) (:tsize link)])


(defn read-link
  "Reader for link vectors produced by `write-link`."
  [v]
  (when v
    (when-not (and (sequential? v) (= 3 (count v)))
      (throw (IllegalArgumentException.
               (str "Link form must be a sequential collection with three "
                    "elements: " (pr-str v)))))
    (apply create v)))



;; ## Link Table

;; This type represents a simple indexed pointer into the link table. It is used
;; to replace actual links before values are encoded, and replaced with real
;; links from the table when decoding.
(deftype LinkIndex
  [^long index]

  Object

  (toString
    [this]
    (format "link-index:%d" index))

  (equals
    [this that]
    (or (identical? this that) true
        (and (instance? LinkIndex that)
             (= index (.index ^LinkIndex that)))))

  (hashCode
    [this]
    (hash-combine (hash (class this)) (hash index)))


  clojure.lang.ILookup

  (valAt
    [this k not-found]
    (if (= :index k) index not-found))

  (valAt
    [this k]
    (.valAt this k nil)))


;; Remove automatic constructor function.
(ns-unmap *ns* '->LinkIndex)


(defn link-index
  "Return a `LinkIndex` value pointing to the given link in the table."
  ([i]
   (LinkIndex. i))
  ([table link]
   (some->>
     table
     (keep-indexed #(when (= link %2) %1))
     (first)
     (LinkIndex.))))


(defn replace-links
  "Replaces all the links in a data structure with indexes into the given
  table. Throws an exception if any links are 'broken' because they were not
  found in the table."
  [table data]
  (walk/postwalk
    (fn replacer [x]
      (if (instance? MerkleLink x)
        (or (link-index table x)
            (throw (ex-info (str "No link in table matching " x)
                            {:table table, :link x})))
        x))
    data))


(defn resolve-indexes
  "Replaces all the link indexes in a data structure with link values resolved
  against the given table. Throws an exception if any links are 'broken' because
  the index is outside the table."
  [table data]
  (walk/postwalk
    (fn resolver [x]
      (if (instance? LinkIndex x)
        (or (nth table (:index x))
            (throw (ex-info (str "No index in table for " x)
                            {:table table, :index x})))
        x))
    data))


(defn resolve-name
  "Resolves a link name against the given table. Returns nil if no matching
  link is found."
  [table name]
  (when name
    (first (filter #(= (str name) (:name %)) table))))



;; ## Utility Functions

(defn update-links
  "Returns an updated vector of links with the given link added, replacing any
  existing link with the same name."
  [links new-link]
  (if new-link
    (let [[before after] (split-with #(not= (:name new-link) (:name %)) links)]
      (vec (concat before [new-link] (rest after))))
    links))


(defn total-size
  "Calculates the total size of data reachable from the given node. Expects a
  block with `:size` and `:links` entries.

  Raw blocks and nodes with no links have a total size equal to their `:size`.
  Each link in the node's link table adds its `:tsize` to the total. Returns
  `nil` if no node is given."
  [node]
  (when-let [size (:size node)]
    (->> (:links node)
         (map :tsize)
         (reduce (fnil + 0 0) size))))


(defprotocol Target
  "Protocol for values which can be targeted by a merkle link."

  (link-to
    [target name]
    "Constructs a new named merkle link to the given target."))


(extend-protocol Target

  Multihash
  (link-to
    [mhash name]
    (create name mhash nil))

  MerkleLink
  (link-to
    [link name]
    (create name (:target link) (:tsize link)))

  Block
  (link-to
    [block name]
    (create name (:id block) (total-size block))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MerkleLink)
(ns-unmap *ns* '->LinkIndex)
