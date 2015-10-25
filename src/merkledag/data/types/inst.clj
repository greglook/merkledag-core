(ns merkledag.data.types.inst
  "Support for time instant literals.

  Java `Date` and Joda `DateTime` values are rendered to `inst` literals, which
  are read back into Joda `DateTime`s."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as format :refer [formatters]])))


(defn render
  "Render a `DateTime` value as an inst string."
  [^org.joda.DateTime dt]
  (format/unparse (formatters :date-time) dt))


(defn parse
  "Parse a string into a `DateTime`."
  ^org.joda.DateTime
  [literal]
  (format/parse (formatters :date-time) literal))


(def plugin
  {:tag 'inst
   :description "Instants in time"
   :reader parse
   :writers {java.util.Date (comp render coerce/from-date)
             org.joda.time.DateTime render}})
