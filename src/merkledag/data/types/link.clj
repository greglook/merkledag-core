(ns merkledag.data.types.link
  "Support for data/link multihash literals."
  (:require
    [multihash.core :as multihash])
  (:import
    multihash.core.Multihash))


(def plugin-types
  {'data/hash
   {:description "Content-addressed multihash references"
    :reader multihash/decode
    :writers {Multihash multihash/base58}}

   'data/link
   {:description "Merkle links within an object"
    :reader nil  ; TODO: implement
    :writers {}}})
