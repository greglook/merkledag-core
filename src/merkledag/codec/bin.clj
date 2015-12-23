(ns merkledag.codec.bin
  "Enhanced binary multicodec which accepts a variety of raw bytes for encoding
  and always produces `PersistentBytes` values on decode."
  (:require
    [byte-streams :as bytes]
    [clojure.java.io :as io]
    [multicodec.core :as multicodec])
  (:import
    blocks.data.PersistentBytes
    (java.io
      ByteArrayOutputStream
      OutputStream)
    java.nio.ByteBuffer))


;; ## Encoding Multimethod

(defprotocol BinaryData
  "Protocol for values which can be encoded directly as a binary sequence."

  (write-bytes!
    [data output]
    "Writes the binary data to the given output stream. Returns the number of
    bytes written."))


(defn binary?
  "Helper function which returns true for values which satisfy the `BinaryData`
  protocol and are valid encoding values."
  [value]
  (satisfies? BinaryData value))


(extend-protocol BinaryData

  (class (byte-array 0))

  (write-bytes!
    [data ^OutputStream output]
    (.write output ^bytes data)
    (count data))


  ByteBuffer

  (write-bytes!
    [buffer ^OutputStream output]
    (let [length (.remaining buffer)]
      (bytes/transfer buffer output)
      length))


  PersistentBytes

  (write-bytes!
    [content output]
    (io/copy (.open content) output)
    (count content)))



;; ## Binary Codec

(defrecord BinaryCodec
  [header]

  multicodec/Encoder

  (encode!
    [this output value]
    (write-bytes! value output))


  multicodec/Decoder

  (decode!
    [this input]
    (let [baos (ByteArrayOutputStream.)]
      (io/copy input baos)
      (PersistentBytes/wrap (.toByteArray baos)))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->BinaryCodec)
(ns-unmap *ns* 'map->BinaryCodec)


(defn bin-codec
  []
  (BinaryCodec. (multicodec/headers :bin)))
