(ns merkledag.ref.file
  "Block storage backed by a local file."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [merkledag.ref :as ref]
    [multihash.core :as multihash])
  (:import
    java.time.Instant
    multihash.core.Multihash))


;; ## File IO

(defn- version->line
  "Converts a ref version map into a line of text."
  ^String
  [version]
  (str/join "\t" [(str (::ref/time version))
                  (multihash/base58 (::ref/value version))
                  (::ref/name version)
                  (::ref/version version)]))


(defn- line->version
  "Converts a line of text into a ref version map."
  [line]
  (let [[timestamp value ref-name version] (str/split line #"\t")]
    {::ref/name ref-name
     ::ref/value (multihash/decode value)
     ::ref/version (Long/parseLong version)
     ::ref/time (Instant/parse timestamp)}))


(defn- read-history
  "Loads the history for the given tracker from the file and adds it to the
  ref agent."
  [file]
  (try
    (with-open [history (io/reader file)]
      (reduce
        (fn [refs line]
          (let [version (line->version (str/trim-newline line))]
            ; TODO: sort lists by version?
            (update refs (::ref/name version) conj version)))
        (sorted-map)
        (line-seq history)))
    (catch java.io.FileNotFoundException ex
      {})))


(defn- write-history!
  "Writes out the complete history from a map of refs to versions. Returns the
  file reference."
  [file refs]
  (let [versions (->> (vals refs) (apply concat) (sort-by ::ref/time))]
    (with-open [history (io/writer file)]
      (doseq [version versions]
        (.write history (version->line version))
        (.write history "\n"))))
  file)


(defn- append-version!
  "Writes a ref version line to the end of a history file. Returns the file
  reference so it can be sent to an agent."
  [file ref-version]
  (with-open [history (io/writer file :append true)]
    (.write history (version->line ref-version))
    (.write history "\n"))
  file)



;; ## File Tracker

;; Multihash references in a file tracker are held in a map in a ref. An agent
;; guards writes to the file.
(defrecord FileRefTracker
  [data-file refs]

  ref/RefTracker

  (list-refs
    [this opts]
    (->> (vals @refs)
         (map first)
         (filter #(or (::ref/value %) (:include-nil opts)))))


  (get-ref
    [this ref-name]
    (ref/get-ref this ref-name nil))


  (get-ref
    [this ref-name version]
    (let [history (get @refs ref-name)]
      (if version
        (some #(when (= version (::ref/version %)) %) history)
        (first history))))


  (get-history
    [this ref-name]
    (get @refs ref-name))


  (set-ref!
    [this ref-name value]
    ; TODO: assert value is a multihash?
    (dosync
      (let [versions (get @refs ref-name [])
            current (first versions)]
        (if (= value (::ref/value current))
          current
          (let [new-version {::ref/name ref-name
                             ::ref/value value
                             ::ref/version (inc (::ref/version current 0))
                             ::ref/time (Instant/now)}]
            (alter refs assoc ref-name (list* new-version versions))
            (send-off data-file append-version! new-version)
            new-version)))))


  (delete-ref!
    [this ref-name]
    (dosync
      (if (contains? @refs ref-name)
        (do (alter refs dissoc ref-name)
            (send-off data-file write-history! @refs)
            true)
        false))))


(alter-meta! #'->FileRefTracker assoc :private true)
(alter-meta! #'map->FileRefTracker assoc :private true)


(defn file-ref-tracker
  "Creates a new simple file-backed ref tracker."
  [path]
  ; TODO: agent error handling?
  (let [file (io/file path)]
    (io/make-parents file)
    (->FileRefTracker (agent file) (ref (sorted-map)))))


(defn load-history!
  "Loads the history file into the file tracker's memory."
  [tracker]
  (dosync
    (let [history (read-history @(:data-file tracker))]
      (alter (:refs tracker) (constantly history))
      history)))
