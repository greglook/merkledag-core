(ns merkledag.data.types.uuid
  "Support for universally unique identifier literals.")


(def plugin
  {:tag 'uuid
   :description "Universally-unique identifiers"
   :reader #(java.util.UUID. %)
   :writers {java.util.UUID str}})
