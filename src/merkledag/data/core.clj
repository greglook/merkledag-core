(ns merkledag.data.core
  "Defines the core protocols for data marshalling."
  (:require
    [arrangement.core :as order]))


; TODO: should define a protocol for reading and writing values into mdag nodes.
; Constructed handler should have a preferred serialization format, and be
; extensible with plugins to convert values into tagged-literals when needed.
; Same plugins should know how to _read_ tagged-literals into values.



;; ## Write Handlers

; To write recursively, a write handler needs to be passed three arguments:
; the value to be written, the output to write it to, and a reference to the
; main rendering function.



(defn print-handler
  "Basic write handler which renders the value using its print representation."
  [encoder value]
  (pr-str value))


(defn primitive-writers
  "Write handler lookup for primitive values."
  [value]
  (when (or (nil? value)
            (true? value)
            (false? value)
            (number? value)
            (string? value)
            (symbol? value)
            (keyword? value))
    print-handler))


(defn- surround-coll
  "Renders a collection of values separated by spaces and surrounded by the
  given opening and closing delimiters."
  [opening closing values]
  (str opening (str/join " " values) closing))


(defn collection-handler
  "Write handler for collections."
  [encoder value]
  (cond
    (seq? value)
      (surround-coll "(" ")" (map encoder value))
    (vector? value)
      (surround-coll "[" "]" (map encoder value))
    (map? value)
      (->> (sort-by first order/rank value)
           (map #(str (encoder (key %)) " "
                      (encoder (val %))))
           (surround-coll "{" "}"))
    (set? value)
      (->> (sort order/rank value)
           (map encoder)
           (surround-coll "#{" "}"))
    :else
      (throw (RuntimeException. (str "Unknown collection type: " value)))))


; TODO: implement lookup logic
; - fallback lookup (across variable number of lookup functions)
; - inheritance lookup (by class)
; - caching lookup (needs to be by class...)
