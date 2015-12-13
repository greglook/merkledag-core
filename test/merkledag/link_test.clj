(ns merkledag.link-test
  (:require
    [clojure.test :refer :all]
    [merkledag.link :as link]
    [multihash.core :as multihash])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(deftest foo
  (is true))


; TODO: test MerkleLink methods
; value equality [equals hashCode]
; comparable to other links
; metadata
; keyword lookup [:name :target :tsize]
; dereferencing [deref realized?]


; Link construction

; Link table functions

; Link targeting
