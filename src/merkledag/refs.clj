(ns merkledag.refs
  "Mutable references stored with a repository.")


#_
(defschema RefVersion
  {:name String
   :value Multihash
   :version Long
   :time DateTime})


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

  (list-ref-history
    [tracker ref-name]
    "Returns the known versions of a ref. Returns a sequence of `RefVersion`
    values.")

  (set-ref!
    [tracker ref-name value]
    "Stores the given multihash value for a reference, creating a new
    version. Returns the created `RefVersion`.")

  (delete-ref!
    [tracker ref-name]
    "Deletes all history for a reference from the tracker."))
