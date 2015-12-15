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


;; ## Standard Types

(def inst-format
  "Joda-time formatter/parser for timestamps."
  (tformat/formatter
    time/utc
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    "yyyy-MM-dd'T'HH:mm:ssZ"
    "yyyy-MM-dd'T'HH:mm:ss"
    "yyyy-MM-dd"))


(defn render-inst
  "Render a `DateTime` value as an inst string."
  [^DateTime dt]
  (tformat/unparse inst-format dt))


(defn parse-inst
  "Parse a string into a `DateTime`."
  ^DateTime
  [literal]
  (tformat/parse inst-format literal))


(def edn-types
  {'inst
   {:description "Instants in time"
    :reader parse-inst
    :writers {Date (comp render-inst coerce/from-date)
              DateTime render-inst}}

   'uuid
   {:description "Universally-unique identifiers"
    :reader #(UUID/fromString %)
    :writers {UUID str}}

   'data/hash
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
   (data-codec edn-types))
  ([types]
   (assoc
     (codecs/mux-codec
       :edn  (edn-codec types)
       :bin  (bin-codec)
       :text (codecs/text-codec))
     :select-encoder (encoding-selector :edn))))
