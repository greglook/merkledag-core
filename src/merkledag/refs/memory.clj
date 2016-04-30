(ns merkledag.refs.memory
  "Ref storage backed by a map in an atom."
  (:require
    [clj-time.core :as time]
    [merkledag.refs :as refs]
    [schema.core :as s])
  (:import
    java.util.Date
    multihash.core.Multihash))


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
    (refs/get-ref this ref-name nil))


  (get-ref
    [this ref-name version]
    (if version
      (some #(when (= version (:version %)) %)
            (get @memory ref-name))
      (first (get @memory ref-name))))


  (get-ref-history
    [this ref-name]
    (get @memory ref-name))


  (set-ref!
    [this ref-name value]
    {:pre [(instance? Multihash value)]}
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
  (MemoryTracker. (atom (sorted-map) :validator (partial s/validate refs/RefsMap))))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MemoryTracker)
(ns-unmap *ns* 'map->MemoryTracker)
