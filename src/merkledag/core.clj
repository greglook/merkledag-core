(ns merkledag.core
  "Core library API."
  (:require
    [blocks.store.memory :refer [memory-block-store]]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [merkledag.cache :as cache]
    [merkledag.v1 :as v1]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [merkledag.store :as store])
  (:import
    merkledag.link.MerkleLink))


;; ## Constructors

(defn link?
  "Predicate which returns true if the argument is a `MerkleLink` object."
  [x]
  (instance? MerkleLink x))


(defn link
  "Construct a new merkle link to the given target value."
  [link-name target]
  (link/create link-name (link/identify target) (link/reachable-size target)))


(defn node*
  "Construct a new merkle node by serializing it to a block."
  [store links data]
  (store/format-block (:codec store) {::node/links links, ::node/data data}))



;; ## Node Storage API

(defn init-store
  "Constructs a new node store with default values.

  Options may include:

  - `:store`
    Block store to persist nodes to. Defaults to an in-memory store.
  - `:codec`
    Codec to serialize nodes with. Defaults to an EDN codec with basic types.
  - `:cache`
    Map of options to supply to construct a node cache. See
    `merkledag.node.cache/node-cache` for options."
  [& {:as opts}]
  (store/block-node-store
    :store (or (:store opts) (memory-block-store))
    :codec (or (:codec opts) (v1/node-codec (:types opts)))
    :cache (when (:cache opts)
             (atom (apply cache/node-cache {} (apply concat (:cache opts)))))))


(defn- resolve-path
  "Retrieve a node by recursively resolving a path through a sequence of nodes.
  The `path` should be a slash-separated string."
  [store root-id path not-found]
  (loop [node (node/-get-node store root-id)
         path (when path (str/split (str path) #"/+"))]
    (if node
      (if (seq path)
        (if-let [link (link/resolve-name (::node/links node) (first path))]
          ; Continue resolving.
          (recur (node/-get-node store (:target link)) (next path))
          ; Link was not found in node.
          not-found)
        ; No more path segments, this is the target node.
        node)
      ; Linked node was not found in store.
      not-found)))


(defn- apply-node-metadata
  "Enrich the link table and data in a node by attaching metadata about the
  other node properties."
  [node]
  (cond-> node
    (::node/links node)
      (update ::node/links
              vary-meta assoc
              ::node/id (::node/id node)
              ::node/size (::node/size node))
    (::node/data node)
      (update ::node/data
              vary-meta assoc
              ::node/id (::node/id node)
              ::node/links (::node/links node)
              ::node/size (::node/size node))))


(defn get-node
  "Retrieve the identified node from the store. Returns a node map, or
  `not-found` if the node was not found.

  The id may be any `link/Target` value. If a `path` is given, each slash
  separated segment is used to recursively look up nodes in the graph by
  walking the corresponding named links."
  ([store id]
   (get-node store id nil nil))
  ([store id path]
   (get-node store id path nil))
  ([store id path not-found]
   (when-let [id (link/identify id)]
     (let [node (resolve-path store id path not-found)]
       (if (identical? node not-found)
         not-found
         (apply-node-metadata node))))))


(defn get-links
  "Retrieve the links for the identified node. Returns the node's link table as
  a vector of `MerkleLink` values, or `not-found`.

  The id may be any `link/Target` value. If a `path` is given, each slash
  separated segment is used to recursively look up nodes in the graph by
  walking the corresponding named links."
  ([store id]
   (get-links store id nil nil))
  ([store id path]
   (get-links store id path nil))
  ([store id path not-found]
   (let [node (get-node store id path not-found)]
     (if (identical? node not-found)
       not-found
       (::node/links node)))))


(defn get-data
  "Retrieve the data for the identified node. Returns the node's data body.

  The id may be any `link/Target` value. If a `path` is given, each slash
  separated segment is used to recursively look up nodes in the graph by
  walking the corresponding named links."
  ([store id]
   (get-data store id nil nil))
  ([store id path]
   (get-data store id path nil))
  ([store id path not-found]
   (let [node (get-node store id path not-found)]
     (if (identical? node not-found)
       not-found
       (::node/data node)))))


(defn store-node!
  "Create a new node by serializing the links and data. Returns the stored node
  map, or nil if both links and data are nil."
  ([store node]
   (let [{:keys [::node/id ::node/links ::node/data]} node]
     (when (or (seq links) data)
       (->
         (if-let [id (or id (::node/id (meta links)) (::node/id (meta data)))]
           ; See if we can re-use an already-stored node.
           (let [extant (node/-get-node store id)]
             (if (and (= (seq links) (seq (::node/links extant)))
                      (= data (::node/data extant)))
               ; Links and data match, re-use stored node.
               extant
               ; Missing or some value mismatch, store a new node.
               (node/-store-node! store node)))
           ; No id metadata, store new node.
           (node/-store-node! store node))
         (apply-node-metadata)))))
  ([store links data]
   (store-node! store {::node/links links, ::node/data data})))


(defn delete-node!
  "Remove a node from the store.

  The id may be any `link/Target` value."
  [store id]
  (when-let [id (link/identify id)]
    (node/-delete-node! store id)))



;; ## Update Functions

(defn update-link
  "Returns an updated node map with the given link added, replacing any
  existing link with the same name. Returns a map with `:merkledag.node/links` and `:merkledag.node/data`
  values."
  [node new-link]
  (if new-link
    (let [name-match? #(= (::link/name new-link) (::link/name %))
          [before after] (split-with (complement name-match?) (::node/links node))]
      {::node/links (vec (concat before [new-link] (rest after)))
       ::node/data
       (walk/postwalk
         (fn link-updater [x]
           (if (and (link? x) (name-match? x))
             new-link
             x))
         (::node/data node))})
    node))


(defn update-node
  "Updates the given node by substituting in the given links and potentially
  running a function to update the body. If the given links have names, and
  links with matching names exist in the current node, they will be replaced
  with the new links. Otherwise, the links will be appended to the table.

  Returns an updated node map."
  ([store node links]
   (update-node store node links identity))
  ([store node links f & args]
   (->> (reduce update-link node links)
        (apply update ::node/data f args))))
