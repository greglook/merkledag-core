(ns merkledag.repo
  "MerkleDAG repository code."
  (:require
    [blobble.core :as blob]
    [blobble.store.memory :refer [memory-store]]
    (merkledag
      [codec :as codec]
      [data :as data]
      [graph :as graph])))


(def base-codec
  "Basic codec for creating nodes."
  {:types data/core-types})



;; ## Graph Repository

;; The graph repository wraps a content-addressable blob store and handles
;; serializing nodes and links into Protobuffer-encoded objects.
(defrecord GraphRepo
  [store codec])


;; Remove automatic constructor functions.
(ns-unmap *ns* '->GraphRepo)
(ns-unmap *ns* 'map->GraphRepo)


(defn graph-repo
  "Constructs a new merkledag graph repository. If no store is given, defaults
  to a new in-memory blob store. Any types given will override the core type
  plugins."
  ([]
   (graph-repo (memory-store)))
  ([store]
   (graph-repo store nil))
  ([store codec]
   (GraphRepo. store (merge base-codec codec))))


(defn get-node
  "Retrieve a node from the given repository's blob store, parsed by the repo's
  codec."
  [repo id]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot look up node for " (pr-str id)
                  " with no repo"))))
  (some->>
    id
    (blob/get (:store repo))
    (codec/decode (:codec repo))))


(defn put-node!
  "Store a node (or map with links and data) in the repository. Returns an
  updated blob record with the serialized node."
  [repo node]
  (when-not repo
    (throw (IllegalArgumentException.
             (str "Cannot store node for " (pr-str (:id node))
                  " with no repo"))))
  (when node
    (let [{:keys [id content links data]} node]
      (if (and id content)
        (blob/put! (:store repo) node)
        (when (or links data)
          (blob/put! (:store repo) (graph/->node repo links data)))))))
