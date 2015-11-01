(ns merkledag.link
  (:refer-clojure :exclude [resolve])
  (:require
    blobble.core
    multihash.core)
  (:import
    blobble.core.Blob
    multihash.core.Multihash))


(def ^:dynamic *link-table*
  "Contextual link table used to collect links when defining nodes, and to
  assign links when parsing nodes."
  nil)


(defn resolve
  "Resolves a link against the current `*link-table*`, if any."
  [name]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: " (pr-str name)))))
  (some #(when (= name (:name %)) %)
        *link-table*))


(defn target
  [x]
  (cond
    (nil? x)
      nil
    (instance? Multihash x)
      x
    (instance? Blob x)
      (:id x)
    :else
      (throw (IllegalArgumentException.
               (str "Cannot resolve type " (class x)
                    " as a merkle link target.")))))
