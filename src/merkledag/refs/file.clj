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


(defn load-history!
  "Loads the history for the given tracker from the file and adds it to the
  ref agent."
  [file]
  (with-open [history (jio/reader file)]
    (reduce
      (fn [refs line]
        (let [[time value ref-name version] (str/split line #"\t")]
          ; TODO: sort lists by version?
          (update refs ref-name conj
                  {:name ref-name
                   :value (multihash/decode value)
                   :version (Long/parseLong version)
                   :time (ftime/parse time-format time)})))
      {}
      (line-seq history))))


(defn write-version!
  "Writes a ref version line to the end of a history file."
  [file ref-version]
  (with-open [history (jio/writer file :append true)]
    (.write history (str/join "\t" [(ftime/unparse time-format (:time ref-version))
                                    (multihash/base58 (:value ref-version))
                                    (:name ref-version)
                                    (:version ref-version)]))))


;; Multihash references in a file tracker are held in a map in a ref. An agent
;; guards writes to the file.
(defrecord FileTracker
  [data-file refs]

  refs/RefTracker

  (list-refs
    [this opts]
    (->> (vals @ref-agent)
         (map first)
         (filter #(or (:value %) (:include-nil opts)))))


  (get-ref
    [this ref-name]
    (refs/get-ref this ref-name nil))


  (get-ref
    [this ref-name version]
    (let [history (get @ref-agent ref-name)]
      (if version
        (some #(when (= version (:version %)) %) history)
        (first history))))


  (list-ref-history
    [this ref-name]
    (get @ref-agent ref-name))


  (set-ref!
    [this ref-name value]
    ; TODO: assert value is a multihash?
    (send-off)
    ; FIXME:
    #_
    (-> memory
        (swap!
          (fn record-ref
            [db]
            (let [versions (get db ref-name [])
                  current (first versions)]
              (if (= value (:value current))
                db
                (let [new-version {:name ref-name
                                   :value value
                                   :version (inc (:version current 0))
                                   :time (time/now)}]
                  (assoc db ref-name (list* new-version versions)))))))
        (get ref-name)
        (first)))


  (delete-ref!
    [this ref-name]
    (let [existed? (contains? @ref-agent ref-name)]
      ; FIXME: re-write file without old ref?
      existed?)))


(defn file-tracker
  "Creates a new simple file-backed ref tracker."
  [path]
  ; TODO: actually validate schema?
  ; TODO: error handling?
  ; TODO: load file data?
  (let [file (jio/file path)]
    (FileTracker. (agent (jio/file path)) (ref {}))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->FileTracker)
(ns-unmap *ns* 'map->FileTracker)
