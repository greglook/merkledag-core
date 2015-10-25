(ns merkledag.data.edn
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
    [multihash.core :as multihash]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      InputStreamReader
      OutputStreamWriter
      PushbackReader)
    java.nio.charset.Charset
    multihash.core.Multihash))


(def ^:no-doc ^Charset data-charset
  "Character set which data node text is serialized as."
  (Charset/forName "UTF-8"))


(def ^:const ^:private data-header
  "Header string which must appear as the first characters in a data node."
  "#data/edn ")



;; ## Value Writing

(defn types->print-handlers
  "Converts a map of type definitions to a dispatching function to look up
  print-handlers."
  [types]
  (->> types
       (mapcat (fn [[tag {:keys [writers]}]]
                 (map (fn [[cls writer]]
                        [cls (puget/tagged-handler tag writer)])
                      writers)))
       (into {})))


(defn print-data
  "Renders a value to a byte array by serializing it with Puget."
  ^bytes
  [types value]
  (let [printer (puget/canonical-printer (types->print-handlers types))
        content-bytes (ByteArrayOutputStream.)]
    (with-open [content (OutputStreamWriter. content-bytes data-charset)]
      (.write content data-header)
      (.write content (puget/render-str printer value))
      (.flush content))
    (.toByteArray content-bytes)))



;; ## Value Reading

(defn check-header
  "Reads the first few bytes from an object's content to determine whether it
  is a data object. Returns true if the header matches."
  [^bytes content]
  (let [header (.getBytes data-header data-charset)
        len (count header)]
    (= (seq header) (take len (seq content)))))


(defn types->data-readers
  "Converts a map of type definitions to a map of tag symbols to reader
  functions."
  [types]
  (->> types
       (map #(vector (key %) (:reader (val %))))
       (into {})))


(defn parse-data
  "Reads the contents of the given blob and attempts to parse it as an EDN data
  structure. Returns the parsed value, or nil if the content is not EDN."
  [types content]
  (when (check-header content)
    (with-open [reader (-> content
                           (ByteArrayInputStream.)
                           (InputStreamReader. data-charset)
                           (PushbackReader.))]
      (.skip reader (count data-header))
      (edn/read {:readers (types->data-readers types)} reader))))
