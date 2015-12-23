(ns merkledag.data.time
  "Support for time types such as instants, dates, and intervals."
  (:require
    (clj-time
      [coerce :as coerce]
      [core :as time]
      [format :as tformat]))
  (:import
    java.util.Date
    (org.joda.time
      DateTime
      Interval
      LocalDate)))


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


(def types
  {'inst
   {:description "Instants in time"
    :reader parse-inst
    :writers {Date (comp render-inst coerce/from-date)
              DateTime render-inst}}

   'time/date
   {:description "Local calendar date"
    :reader '???
    :writers {LocalDate str}}

   'time/interval
   {:description "Interval between two instants in time"
    :reader '???
    :writers {Interval #(vector (time/start %) (time/end %))}}})
