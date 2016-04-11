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
      [format :as format]
      [link :as link]
      [refs :as refs])
    [multicodec.core :as codec])
  (:import
    merkledag.link.MerkleLink))


;; It should be simple to:
;; - Create a "buffer" on top of an existing graph.
;; - Take some root pins into the graph.
;; - 'Mutate' the pins by updating into the nodes by resolving paths.
;; - Clean up the buffer by garbage collecting from the mutated pins.
;; - 'Flush' the buffer by writing all the blocks to the backing store.



;; ## Global Node Formatter

(def block-codec
  "The standard format used to read and write data blocks."
  (delay (format/standard-format)))


(defn set-codec!
  "Sets the global node format to the given formatter."
  [codec]
  (when-not (satisfies? codec/Encoder codec)
    (throw (IllegalArgumentException.
             (str "Cannot set MerkleDAG block codec to type which does not "
                  "satisfy the Encoder protocol: " (class codec)))))
  (when-not (satisfies? codec/Decoder codec)
    (throw (IllegalArgumentException.
             (str "Cannot set MerkleDAG block codec to type which does not "
                  "satisfy the Decoder protocol: " (class codec)))))
  (alter-var-root #'block-codec (constantly (delay codec))))



;; ## Graph Repository

(defrecord GraphRepo
  [codec store refs])


(defn graph-repo
  [& {:keys [codec store refs]}]
  (GraphRepo. codec store refs))


(defn get-node
  "Retrieves and parses the block identified by the given multihash."
  [repo id]
  (when-let [block (block/get (:store repo) id)]
    (format/parse-block (:codec repo) block)))


(defn get-link
  "Convenience function which retrieves a link target as a node."
  [repo link]
  (when-let [target (:target link)]
    (get-node repo target)))


(defn get-path
  "Retrieve a node by recursively resolving a path through a sequence of nodes.
  The `path` should be a slash-separated string or a vector of path segments."
  ([repo root path]
   (get-path repo root path nil))
  ([repo root path not-found]
   (loop [node (get-node repo root)
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
         (format/format-block (or (:codec repo) @block-codec))
         (block/put! (:store repo)))))



;; ## Value Constructors

(defn link
  "Non-magical link constructor which uses the `link/Target` protocol to build
  a link to the target value."
  [name target]
  (link/link-to target name))


(defn node*
  "Non-magical node constructor which constructs a block from the given links
  and data. No attempt is made to ensure the link table is comprehensive."
  ([data]
   (node* nil data))
  ([links data]
   (link/validate-links! links)
   (format/format-block @block-codec {:links links, :data data})))


(defn node
  "Constructs a new merkle node. Any links passed as an argument will be
  placed at the beginning of the link segment, in order. Additional links
  walked from the data value will be appended in a canonical order."
  ([data]
   (node nil data))
  ([ordered-links data]
   (let [links (->> (link/find-links data)
                    (concat ordered-links)
                    (link/compact-links)
                    (remove (set ordered-links))
                    (concat ordered-links))]
     (node* links data))))



;; ## Data Manipulation

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
  [store root path f & args]
  (if (empty? path)
    ; Base Case: empty path segment
    [(apply f root args)]
    ; Recursive Case: first path segment
    (let [link-name (str (first path))
          child (when-let [link' (and root (link/resolve-name (:links root) link-name))]
                  (get-link store link'))]
      (when-let [children (apply update-path store child (rest path) f args)]
        (cons (if root
                (update-node-link root link-name (first children))
                (node [(link link-name (first children))] nil))
              children)))))



;; ## Garbage Collection

; TODO: (reachable-blocks graph root) -> #{multihashes}
; TODO: (garbage-collect! graph #{roots}) -> stats
