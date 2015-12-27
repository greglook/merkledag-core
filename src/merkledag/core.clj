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
      [format :as format]
      [link :as link :refer [*link-table*]]))
  (:import
    merkledag.link.MerkleLink))


;; It should be simple to:
;; - Create a "buffer" on top of an existing graph.
;; - Take some root pins into the graph.
;; - 'Mutate' the pins by updating into the nodes by resolving paths.
;; - Clean up the buffer by garbage collecting from the mutated pins.
;; - 'Flush' the buffer by writing all the blocks to the backing store.


;; ## Global Node Formatter

(def block-format
  "The standard format used to convert between structured node data and block
  values."
  (format/protobuf-format (data/data-codec)))


(defn set-format!
  "Sets the global node format to the given formatter."
  [formatter]
  (when-not (satisfies? format/BlockFormat format)
    (throw (IllegalArgumentException.
             (str "Cannot set MerkleDAG block format to type which does not "
                  "satisfy the BlockFormat protocol: " (class formatter)))))
  (alter-var-root #'block-format (constantly formatter)))



;; ## Value Constructors

(defn link*
  "Non-magical link constructor which uses the `link/Target` protocol to build
  a link to the target value."
  [name target]
  (link/link-to target name))


(defn link
  "Constructs a new merkle link. The name should be a string. If no target is
  given, the name is looked up in the dynamic link table. Otherwise, this
  constructs a link to the target and adds it to the link table."
  ([name]
   (link/read-link name))
  ([name target]
   (let [new-link (link* name target)]
     (if-let [extant (link/resolve name)]
       ; Check if existing link matches.
       (if (= (:target new-link) (:target extant))
         extant
         (throw (IllegalStateException.
                  (str "Can't link " name " to " (:target new-link)
                       ", already points to " (:target extant)))))
       ; No existing link, use new one.
       (do
         (when *link-table*
           (set! *link-table* (conj *link-table* new-link)))
         new-link)))))


(defn node*
  "Non-magical node constructor which constructs a block from the given links
  and data. No attempt is made to ensure the link table is comprehensive."
  ([data]
   (node* nil data))
  ([links data]
   (format/format-node block-format links data)))


(defmacro node
  "Constructs a new merkle node. Handles binding of the link table to capture
  links declared as part of the data. Any links passed as an argument will be
  placed at the beginning of the link segment."
  ([data]
   `(node nil ~data))
  ([extra-links data]
   `(let [links# (binding [*link-table* nil] ~extra-links)]
      (binding [*link-table* (vec links#)]
        (let [data# ~data]
          (format/format-node block-format *link-table* data#))))))



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
  (update-node node [(link* link-name target)]))



;; ## Graph Operations

(defn get-node
  "Retrieves and parses the block identified by the given multihash."
  [store id]
  (when-let [block (block/get store id)]
    (format/parse-node block-format block)))


(defn get-link
  "Convenience function which retrieves a link target as a node."
  [store link]
  (when-let [target (:target link)]
    (get-node store target)))


(defn get-path
  "Retrieve a node by recursively resolving a path through a sequence of nodes.
  The `path` should be a slash-separated string or a vector of path segments."
  ([store root path]
   (get-path store root path nil))
  ([store root path not-found]
   (loop [node root
          path (if (string? path) (str/split #"/" path) (seq path))]
     (if node
       (if (seq path)
         (if-let [link (link/resolve (str (first path)) (:links node))]
           (if-let [child (get-link store link)]
             (recur child (rest path))
             (throw (ex-info (str "Linked node " (:target link) " is not available")
                             {:node (:id node)})))
           not-found)
         node)
       not-found))))


(defn put-node!
  "Stores a node in the graph for later retrieval. Accepts a pre-built node
  block or a map with `:links` and `:data` entries."
  [store node]
  (when-let [{:keys [id links data]} node]
    (if id
      (block/put! store node)
      (when (or links data)
        (block/put! store (format/format-node block-format links data))))))


(defn update-path
  "Returns a sequence of nodes, the first of which is the updated root node."
  [store root path f & args]
  (if (empty? path)
    ; Base Case: empty path segment
    [(apply f root args)]
    ; Recursive Case: first path segment
    (let [link-name (str (first path))
          child (when-let [link (and root (link/resolve link-name (:links root)))]
                  (get-link store link))]
      (when-let [children (apply update-path store child (rest path) f args)]
        (cons (if root
                (update-node-link root link-name (first children))
                (node [(link* link-name (first children))] nil))
              children)))))



;; ## Garbage Collection

; TODO: (reachable-blocks graph root) -> #{multihashes}
; TODO: (garbage-collect! graph #{roots}) -> stats
