(ns merkledag.data
  "Support for core types and data segment codecs."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as tformat])
    (merkledag.codec
      [bin :as bin]
      [edn :as edn])
    [merkledag.link :as link]
    [multicodec.codecs :as codecs]
    [multihash.core :as multihash])
  (:import
    (java.util Date UUID)
    merkledag.link.MerkleLink
    multihash.core.Multihash
    org.joda.time.DateTime))


; TODO: implement type plugin system
; Should load namespaces under merkledag.data:
; - merkledag.data.time
; - merkledag.data.bytes
; - merkledag.data.units
; ...


;; ## Standard Types

(def core-types
  ; TODO: is data/hash necessary? Multihashes shouldn't show up in data segments.
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle links within an object"
    :reader link/read-link
    :writers {MerkleLink :name}}})


(def data-types
  "Registry of all supported data types in the system. This is a merged type
  map from all registered plugins."
  core-types)


(defn register-types!
  "Registers types by adding the named var to the data-types set."
  [t]
  (when-not (map? t)
    (throw (IllegalArgumentException.
             (str "Argument to register-types! must be a type map: " (pr-str t)))))
  ; TODO: check that t resolves to a valid types map
  (alter-var-root #'data-types merge t core-types))



;; ## Standard Data Codec

(defn- encoding-selector
  "Constructs a function which returns `:text` for strings, `:bin` for raw byte
  types, and the given value for everything else."
  [default]
  (fn select
    [_ value]
    (cond
      (string? value)
        :text
      (bin/binary? value)
        :bin
      :else
        default)))


(defn data-codec
  "Creates a new multiplexing codec to select among binary, text, and EDN
  encodings based on the value type."
  ([]
   (data-codec #'data-types))
  ([types]
   (assoc
     (codecs/mux-codec
       :edn  (edn/edn-codec types)
       :bin  (bin/bin-codec)
       :text (codecs/text-codec))
     :select-encoder (encoding-selector :edn))))
