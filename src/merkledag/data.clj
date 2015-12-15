(ns merkledag.data
  "Support for core types like time instant literals and UUIDs.

  Java `Date` and Joda `DateTime` values are rendered to `inst` literals, which
  are read back into Joda `DateTime` objects."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as tformat])
    [merkledag.link :as link]
    [multihash.core :as multihash])
  (:import
    (java.util Date UUID)
    merkledag.link.MerkleLink
    multihash.core.Multihash
    org.joda.time.DateTime))


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
