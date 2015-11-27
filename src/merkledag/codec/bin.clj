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
      InputStream
      OutputStream)
    java.nio.ByteBuffer))


;; ## Encoding Multimethod

(defn- byte-writer-dispatch
  "Dispatches `write-bytes!` arguments on the class of the value to be written."
  [value output]
  (class value))


(defmulti write-bytes!
  "Writes the value as a sequence of bytes to the given output stream. Returns
  the number of bytes written."
  #'byte-writer-dispatch)


(defmethod write-bytes! nil
  [_ output]
  0)


(defmethod write-bytes! (Class/forName "[B")
  [^bytes data ^OutputStream output]
  (.write output data)
  (count data))


(defmethod write-bytes! ByteBuffer
  [^ByteBuffer buffer output]
  (let [length (.remaining buffer)]
    (bytes/transfer buffer output)
    length))


(defmethod write-bytes! PersistentBytes
  [^PersistentBytes content output]
  (io/copy (.open content) output)
  (count content))



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
