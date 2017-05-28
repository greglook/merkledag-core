(ns merkledag.link
  "The edges in the DAG are represented with _links_ from one node to another.
  A merkle-link has a multihash target, an optional name string, and a recursive
  'total size' value."
  (:require
    [clojure.future :refer [nat-int?]]
    [clojure.spec :as s]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [multihash.core :as multihash])
  (:import
    multihash.core.Multihash))


(s/def ::name string?)
(s/def ::target #(instance? Multihash %))
(s/def ::rsize (s/nilable nat-int?))



;; ## Link Type

;; Links have three main properties. Note that **only** link-name and target
;; are used for equality and comparison checks!
;;
;; - `:name` is a string giving the link's name from an object link table.
;; - `:target` is the merklehash to which the link points.
;; - `:rsize` is the total number of bytes reachable from the linked block.
;;   This should equal the sum of the target's links' rsizes, plus the size
;;   of the object itself.
(deftype MerkleLink
  [name target rsize _meta]

  :load-ns true

  Object

  (toString
    [this]
    (format "link:%s:%s:%s" name (multihash/hex target) (or rsize "-")))

  (equals
    [this that]
    (cond
      (identical? this that) true
      (instance? MerkleLink that)
        (and (= name   (.name   ^MerkleLink that))
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
               [(:name that) (:target that)])))


  clojure.lang.IObj

  (meta [_] _meta)

  (withMeta
    [_ meta-map]
    (MerkleLink. name target rsize meta-map))


  clojure.lang.ILookup

  (valAt
    [this k not-found]
    (case k
      :name name
      :target target
      :rsize rsize
      not-found))

  (valAt
    [this k]
    (.valAt this k nil)))


(alter-meta! #'->MerkleLink assoc :private true)


(defn create
  "Constructs a `MerkleLink` value, validating the inputs."
  [name target rsize]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: "
                  (pr-str name)))))
  (when (str/index-of name "/")
    (throw (IllegalArgumentException.
             (str "Link name must not contain slashes: "
                  (pr-str name)))))
  (when (and target (not (instance? Multihash target)))
    (throw (IllegalArgumentException.
             (str "Link target must be a multihash, got: "
                  (pr-str target)))))
  (when (and rsize (not (integer? rsize)))
    (throw (IllegalArgumentException.
             (str "Link size must be an integer, got: "
                  (pr-str rsize)))))
  (->MerkleLink name target rsize nil))


(defn link->form
  "Returns a vector representing the given link, suitable for serialization as
  part of a tagged literal value."
  [link]
  (when link
    [(:name link) (:target link) (:rsize link)]))


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
  (print-method (tagged-literal 'data/link (link->form link)) w))


(defn merkle-link?
  "Predicate which returns true if the argument is a `MerkleLink` object."
  [x]
  (instance? MerkleLink x))


(s/def :merkledag/link
  (s/and merkle-link?
         ; Can't use s/keys because links aren't maps
         #(s/valid? ::name (:name %))
         #(s/valid? ::target (:target %))
         #(s/valid? ::rsize (:rsize %))))



;; ## Link Tables

(s/def ::table
  (s/coll-of :merkledag/link :kind vector? :min-count 1))


(defn validate-links!
  "Validates certain invariants about link tables. Throws an exception on error,
  or returns the table if it is valid."
  [link-table]
  (let [by-name (group-by :name link-table)]
    ; throw exception if any links have the same name and different targets
    (when-let [bad-names (seq (filter #(< 1 (count (val %))) by-name))]
      (throw (ex-info (str "Cannot compact links with multiple targets for the "
                           "same name: " (str/join ", " (map first bad-names)))
                      {:bad-links (into {} bad-names)})))
    ; throw exception if any link name contains '/'
    (when-let [bad-names (seq (filter #(str/index-of % "/") (keys by-name)))]
      (throw (ex-info (str "Some links in table have illegal names: "
                           (str/join ", " (map pr-str bad-names)))
                      {:bad-links bad-names}))))
  link-table)


(defn find-links
  "Walks the given data structure looking for links. Returns a set of the links
  discovered."
  [data]
  (let [links (volatile! (transient #{}))]
    (walk/postwalk
      (fn link-detector [x]
        (when (merkle-link? x)
          (vswap! links conj! x))
        x)
      data)
    (persistent! @links)))


(defn compact-links
  "Attempts to convert a sequence of links into a compact, canonical form."
  [link-table]
  (->> link-table
       (remove (comp nil? :target))
       (distinct)
       (sort-by (juxt :name :target))))


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


(defn resolve-name
  "Resolves a link name against the given table. Returns nil if no matching
  link is found."
  [link-table link-name]
  (when link-name
    (first (filter #(= (str link-name) (:name %)) link-table))))


(defn update-link
  "Returns an updated node map with the given link added, replacing any
  existing link with the same name. Returns a map with `:links` and `:data`
  values."
  [node new-link]
  (if new-link
    (let [name-match? #(= (:name new-link) (:name %))
          [before after] (split-with (complement name-match?) (:links node))]
      {:links (vec (concat before [new-link] (rest after)))
       :data (walk/postwalk
               (fn link-updater [x]
                 (if (and (merkle-link? x) (name-match? x))
                   new-link
                   x))
               (:data node))})
    node))



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
    (if (= :index k) index not-found))

  (valAt
    [this k]
    (.valAt this k nil)))


(alter-meta! #'->LinkIndex assoc :private true)


(defn link-index
  "Return a `LinkIndex` value pointing to the given link in the table."
  ([i]
   (LinkIndex. i))
  ([link-table link]
   (some->>
     link-table
     (keep-indexed #(when (= link %2) %1))
     (first)
     (->LinkIndex))))


(defn replace-links
  "Replaces all the links in a data structure with indexes into the given
  table. Throws an exception if any links are 'broken' because they were not
  found in the table."
  [link-table data]
  (walk/postwalk
    (fn replacer [x]
      (if (merkle-link? x)
        (or (link-index link-table x)
            (throw (ex-info (str "No link in table matching " x)
                            {:link-table link-table, :link x})))
        x))
    data))


(defn resolve-indexes
  "Replaces all the link indexes in a data structure with link values resolved
  against the given table. Throws an exception if any links are 'broken' because
  the index is outside the table."
  [link-table data]
  (walk/postwalk
    (fn resolver [x]
      (if (instance? LinkIndex x)
        (or (nth link-table (:index x) nil)
            (throw (ex-info (str "No index in table for " x)
                            {:link-table link-table, :index x})))
        x))
    data))
