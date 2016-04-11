(ns merkledag.core
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
    [clojure.string :as str]
    (merkledag
      [data :as data]
      [link :as link]
      [node :as node :refer [node-codec]]
      [refs :as refs])
    [multicodec.core :as codec]
    [multicodec.codecs.mux :refer [mux-codec]])
  (:import
    merkledag.link.MerkleLink))


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
  ([codec data]
   (node codec nil data))
  ([codec ordered-links data]
   (let [links (->> (link/find-links data)
                    (concat ordered-links)
                    (link/compact-links)
                    (remove (set ordered-links))
                    (concat ordered-links))]
     (link/validate-links! links)
     (node/format-block codec {:links links, :data data}))))



;; ## Data Manipulation

(defn get-node
  "Retrieves and parses the block identified by the given multihash."
  [repo id]
  (when-let [block (block/get (:store repo) id)]
    (node/parse-block (:codec repo) block)))


(defn get-link
  "Convenience function which retrieves a link target as a node."
  [repo link]
  (when-let [target (:target link)]
    (get-node repo target)))


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
           (if-let [child (get-link repo link)]
             (recur child (rest path))
             (throw (ex-info (str "Linked node " (:target link) " is not available")
                             {:node (:id node)})))
           not-found)
         node)
       not-found))))


(defn put-node!
  "Stores a value in the graph as a data block. Accepts any value that is
  encodable by the block format, or a map with `:links` and `:data` entries."
  [repo value]
  (when value
    (->> value
         (node/format-block (:codec repo))
         (block/put! (:store repo)))))


(defn update-node
  "Updates the given node by substituting in the given links and potentially
  running a function to update the body.

  If the given links have names, and links with matching names exist in the
  current node, they will be replaced with the new links. Otherwise, the links
  will be appended to the table."
  ([node links]
   (update-node node links identity))
  ([node- links f & args]
   (let [links' (reduce link/update-links (:links node-) links)]
     (node links' (apply f (:data node-) args)))))


(defn update-node-link
  "Helper function for updating a single link in a node to point to a new node.
  Returns the updated node."
  [node link-name target]
  (update-node node [(link link-name target)]))


(defn update-path
  "Returns a sequence of nodes, the first of which is the updated root node."
  [repo root path f & args]
  (if (empty? path)
    ; Base Case: empty path segment
    [(apply f root args)]
    ; Recursive Case: first path segment
    (let [link-name (str (first path))
          child (when-let [link' (and root (link/resolve-name (:links root) link-name))]
                  (get-link repo link'))]
      (when-let [children (apply update-path repo child (rest path) f args)]
        (cons (if root
                (update-node-link root link-name (first children))
                (node [(link link-name (first children))] nil))
              children)))))



;; ## Garbage Collection

; TODO: (reachable-blocks graph root) -> #{multihashes}
; TODO: (garbage-collect! graph #{roots}) -> stats
