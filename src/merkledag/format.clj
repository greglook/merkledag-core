(ns merkledag.format
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data."
  (:require
    [blocks.core :as block]
    [byte-streams :refer [bytes=]]
    (merkledag.codecs
      [bin :refer [bin-codec]]
      [node :refer [node-codec]])
    [multicodec.core :as codec]
    (multicodec.codecs
      [bin :refer [BinaryData]]
      [filter :refer [filter-codec]]
      [mux :as mux :refer [mux-codec]]
      [text :refer [text-codec]]))
  (:import
    java.io.PushbackInputStream))


(defn parse-block
  "Attempts to parse the contents of a block with the given codec. Returns an
  updated version of the block with additional keys set. At a minimum, this
  will add an `:encoding` key with the detected codec, or `nil` for raw blocks.

  The dispatched codec should return a map of attributes to merge into the
  block; typically including a `:data` field with the decoded block value. Node
  codecs should also return a `:links` vector."
  [codec block]
  (when block
    (->>
      (with-open [content (PushbackInputStream. (block/open block))]
        (let [first-byte (.read content)]
          (if (<= 0 first-byte 127)
            ; Possible multicodec header.
            (do (.unread content first-byte)
                (codec/decode! codec content))
            ; Unknown encoding.
            {:encoding nil})))
      (into block))))


(defn format-block
  "Serializes the given data value into a block using the given codec. Returns
  a block containing both the formatted content, an `:encoding` key for the
  actual codec used (if any), and additional data merged in if the value was a
  map."
  [codec data]
  (when data
    (let [content (codec/encode codec data)
          block (block/read! content)
          decoded (codec/decode codec content)]
      (when-not (if (map? data)
                  (and (= (:links data) (:links decoded))
                       (= (:data  data) (:data  decoded)))
                  (or (= data (:data decoded))
                      (and (satisfies? BinaryData data)
                           (bytes= data (:data decoded)))))
        (throw (ex-info (str "Decoded data does not match input data " (class data))
                        {:input data
                         :decoded decoded})))
      (into block decoded))))


(defn- lift-codec
  "Lifts a codec into a block format by wrapping the decoded value in a map with
  `:encoding` and `:data` entries."
  [codec]
  (filter-codec codec
    :decoding (fn wrap-data
                [data]
                {:encoding (:header codec)
                 :data data})))


(defn standard-format
  [types]
  (mux-codec
    :bin  (lift-codec (bin-codec))
    :text (lift-codec (text-codec))
    :node (node-codec types)))
