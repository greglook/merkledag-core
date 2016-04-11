(ns merkledag.data
  "Functions for loading and managing type representation for merkledag nodes."
  (:require
    [bultitude.core :as bult]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [merkledag.link :as link]
    [multihash.core :as multihash])
  (:import
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(def core-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :writers {Multihash multihash/base58}
    ; just an example:
    :cbor {:tag 27
           :writers {Multihash multihash/encode}}}

   'data/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}}

   'data/link-index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}}})


(defn load-plugin-ns!
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
      (remove #{(str *ns*)})
      (set)
      (sort)
      (reduce load-plugin-ns! {}))
    core-types))
