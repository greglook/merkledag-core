(ns merkledag.format
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data."
  (:require
    [blocks.core :as block]
    [bultitude.core :as bult]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    (merkledag.codecs
      [bin :refer [bin-codec]]
      [node :refer [node-codec]])
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    (multicodec.codecs
      [mux :as mux :refer [mux-codec]]
      [text :refer [text-codec]])
    [multihash.core :as multihash])
  (:import
    java.io.ByteArrayInputStream
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


;; ## Type Handlers

(def core-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}}

   'data/link-index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}}})


(defn- load-plugin-ns!
  "Attempts to load data types from the given namespace. Returns a a type map
  updated with the loaded types, if any."
  [types ns-sym]
  (try
    (when-not (find-ns ns-sym)
      (require ns-sym))
    (if-let [plugin-var (ns-resolve ns-sym 'data-types)]
      (do
        (log/info "Loading data types from" plugin-var)
        (-> types
            (merge @plugin-var)
            (vary-meta update :merkledag.data/types conj plugin-var)))
      (do
        (log/warn "No data-types var found in namespace" ns-sym)
        types))
    (catch Exception e
      (log/error e "Exception while loading data-types for namespace" ns-sym)
      types)))


(defn load-types!
  "Scans the namespaces under `merkledag.data` for vars named `data-types`.
  Returns a merged map of all loaded type definitions. Types are merged in
  lexical order, with the `core-types` from this namespace merged in last.

  The returned map will have attached metadata under the
  `:merkledag.data/types` key with a list of the loaded vars."
  []
  (merge
    (->>
      (bult/namespaces-on-classpath :prefix "merkledag.data")
      (filter #(= 3 (count (str/split (str %) #"\."))))
      (set)
      (sort)
      (reduce load-plugin-ns! {}))
    core-types))



;; ## Format Functions

(defn- decode-info!
  [codec input]
  (binding [header/*headers* []]
    (let [decoded (codec/decode! codec input)]
      (assoc
        (if (map? decoded)
          decoded
          {:data decoded})
        :encoding header/*headers*))))


(defn format-block
  "Serializes the given data value into a block using the codec. Returns a
  block containing both the formatted content, an `:encoding` key for the
  actual codec used (if any), and additional data merged in if the value was a
  map."
  [codec value]
  (when value
    (binding [header/*headers* []]
      (let [content (codec/encode codec value)
            encoded-headers header/*headers*
            block (block/read! content)
            info (decode-info! codec (ByteArrayInputStream. content))]
        (when-not (= encoded-headers (:encoding info))
          (throw (ex-info "Decoded headers do not match written encoding"
                          {:encoded encoded-headers
                           :decoded (:encoding info)})))
        (into block info)))))


(defn parse-block
  "Attempts to parse the contents of a block with the given codec. Returns an
  updated version of the block with additional keys set. At a minimum, this
  will add an `:encoding` key with the detected codec, or `nil` for raw blocks.

  The dispatched codec should return a map of attributes to merge into the
  block; typically including a `:data` field with the decoded block value. Node
  codecs should also return a `:links` vector."
  [codec block]
  (when block
    (with-open [content (block/open block)]
      (try
        (into block (decode-info! codec content))
        (catch clojure.lang.ExceptionInfo e
          (-> block
              (assoc :encoding nil)
              (vary-meta assoc ::error e)))))))


(defn parser-error
  "Returns the exception raised while trying to parse the given block, if any."
  [block]
  (::error (meta block)))



;; ## Codec Construction

(defn standard-format
  ([]
   (standard-format (load-types!)))
  ([types]
   (mux-codec
     :bin  (bin-codec)
     :text (text-codec)
     :node (node-codec types))))
