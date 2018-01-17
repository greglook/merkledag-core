(ns merkledag.codec.edn
  "Functions to handle structured data formatted as EDN.

  Special types are handled by plugins which define three important attributes
  to support serializing a type to and from EDN. A plugin map should contain:

  - `:reader` a function which converts a literal form into a value of this type.
  - `:writers` a map of classes or other interfaces to functions which return
    the literal form for a value of that type.
  - `:description` human-readable text about the type.

  The collection of data type plugins maps the symbol _tag_ for each type to
  the plugin for that type."
  (:require
    [clojure.edn :as edn]
    [multistream.codec :as codec :refer [defcodec defdecoder defencoder]]
    [puget.printer :as puget])
  (:import
    (java.io
      InputStream
      InputStreamReader
      OutputStream
      OutputStreamWriter
      PushbackReader)
    java.nio.charset.Charset
    java.util.UUID))


(def ^Charset data-charset
  "Character set which data node text is serialized as."
  (Charset/forName "UTF-8"))


(def core-types
  "Core type extensions for the EDN codec."
  {'uuid
   {:reader #(UUID/fromString %)
    :writers {UUID str}}})


(defn- types->print-handlers
  "Converts a map of type definitions to a dispatching function to look up
  print-handlers."
  [types]
  (into {}
        (comp
          (map (juxt key (comp (some-fn :edn/writers :writers) val)))
          (map (fn [[tag writers]]
                 (map (fn [[cls writer]]
                        [cls (puget/tagged-handler tag writer)])
                      writers)))
          cat)
        types))


(defn- types->data-readers
  "Converts a map of type definitions to a map of tag symbols to reader
  functions."
  [types]
  (into {}
        (map (juxt key (comp (some-fn :edn/reader :reader) val)))
        types))


(defencoder EDNEncoderStream
  [^OutputStreamWriter writer
   printer]

  (write!
    [this value]
    (let [encoded (puget/render-str printer value)]
      (.write writer encoded)
      (.write writer "\n")
      (.flush writer)
      (inc (count (.getBytes encoded data-charset))))))


(defdecoder EDNDecoderStream
  [^PushbackReader reader
   data-readers]

  (read!
    [this]
    (let [value (edn/read {:readers data-readers, :eof this} reader)]
      (if (identical? value this)
        (if (thread-bound? #'codec/*eof-guard*)
          codec/*eof-guard*
          (throw (ex-info "End of input stream reached"
                          {:type ::codec/eof})))
        value))))


(defcodec EDNCodec
  [header data-types]

  (encode-byte-stream
    [this selector output-stream]
    (->EDNEncoderStream
      (OutputStreamWriter. ^OutputStream output-stream data-charset)
      (puget/canonical-printer (types->print-handlers data-types))))


  (decode-byte-stream
    [this header input-stream]
    (->EDNDecoderStream
      (PushbackReader.
        (InputStreamReader. ^InputStream input-stream data-charset))
      (types->data-readers data-types))))


(defn edn-codec
  "Constructs a new EDN codec using the given map of type handlers."
  [data-types & {:as opts}]
  (map->EDNCodec
    (assoc opts
           :header (:edn codec/headers)
           :data-types (merge core-types data-types))))
