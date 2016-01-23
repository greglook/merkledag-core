(ns merkledag.format
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data."
  (:require
    [blocks.core :as block]
    (merkledag.codecs
      [bin :refer [bin-codec]]
      [edn :refer [edn-codec]]
      [node :refer [node-codec]])
    [multicodec.core :as codec]
    (multicodec.codecs
      [filter :refer [filter-codec]]
      [mux :as mux :refer [mux-codec]]
      [text :refer [text-codec]]))
  (:import
    java.io.PushbackInputStream))


(defn- parse-input
  "Helper for parsing the input stream from a block. Returns a map with at least
  an `:encoding` key. If the codec returns a non-map value, it is added directly
  to the result in the `:data` key, otherwise the `:encoding` is added to the
  map returned by the codec."
  [codec ^PushbackInputStream input]
  (let [first-byte (.read input)]
    (if (<= 0 first-byte 127)
      ; Possible multicodec header.
      (do (.unread input first-byte)
          (codec/decode! codec input))
      ; Unknown encoding.
      {:encoding nil})))


(defn parse-block
  "Attempts to parse the contents of a block with the given codec. Returns an
  updated version of the block with additional keys set. At a minimum, this
  will add an `:encoding` key with the detected codec, or `nil` for raw blocks.

  The dispatched codec should return a map of attributes to merge into the
  block; typically including a `:data` field with the decoded block value. Node
  codecs should also return a `:links` vector."
  [codec block]
  (when block
    (into block
          (with-open [content (PushbackInputStream. (block/open block))]
            (parse-input content)))))


(defn format-block
  "Serializes the given data value into a block using the given codec. Returns
  a block containing both the formatted content, an `:encoding` key for the
  actual codec used (if any), and additional data merged in if the value was a
  map."
  [codec data]
  (when data
    (binding [mux/*dispatched-codec* nil]
      (let [content (codec/encode codec data)
            encoding (get-in codec [:codecs mux/*dispatched-codec*])
            block (assoc (block/read! content)
                         :encoding encoding)]
        (if (and (map? data) (:data data))
          (into block data)
          (assoc block :data data))))))


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
    :bin  (bin-codec)
    :text (lift-codec (text-codec))
    :node (node-codec :edn (edn-codec types))))
