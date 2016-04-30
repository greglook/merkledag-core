(ns merkledag.data.common
  "Support for common data attributes."
  (:require
    [merkledag.link :as link]
    [schema.core :as s])
  (:import
    merkledag.link.MerkleLink))


(defn attributes->schema
  "Converts a map of data attribute definitions into a map schema with those
  (namespace-qualified) keywords as optional entries."
  [ns-prefix attrs]
  {:pre [(keyword? ns-prefix) (nil? (namespace ns-prefix))]}
  (->>
    attrs
    (keep (fn [[k v]]
            (when-let [schema (:schema v)]
              [(s/optional-key (keyword (name ns-prefix) (name k))) schema])))
    (into {})))


(def data-attributes
  {:type
   {:schema s/Keyword
    :description "Keyword giving the primary data type."}

   :title
   {:schema s/Str
    :description "Short label string for the entity."}

   :description
   {:schema s/Str
    :description "Human-readable string describing the data."}

   :sources
   {:schema #{MerkleLink}
    :descriiption "Set of links to related source data."}})


(s/defschema CommonAttributes
  (attributes->schema :data data-attributes))


; /path/to/my/node/@id                 (look up root identity node)
; /path/to/my/node/@context/data/type  (look up :data/type key definition)
