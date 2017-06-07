(ns merkledag.node-test
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-block-store]]
    [clojure.spec :as s]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [clojure.test.check.generators :as gen]
    [clojure.walk :as walk]
    [merkledag.codec.node-v1 :as cv1]
    [merkledag.store.block :as msb]
    [merkledag.store.cache :as cache]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.core :as codec]
    [test.carly.core :as carly :refer [defop]]))


(prefer-method clojure.pprint/simple-dispatch
               clojure.lang.IPersistentMap
               clojure.lang.IDeref)

(defmethod print-method multihash.core.Multihash
  [v w]
  (print-method (tagged-literal 'data/hash (multihash.core/base58 v)) w))

(defmethod print-method blocks.data.Block
  [v w]
  (print-method (tagged-literal 'data/block (dissoc (into {} v) :id #_:size)) w))

(defmethod print-method merkledag.link.MerkleLink
  [v w]
  (print-method (tagged-literal 'data/link link/link->form) w))




;; ## Context Generation

(def gen-node-data
  "Generator for test node data."
  (gen/fmap
    (fn [v]
      (walk/postwalk
        #(cond
           (and (float? %)
                (or (Double/isNaN %)
                    (Double/isInfinite %)))
             0.0

           (char? %)
             (str %)

           :else %)
        v))
    (gen/one-of
      [(gen/map
         gen/keyword-ns
         gen/any-printable)
       (gen/vector gen/any-printable)
       (gen/set gen/any-printable)])))


(defn- build-node-context
  [codec input]
  (->>
    input
    (reduce
      (fn [nodes [backlinks data]]
        (if (empty? nodes)
          (conj nodes (msb/format-block codec {::node/data data}))
          (let [links (mapv (fn idx->link
                              [[n i]]
                              (let [node (nth nodes (mod i (count nodes)))]
                                (link/create n (::node/id node) (node/reachable-size node))))
                            backlinks)]
            (conj nodes (msb/format-block codec {::node/links links, ::node/data data})))))
      [])
    (map (juxt ::node/id identity))
    (into {})))


(defn gen-context
  "Generate a context map of precreated nodes by serializing generated data
  with the given codec."
  [codec]
  (let [gen-link-name (gen/such-that #(not (str/index-of % "/")) gen/string)
        gen-backlinks (gen/vector (gen/tuple gen-link-name gen/nat))
        gen-node-input (gen/tuple gen-backlinks gen-node-data)]
    (gen/fmap
      (partial build-node-context codec)
      (gen/not-empty (gen/vector gen-node-input)))))



;; ## Operation Definitions

(defop GetNode
  [id]

  (gen-args
    [nodes]
    [(gen/elements (keys nodes))])

  (apply-op
    [this store]
    (node/get-node store id))

  (check
    [this model result]
    (if-let [node (get model id)]
      (and
        (is (= (select-keys node node/node-keys)
               (select-keys result node/node-keys)))
        (is (nil? (s/explain-data :merkledag/node node))))
      (is (nil? result)))))


(defop GetLinks
  [id]

  (gen-args
    [nodes]
    [(gen/elements (keys nodes))])

  (apply-op
    [this store]
    (node/get-links store id))

  (check
    [this model result]
    (and
      (is (= (get-in model [id ::node/links]) result))
      (is (nil? (s/explain-data (s/nilable ::node/links) result))))))


(defop GetData
  [id]

  (gen-args
    [nodes]
    [(gen/elements (keys nodes))])

  (apply-op
    [this store]
    (node/get-data store id))

  (check
    [this model result]
    (is (= (get-in model [id ::node/data]) result))))


(defop StoreNode
  [node]

  (gen-args
    [nodes]
    [(gen/elements (vals nodes))])

  (apply-op
    [this store]
    (node/store-node! store node))

  (check
    [this model result]
    (and
      (is (= (select-keys node node/node-keys)
             (select-keys result node/node-keys)))
      (is (nil? (s/explain-data :merkledag/node result)))))

  (update-model
    [this model]
    (assoc model (::node/id node) node)))


(defop DeleteNode
  [id]

  (gen-args
    [nodes]
    [(gen/elements (keys nodes))])

  (apply-op
    [this store]
    (node/delete-node! store id))

  (check
    [this model result]
    (is (= (contains? model id) result)))

  (update-model
    [this model]
    (dissoc model id)))


(def op-generators
  (juxt gen->GetNode
        gen->GetLinks
        gen->GetData
        gen->StoreNode
        gen->DeleteNode))



;; ## Test Harnesses

(deftest edn-block-store-test
  (let [codec (cv1/edn-node-codec)]
    (carly/check-system
      "block-node-store linear EDN test"
      #(msb/block-node-store
         :store (memory-block-store)
         :codec codec)
      op-generators
      :context (gen-context codec)
      :iterations 15)))


(deftest cbor-block-store-test
  (let [codec (cv1/cbor-node-codec)]
    (carly/check-system
      "block-node-store linear CBOR test"
      #(msb/block-node-store
         :store (memory-block-store)
         :codec codec)
      op-generators
      :context (gen-context codec)
      :iterations 15)))


(deftest caching-block-store-test
  (let [codec (cv1/cbor-node-codec)]
    (carly/check-system
      "block-node-store linear CBOR test with caching"
      #(msb/block-node-store
         :store (memory-block-store)
         :codec codec
         :cache (atom (cache/node-cache {})))
      op-generators
      :context (gen-context codec)
      :iterations 10)))
