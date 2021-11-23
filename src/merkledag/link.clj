(ns merkledag.link
  "The edges in the DAG are represented with _links_ from one node to another.
  A merkle-link has a CID target, an optional name string, and an optional
  recursive 'reference size' value."
  (:refer-clojure :exclude [find resolve])
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [multiformats.cid :as cid])
  (:import
    multiformats.cid.ContentID))


;; ## Link Type

;; Links have three main properties. Note that **only** link-name and target
;; are used for equality and comparison checks!
;;
;; - `name` is a string giving the link's name from an object link table.
;; - `target` is the cid to which the link points.
;; - `rsize` is the total number of bytes reachable from the linked block.
;;   This should equal the sum of the target's links' rsizes, plus the size
;;   of the target node itself.
(deftype MerkleLink
  [name target rsize _meta]

  ;:load-ns true

  Object

  (toString
    [this]
    (format "link:%s:%s:%s" name (str target) (or rsize "-")))

  (equals
    [this that]
    (cond
      (identical? this that)
      true

      (instance? MerkleLink that)
      (and (= name (.name ^MerkleLink that))
           (= target (.target ^MerkleLink that)))

      :else false))

  (hashCode
    [this]
    (hash-combine (hash name) (hash target)))


  Comparable

  (compareTo
    [this that]
    (if (= this that)
      0
      (compare [name target]
               [(.name ^MerkleLink that) (.target ^MerkleLink that)])))


  java.io.Serializable


  clojure.lang.IObj

  (meta
    [_]
    _meta)

  (withMeta
    [_ meta-map]
    (MerkleLink. name target rsize meta-map))


  clojure.lang.ILookup

  (valAt
    [this k]
    (.valAt this k nil))

  (valAt
    [this k not-found]
    (case k
      (:name   ::name)   name
      (:target ::target) target
      (:rsize  ::rsize)  rsize
      not-found)))


(alter-meta! #'->MerkleLink assoc :private true)


(defn create
  "Constructs a `MerkleLink` value, validating the inputs."
  [name target rsize]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: "
                  (pr-str name)))))
  (when (str/includes? name "/")
    (throw (IllegalArgumentException.
             (str "Link name must not contain slashes: "
                  (pr-str name)))))
  (when (and target (not (instance? ContentID target)))
    (throw (IllegalArgumentException.
             (str "Link target must be a content identifier, got: "
                  (pr-str target)))))
  (when (and rsize (not (integer? rsize)))
    (throw (IllegalArgumentException.
             (str "Link size must be an integer, got: "
                  (pr-str rsize)))))
  (->MerkleLink name target rsize nil))


(defn link->form
  "Returns a vector representing the given link, suitable for serialization as
  part of a tagged literal value."
  [^MerkleLink link]
  (when link
    [(.name link) (.target link) (.rsize link)]))


(defn form->link
  "Reader for link vectors produced by `link->form`."
  [v]
  (when v
    (when-not (and (sequential? v) (= 3 (count v)))
      (throw (IllegalArgumentException.
               (str "Link form must be a sequential collection with three "
                    "elements: " (pr-str v)))))
    (apply create v)))


(defmethod print-method MerkleLink
  [link w]
  (print-method (tagged-literal 'merkledag/link (link->form link)) w))



;; ## Link Tables

(defn validate-table!
  "Validates certain invariants about link tables. Throws an exception on error,
  or returns the table if it is valid."
  [links]
  (let [by-name (group-by ::name links)]
    ;; throw exception if any links have the same name and different targets
    (when-let [bad-names (seq (filter #(< 1 (count (val %))) by-name))]
      (throw (ex-info (str "Cannot compact links with multiple targets for the "
                           "same name: " (str/join ", " (map first bad-names)))
                      {:bad-links (into {} bad-names)})))
    ;; throw exception if any link name contains '/'
    (when-let [bad-names (seq (filter #(str/includes? % "/") (keys by-name)))]
      (throw (ex-info (str "Some links in table have illegal names: "
                           (str/join ", " (map pr-str bad-names)))
                      {:bad-links bad-names}))))
  links)


(defn find
  "Walks the given data structure looking for links. Returns a set of the links
  discovered."
  [data]
  (let [links (volatile! (transient #{}))]
    (walk/postwalk
      (fn detect
        [x]
        (when (instance? MerkleLink x)
          (vswap! links conj! x))
        x)
      data)
    (persistent! @links)))


(defn compact
  "Attempts to convert a sequence of links into a compact, canonical form."
  [links]
  (->> links
       (filter ::target)
       (distinct)
       (sort-by (juxt ::name ::target))))


(defn collect-table
  "Constructs a link table from the given data. The ordered links passed will
  be placed at the beginning of the link table, in order. Additional links
  walked from the data value will be appended in a canonical order."
  [ordered-links data]
  (->> (find-links data)
       (concat ordered-links)
       (compact-links)
       (remove (set ordered-links))
       (concat ordered-links)
       (vec)))


(defn resolve
  "Resolves a link name against the given table. Returns nil if no matching
  link is found."
  [links name]
  (loop [links (seq links)]
    (when links
      (let [link (first links)]
        (if (and link (= name (::name link)))
          link
          (recur (next links)))))))



;; ## Link Indexes

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
    (or (identical? this that)
        (and (instance? LinkIndex that)
             (= index (.index ^LinkIndex that)))))

  (hashCode
    [this]
    (hash-combine (hash (class this)) (hash index)))


  clojure.lang.ILookup

  (valAt
    [this k not-found]
    (if (identical? :index k)
      index
      not-found))

  (valAt
    [this k]
    (.valAt this k nil)))


(alter-meta! #'->LinkIndex assoc :private true)


(defn link-index
  "Return a `LinkIndex` value pointing to the given link in the table."
  ([i]
   (LinkIndex. i))
  ([table link]
   (some->>
     table
     (keep-indexed #(when (= link %2) %1))
     (first)
     (->LinkIndex))))


(defn replace-links
  "Replaces all the links in a data structure with indexes into the given
  table. Throws an exception if any links are 'broken' because they were not
  found in the table."
  [table data]
  (walk/postwalk
    (fn replacer
      [x]
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
    (fn resolver
      [x]
      (if (instance? LinkIndex x)
        (or (nth table (:index x) nil)
            (throw (ex-info (str "No index in table for " x)
                            {:table table, :index x})))
        x))
    data))



;; ## Targeting Protocol

(defprotocol Target
  "Protocol for values which can be targeted by merkle links."

  (identify
    [target]
    "Return the CID identifying the target value.")

  (reachable-size
    [target]
    "Calculate the size of data in bytes reachable from the target."))


(extend-protocol Target

  nil
  (identify [_] nil)
  (reachable-size [_] nil)

  String
  (identify [s] (cid/parse s))
  (reachable-size [s] nil)

  ContentID
  (identify [cid] cid)
  (reachable-size [cid] nil)

  MerkleLink
  (identify [link] (::target link))
  (reachable-size [link] (::rsize link)))
