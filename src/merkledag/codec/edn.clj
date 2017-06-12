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
    [multicodec.core :as multicodec]
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


(defrecord EDNCodec
  [header printer data-readers eof]

  multicodec/Encoder

  (encodable?
    [this value]
    ; In reality, some values will fail without proper type handlers.
    true)


  (encode!
    [this output value]
    (let [data (OutputStreamWriter. ^OutputStream output data-charset)
          encoded (puget/render-str printer value)]
      (.write data encoded)
      (.write data "\n")
      (.flush data)
      (inc (count (.getBytes encoded data-charset)))))


  multicodec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (edn/read
      {:readers data-readers
       :eof eof}
      (-> ^InputStream input
          (InputStreamReader. data-charset)
          (PushbackReader.)))))


(alter-meta! #'->EDNCodec assoc :private true)
(alter-meta! #'map->EDNCodec assoc :private true)


(defn edn-codec
  "Constructs a new EDN codec. Opts may include:

  - `:eof` a value to be returned from the codec when the end of the stream is
    reached instead of throwing an exception. "
  [types & {:as opts}]
  (let [types* (merge core-types types)]
    (map->EDNCodec
      (assoc opts
             :header (:edn multicodec/headers)
             :printer (puget/canonical-printer (types->print-handlers types*))
             :data-readers (types->data-readers types*)))))
