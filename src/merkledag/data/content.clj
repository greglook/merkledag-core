(ns merkledag.data.content
  "Support for content indirection and byte sequence representations."
  (:require
    [blocks.core :as block]
    [blocks.data]
    [byte-streams :as bytes]
    [schema.core :as s])
  (:import
    blocks.data.PersistentBytes))


(defprotocol ContentSource
  "Protocol for content sources which can be opened to return a stream of byte
  content."

  (open [this]
    "Returns an `InputStream` over the contained content."))


(extend-type PersistentBytes

  ContentSource

  (open
    [this]
    (.open this)))


(defrecord RawContentSource
  [store link]

  ContentSource

  (open
    [this]
    (->> (:target link)
         (block/get store)
         (block/open))))


(defrecord SeqContentSource
  [repo link]

  ContentSource

  (open
    [this]
    ; TODO: load node, assert it matches ContentSequence
    ; TODO: make lazy sequence of parts
    nil))


(s/defschema DataSource
  ; TODO: should be the 'openable' protocol
  s/Any)


(s/defschema ContentAttributes
  {:content/data DataSource
   (s/optional-key :content/length) (s/constrained s/Int pos?)
   (s/optional-key :content/type) s/Str})


(s/defschema ContentPart
  {:size (s/constrained s/Int pos?)
   (s/optional-key :content) DataSource
   (s/optional-key :offset) (s/constrained s/Int pos?)})


(s/defschema ContentSequence
  {:data/type :content/sequence
   :content/parts [ContentPart]})


(def data-types
  {'bytes/bin
   {:description "Embedded base-64 encoded string."
    :reader 'b64->pbytes
    :writers {PersistentBytes 'b64->encode}}

   'bytes/raw
   {:description "Merkle link to a block to use directly as the byte content."
    :reader 'link->raw
    :writers {RawContentSource :link}}

   'bytes/seq
   {:description "Merkle link to a vector of byte parts."
    :reader 'link->seq
    :writers {SeqContentSource :link}}})


(def data-attributes
  nil)
