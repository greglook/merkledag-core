(ns merkledag.data.types
  "Support for core types like time instant literals and UUIDs.

  Java `Date` and Joda `DateTime` values are rendered to `inst` literals, which
  are read back into Joda `DateTime` objects."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as format :refer [formatters]])
    [multihash.core :as multihash])
  (:import
    (java.util Date UUID)
    multihash.core.Multihash
    org.joda.time.DateTime))


(defn render-inst
  "Render a `DateTime` value as an inst string."
  [^DateTime dt]
  (format/unparse (formatters :date-time) dt))


(defn parse-inst
  "Parse a string into a `DateTime`."
  ^DateTime
  [literal]
  (format/parse (formatters :date-time) literal))


(def type-plugins
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
    :reader nil  ; TODO: implement
    :writers {}}})
