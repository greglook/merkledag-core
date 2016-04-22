(ns merkledag.data.version
  "Support for version control properties."
  (:require
    [blocks.core :as block]
    [blocks.data]
    [byte-streams :as bytes]
    [schema.core :as s])
  (:import
    merkledag.link.MerkleLink))


(def data-attributes
  [:data.version
   {:parents
    {:schema #{MerkleLink}
     :description "Set of links to immediate ancestor versions of this document."}
    }])
