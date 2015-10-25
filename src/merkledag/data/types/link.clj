(ns merkledag.data.types.link
  "Support for data/link multihash literals."
  (:require
    [multihash.core :as multihash]))


(def plugin
  {:tag 'data/link
   :description "Content-addressed multihash references"
   :reader multihash/decode
   :writers {multihash.core.Multihash multihash/base58}})
