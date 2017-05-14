(ns merkledag.ref
  "Mutable references stored with a repository."
  (:require
    [clojure.future :refer [inst? nat-int?]]
    [clojure.spec :as s]
    [multihash.core :as multihash])
  (:import
    java.time.Instant
    multihash.core.Multihash))


;; ## Specs

(s/def ::name (s/and string? not-empty))
(s/def ::value (s/nilable #(instance? Multihash %)))
(s/def ::version nat-int?)
(s/def ::time inst?)

(s/def :merkledag/ref
  (s/keys :req [::name ::value ::version ::time]))



;; ## Tracker Protocol

(defprotocol RefTracker
  "Protocol for a mutable tracker for reference pointers."

  (list-refs
    [tracker opts]
    "List all references in the tracker. Returns a sequence of `RefVersion`
    values. Opts may include:

    - `:include-nil` if true, refs with history but currently nil will be
      returned.")

  (get-ref
    [tracker ref-name]
    [tracker ref-name version]
    "Retrieve the given reference. If version is not given, the latest version
    is returned. Returns a `RefVersion`.")

  (get-history
    [tracker ref-name]
    "Returns a lazy sequence of the known versions of a ref.")

  (set-ref!
    [tracker ref-name value]
    "Stores the given multihash value for a reference, creating a new
    version. Returns the created `RefVersion`.")

  (delete-ref!
    [tracker ref-name]
    "Deletes all history for a reference from the tracker."))
