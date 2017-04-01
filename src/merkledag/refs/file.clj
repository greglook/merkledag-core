(ns merkledag.refs.file
  "Block storage backed by a local file."
  (:require
    [clj-time.core :as time]
    [clj-time.format :as ftime]
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [merkledag.refs :as refs]
    [multihash.core :as multihash])
  (:import
    java.util.Date
    multihash.core.Multihash))


;; ## File IO

(def ^:private time-format
  "Joda-time formatter/parser for timestamps."
  (ftime/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZ" time/utc))


(defn- version->line
  "Converts a ref version map into a line of text."
  ^String
  [version]
  (str/join "\t" [(ftime/unparse time-format (:time version))
                  (multihash/base58 (:value version))
                  (:name version)
                  (:version version)]))


(defn- line->version
  "Converts a line of text into a ref version map."
  [line]
  (let [[time value ref-name version] (str/split line #"\t")]
    {:name ref-name
     :value (multihash/decode value)
     :version (Long/parseLong version)
     :time (ftime/parse time-format time)}))


(defn- read-history
  "Loads the history for the given tracker from the file and adds it to the
  ref agent."
  [file]
  (try
    (with-open [history (jio/reader file)]
      (reduce
        (fn [refs line]
          (let [version (line->version (str/trim-newline line))]
            ; TODO: sort lists by version?
            (update refs (:name version) conj version)))
        (sorted-map)
        (line-seq history)))
    (catch java.io.FileNotFoundException ex
      {})))


(defn- write-history!
  "Writes out the complete history from a map of refs to versions. Returns the
  file reference."
  [file refs]
  (let [versions (->> (vals refs) (apply concat) (sort-by :time))]
    (with-open [history (jio/writer file)]
      (doseq [version versions]
        (.write history (version->line version))
        (.write history "\n"))))
  file)


(defn- append-version!
  "Writes a ref version line to the end of a history file. Returns the file
  reference so it can be sent to an agent."
  [file ref-version]
  (with-open [history (jio/writer file :append true)]
    (.write history (str/join "\t" [(ftime/unparse time-format (:time ref-version))
                                    (multihash/base58 (:value ref-version))
                                    (:name ref-version)
                                    (:version ref-version)]))
    (.write history "\n"))
  file)



;; ## File Tracker

;; Multihash references in a file tracker are held in a map in a ref. An agent
;; guards writes to the file.
(defrecord FileTracker
  [data-file refs]

  refs/RefTracker

  (list-refs
    [this opts]
    (->> (vals @refs)
         (map first)
         (filter #(or (:value %) (:include-nil opts)))))


  (get-ref
    [this ref-name]
    (refs/get-ref this ref-name nil))


  (get-ref
    [this ref-name version]
    (let [history (get @refs ref-name)]
      (if version
        (some #(when (= version (:version %)) %) history)
        (first history))))


  (get-ref-history
    [this ref-name]
    (get @refs ref-name))


  (set-ref!
    [this ref-name value]
    ; TODO: assert value is a multihash?
    (dosync
      (let [versions (get @refs ref-name [])
            current (first versions)]
        (if (= value (:value current))
          current
          (let [new-version {:name ref-name
                             :value value
                             :version (inc (:version current 0))
                             :time (time/now)}]
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


(alter-meta! #'->FileTracker assoc :private true)
(alter-meta! #'map->FileTracker assoc :private true)


(defn file-tracker
  "Creates a new simple file-backed ref tracker."
  [path]
  ; TODO: agent error handling?
  (let [file (jio/file path)]
    (jio/make-parents file)
    (->FileTracker (agent file) (ref (sorted-map)))))


(defn load-history!
  "Loads the history file into the file tracker's memory."
  [tracker]
  (dosync
    (let [history (read-history @(:data-file tracker))]
      (alter (:refs tracker) (constantly history))
      history)))
