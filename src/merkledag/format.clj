(ns merkledag.format
  "Serialization protocol for encoding the links and data of a node into a block
  and later decoding back into data."
  (:require
    [blocks.core :as block]
    [multicodec.core :as codec]
    [multicodec.codecs.mux :as mux])
  (:import
    java.io.PushbackInputStream))


(defn- parse-input
  "Helper for parsing the input stream from a block. Returns a map with at least
  an `:encoding` key. If the codec returns a non-map value, it is added directly
  to the result in the `:data` key, otherwise the `:encoding` is added to the
  map returned by the codec."
  [codec ^PushbackInputStream input]
  (let [first-byte (.read content)]
    (if (= \/ (char first-byte))
      ; Possible multicodec header.
      (do (.unread first-byte content)
          (binding [mux/*dispatched-codec* nil]
            (let [data (codec/decode! codec content)]
              (assoc (if (map? data) data {:data data})
                     :encoding (or mux/*dispatched-codec*
                                   (:header codec))))))
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
    ; - If value is binary, serialize as binary and return block with :data set
    ;   to a PersistentBytes value.
    ; - If value is a string, serialize as UTF-8 and return block with :data set
    ;   to the string value.
    ; - If value is a map, assume it's meant to be a node, warn if :links or
    ;   :data not present. Serialize as merkledag via EDN or CBOR, return block
    ;   with :links and :data keys merged.
    ))


(comment
  (mux-codec
    :bin  (filter-codec
            (bin-codec)
            :decoder #(array-map :data %))
    :text (filter-codec
            (text-codec)
            :decoder #(array-map :data %))
    :node (node-codec :edn (edn-codec types))))
