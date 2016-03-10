(ns merkledag.refs.memory
  "Block storage backed by a map in an atom. Blocks put into this store will be
  passed to `load!` to ensure the content resides in memory.

  This store is most suitable for testing, caches, and other situations which
  call for a non-persistent block store."
  (:require
    [clj-time.core :as time]
    [merkledag.refs :as refs])
  (:import
    java.util.Date
    multihash.core.Multihash))


(comment
  (defschema RefHistory
    (s/constrained
      [RefVersion]
      #(every? (fn [[a b]] (pos? (compare (:version a) (:version b))))
               (partition 2 1 %))))

  (defschema RefDB
    {String RefHistory}))


;; Multihash references in a memory tracker are held in a map in an atom.
;; `memory` is a map from ref name to a sequence of historical values for the
;; ref.
(defrecord MemoryTracker
  [memory]

  refs/RefTracker

  (list-refs
    [this opts]
    (->> (vals @memory)
         (map first)
         (filter #(or (:value %) (:include-nil opts)))))


  (get-ref
    [this ref-name]
    (first (get @memory ref-name)))


  (get-ref
    [this ref-name version]
    (some #(when (= version (:version %)) %)
          (get @memory ref-name)))


  (list-ref-history
    [this ref-name]
    (get @memory ref-name))


  (set-ref!
    [this ref-name value]
    ; TODO: assert value is a multihash?
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
    (let [existed? (contains? @memory ref-name)]
      (swap! memory dissoc ref-name)
      existed?)))


(defn memory-tracker
  "Creates a new in-memory ref tracker."
  []
  ; TODO: actually validate schema?
  (MemoryTracker. (atom (sorted-map) :validator map?)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MemoryTracker)
(ns-unmap *ns* 'map->MemoryTracker)
