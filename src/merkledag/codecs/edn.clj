(ns merkledag.codecs.edn
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


(defn- resolve-types
  "Returns the type map from the given argument. Accepts either direct maps or
  vars holding a map."
  [types]
  (if (var? types) @types types))


(defn ^:no-doc types->print-handlers
  "Converts a map of type definitions to a dispatching function to look up
  print-handlers."
  [types]
  (->> (resolve-types types)
       (mapcat (fn [[tag {:keys [writers]}]]
                 (map (fn [[cls writer]]
                        [cls (puget/tagged-handler tag writer)])
                      writers)))
       (into {})))


(defn ^:no-doc types->data-readers
  "Converts a map of type definitions to a map of tag symbols to reader
  functions."
  [types]
  (->> (resolve-types types)
       (map #(vector (key %) (:reader (val %))))
       (into {})))


(defrecord EDNCodec
  [header types eof]

  multicodec/Encoder

  (encodable?
    [this value]
    ; In reality, some values will fail without proper type handlers.
    true)


  (encode!
    [this output value]
    (let [printer (puget/canonical-printer (types->print-handlers types))
          data (OutputStreamWriter. ^OutputStream output data-charset)
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
      {:readers (types->data-readers types)
       :eof eof}
      (-> ^InputStream input
          (InputStreamReader. data-charset)
          (PushbackReader.)))))


;; Remove automatic constructor functions.
(alter-meta! #'->EDNCodec assoc :private true)
(alter-meta! #'map->EDNCodec assoc :private true)


(defn edn-codec
  "Constructs a new EDN codec. Opts may include:

  - `:eof` a value to be returned from the codec when the end of the stream is
    reached instead of throwing an exception. "
  [types & {:as opts}]
  (map->EDNCodec (merge opts {:header "/edn", :types types})))
