(ns merkledag.core
  "Core merkle graph types and protocol functions.

  A _block_ is a record that represents a binary sequence and a cryptographic
  digest identifying it. Blocks have two primary attributes in addition to
  their byte content:

  - `:id` a _multihash_ identifying the block content.
  - `:size` an integer counting the number of bytes in the content.

  A _node_ is a block which follows the merkledag format. Nodes in the
  merkledag may have links to other nodes as well as some internal data. In
  addition to the block properties, nodes have one mandatory and two optional
  attributes:

  - `:encoding` the multicodec headers the content is actually encoded with.
  - `:links` a sequence of `MerkleLink`s to other nodes in the merkledag.
  - `:data` the data segment, which may be a parsed value or a raw byte
    sequence.

  Like blocks, nodes _may_ have additional attributes which are not part of the
  serialized content."
  (:require
    [blocks.core :as block]
    [clojure.string :as str]
    (merkledag
      [data :as data]
      [link :as link]
      [node :as node :refer [node-codec]]
      [refs :as refs])
    [multicodec.core :as codec]
    [multicodec.codecs.mux :refer [mux-codec]])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


;; ## Graph Repository

(defrecord GraphRepo
  [codec store refs])


(defn graph-repo
  [& {:keys [types store refs]}]
  (GraphRepo.
    (mux-codec :node/v1 (node-codec (or types (data/load-types!))))
    store
    refs))



;; ## Value Constructors

(defn link*
  "Link constructor which uses the `link/Target` protocol to build a link to
  the target value."
  [name target]
  (link/link-to target name))


(defn node*
  "Constructs a new merkle node. Any links passed as an argument will be
  placed at the beginning of the link segment, in order. Additional links
  walked from the data value will be appended in a canonical order."
  [codec ordered-links data]
  (when (or (seq ordered-links) data)
    (let [links (->> (link/find-links data)
                     (concat ordered-links)
                     (link/compact-links)
                     (remove (set ordered-links))
                     (concat ordered-links))]
      (link/validate-links! links)
      (node/format-block codec {:links links, :data data}))))


(defn update-node
  "Updates the given node by substituting in the given links and potentially
  running a function to update the body.

  If the given links have names, and links with matching names exist in the
  current node, they will be replaced with the new links. Otherwise, the links
  will be appended to the table."
  ([codec node links]
   (update-node codec node links identity))
  ([codec node links f & args]
   (let [node' (reduce link/update-link node links)]
     (node* codec (:links node') (apply f (:data node') args)))))



;; ## Graph Traversal

(defn- resolve-ident
  "Resolves a string as either a valid multihash or a ref name. Returns the
  multihash pointed to by the value, or nil if the identifier is not found."
  [tracker ident]
  (if (instance? Multihash ident)
    ident
    (:value (refs/get-ref tracker ident))))


(defn get-node
  "Retrieves and parses the block identified. The identifier may be a multihash
  or a string ref name."
  [repo ident]
  ; TODO: cache parsed values
  (some->>
    ident
    (resolve-ident (:refs repo))
    (block/get (:store repo))
    (node/parse-block (:codec repo))))


(defn get-path
  "Retrieve a node by recursively resolving a path through a sequence of nodes.
  The `path` should be a slash-separated string or a vector of path segments."
  ([repo root-id path]
   (get-path repo root-id path nil))
  ([repo root-id path not-found]
   (loop [node (get-node repo root-id)
          path (if (string? path) (str/split path #"/") (seq path))]
     (if node
       (if (seq path)
         (if-let [link (link/resolve-name (:links node) (str (first path)))]
           (if-let [child (get-node repo (:target link))]
             (recur child (rest path))
             not-found)
           not-found)
         node)
       not-found))))



;; ## Graph Manipulation

(defn create-node
  "Creates a new node value using the codecs in the given repo. The node is not
  persisted in the repo, for that use `create-node!`."
  ([repo data]
   (node* (:codec repo) nil data))
  ([repo links data]
   (node* (:codec repo) links data)))


(defn create-node!
  "Stores a new node value in the repository. Returns the persisted block."
  ([repo data]
   (create-node! repo nil data))
  ([repo links data]
   (when-let [node (create-node repo links data)]
     (block/put! (:store repo) node))))


(defn- path-updates
  "Returns a sequence of nodes, the first of which is the updated root node."
  [repo root path f args]
  (if (empty? path)
    ; Base Case: empty path segment
    [(apply f root args)]
    ; Recursive Case: first path segment
    (let [link-name (str (first path))
          child (when-let [link' (and root (link/resolve-name (:links root) link-name))]
                  (get-node repo (:target link')))]
      (when-let [children (path-updates repo child (rest path) f args)]
        (cons (if root
                (update-node (:codec repo) root [(link* link-name (first children))])
                (node* (:codec repo) [(link* link-name (first children))] nil))
              children)))))


(defn update-path!
  "Updates the node at the given path from the root ref by applying a function
  to it. Creates a new version for the ref, which must already exist. Returns
  the updated ref. Path must be a string or sequence of strings."
  [repo root-id path f & args]
  (if-let [ref-val (refs/get-ref (:refs repo) root-id)]
    (let [old-root (get-node repo (:value ref-val))
          path (-> (if (string? path) path (str/join "/" path))
                   (str/split #"/"))]
      ; Update the ref to point at the new root.
      (->> (path-updates repo old-root path f args)
           (map (partial block/put! (:store repo)))
           (doall)
           (first)
           (:id)
           (refs/set-ref! (:refs repo) (:name ref-val))))
    (throw (ex-info (str "Cannot update path rooted at " root-id " which is not a ref")
                    {:id root-id}))))



;; ## Garbage Collection

; TODO: (reachable-blocks graph root) -> #{multihashes}
; TODO: (garbage-collect! graph #{roots}) -> stats
