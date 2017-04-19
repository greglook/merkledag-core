(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [blocks.core :as block]
    [clojure.future :refer :all]
    [clojure.spec :as s]
    [merkledag.codecs.cbor :refer [cbor-codec]]
    [merkledag.codecs.edn :refer [edn-codec]]
    [merkledag.codecs.node :refer [node-codec]]
    [merkledag.link :as link]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.mux :refer [mux-codec]]
    [multihash.core :as multihash])
  (:import
    blocks.data.Block
    java.io.ByteArrayInputStream
    merkledag.link.LinkIndex
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(s/def ::id #(instance? Multihash %))
(s/def ::size pos-int?)
(s/def ::encoding (s/nilable (s/coll-of string? :kind vector?)))
(s/def ::links (s/coll-of link/merkle-link? :kind vector?))
(s/def ::data coll?)

(s/def :merkledag/node
  (s/keys :req-un [::id ::size ::encoding]
          :opt-un [::links ::data]))


(def default-types
  "The core type definitions for hashes and links which are used in the base
  merkledag data structure."
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :cbor/tag 68
    :cbor/writers {Multihash multihash/encode}
    :edn/writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle link values"
    :reader link/form->link
    :writers {MerkleLink link/link->form}
    :cbor/tag 69}

   'data/link-index
   {:description "Indexes to the link table within a node"
    :reader link/link-index
    :writers {LinkIndex :index}
    :cbor/tag 72}})
