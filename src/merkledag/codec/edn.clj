(ns merkledag.codec.edn
  "Functions to handle structured data formatted as EDN.

  Special types are handled by plugins which define three important
  attributes to support serializing a type to and from EDN. A plugin map should
  contain:

  - `:reader` a function which converts a literal form into a value of this type.
  - `:writers` a map of classes or other interfaces to functions which return
    the literal form for a value of that type.
  - `:description` human-readable text about the type.

  The collection of data type plugins maps the symbol _tag_ for each type to
  the plugin for that type."
  (:require
    [clojure.edn :as edn]
    [multicodec.core :as multicodec]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    (java.io
      InputStream
      InputStreamReader
      OutputStream
      OutputStreamWriter
      PushbackReader)
    java.nio.charset.Charset))


(def ^Charset data-charset
  "Character set which data node text is serialized as."
  (Charset/forName "UTF-8"))


(defn ^:no-doc types->print-handlers
  "Converts a map of type definitions to a dispatching function to look up
  print-handlers."
  [types]
  (->> types
       (mapcat (fn [[tag {:keys [writers]}]]
                 (map (fn [[cls writer]]
                        [cls (puget/tagged-handler tag writer)])
                      writers)))
       (into {})))


(defn ^:no-doc types->data-readers
  "Converts a map of type definitions to a map of tag symbols to reader
  functions."
  [types]
  (->> types
       (map #(vector (key %) (:reader (val %))))
       (into {})))


(defrecord EDNCodec
  [header types]

  multicodec/Encoder

  (encode!
    [this output value]
    (let [printer (puget/canonical-printer (types->print-handlers types))]
      (let [data (OutputStreamWriter. ^OutputStream output data-charset)
            encoded (puget/render-str printer value)]
        ; TODO: write sequential collections as multiline values?
        (.write data encoded)
        (.flush data)
        (count (.getBytes encoded data-charset)))))


  multicodec/Decoder

  (decode!
    [this input]
    (let [reader (-> ^InputStream input
                     (InputStreamReader. data-charset)
                     (PushbackReader.))]
      ; TODO: read every value in the stream
      (edn/read {:readers (types->data-readers types)} reader))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->EDNCodec)
(ns-unmap *ns* 'map->EDNCodec)


(defn edn-codec
  [types]
  (EDNCodec. "/edn/" types))
