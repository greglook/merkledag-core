(ns merkledag.data.types.core
  "Support for core types like time instant literals and UUIDs.

  Java `Date` and Joda `DateTime` values are rendered to `inst` literals, which
  are read back into Joda `DateTime`s."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as format :refer [formatters]]))
  (:import
    java.util.Date
    java.util.UUID
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


(def plugin-types
  {'inst
   {:description "Instants in time"
    :reader parse-inst
    :writers {Date (comp render-inst coerce/from-date)
              DateTime render-inst}}

   'uuid
   {:description "Universally-unique identifiers"
    :reader #(UUID. %)
    :writers {UUID str}}})
