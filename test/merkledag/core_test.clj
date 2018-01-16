(ns merkledag.core-test
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-block-store]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [clojure.test.check.generators :as gen]
    [clojure.walk :as walk]
    [merkledag.cache :as cache]
    [merkledag.core :as mdag]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [merkledag.store :as store]
    [multistream.codec :as codec]
    [test.carly.core :as carly :refer [defop]]))


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
  [store input]
  (->>
    input
    (reduce
      (fn [nodes [backlinks data]]
        (if (empty? nodes)
          (conj nodes (store/format-block store {::node/data data}))
          (let [links (mapv (fn idx->link
                              [[n i]]
                              (let [node (nth nodes (mod i (count nodes)))]
                                (link/create n (::node/id node) (node/reachable-size node))))
                            backlinks)]
            (conj nodes (store/format-block store {::node/links links, ::node/data data})))))
      [])
    (map (juxt ::node/id identity))
    (into {})))


(defn gen-context
  "Generate a context map of precreated nodes by serializing generated data
  with the given codec."
  [store]
  (let [gen-link-name (gen/such-that #(not (str/index-of % "/")) gen/string)
        gen-backlinks (gen/vector (gen/tuple gen-link-name gen/nat))
        gen-node-input (gen/tuple gen-backlinks gen-node-data)]
    (gen/fmap
      (partial build-node-context store)
      (gen/not-empty (gen/vector gen-node-input)))))



;; ## Operation Definitions

(defop GetNode
  [id]

  (gen-args
    [nodes]
    [(gen/elements (keys nodes))])

  (apply-op
    [this store]
    (mdag/get-node store id))

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
    (mdag/get-links store id))

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
    (mdag/get-data store id))

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
    (mdag/store-node! store node))

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
    (mdag/delete-node! store id))

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

(deftest ^:integration edn-node-store
  (let [encoding [:mdag :edn]
        node-format {:encoding encoding
                     :codecs (store/node-codecs nil)}]
    (carly/check-system "block-node-store linear EDN test" 25
      #(mdag/init-store :encoding encoding)
      op-generators
      :context-gen (gen-context node-format)
      :concurrency 1
      :repetitions 1)))


(deftest ^:integration cbor-node-store
  (let [encoding [:mdag :cbor]
        node-format {:encoding encoding
                     :codecs (store/node-codecs nil)}]
    (carly/check-system "block-node-store linear CBOR test" 25
      #(mdag/init-store :encoding encoding)
      op-generators
      :context-gen (gen-context node-format)
      :concurrency 1
      :repetitions 1)))


(deftest ^:integration caching-compressed-node-store
  (let [encoding [:mdag :gzip :edn]
        node-format {:encoding encoding
                     :codecs (store/node-codecs nil)}]
    (carly/check-system "block-node-store linear CBOR test with caching" 20
      #(mdag/init-store
         :encoding encoding
         :cache {:total-size-limit (* 32 1024)})
      op-generators
      :context-gen (gen-context node-format)
      :concurrency 1
      :repetitions 1)))
