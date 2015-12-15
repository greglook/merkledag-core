(ns merkledag.data
  "Support for core types and data segment codecs."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as tformat])
    (merkledag.codec
      [bin :refer [bin-codec]]
      [edn :refer [edn-codec]])
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
      (satisfies? merkledag.codec.bin/BinaryData value)
        :bin
      :else
        default)))


(defn data-codec
  "Creates a new multiplexing codec to select among binary, text, and EDN
  encodings based on the value type."
  ([]
   (data-codec core-types))
  ([types]
   (assoc
     (codecs/mux-codec
       :edn  (edn-codec types)
       :bin  (bin-codec)
       :text (codecs/text-codec))
     :select-encoder (encoding-selector :edn))))
