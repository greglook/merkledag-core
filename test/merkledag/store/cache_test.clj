(ns merkledag.store.cache-test
  (:require
    [clojure.core.cache :as cache]
    [clojure.test :refer :all]
    [merkledag.node :as node]
    [merkledag.store.cache :as msc]
    [multihash.digest :as digest]))


(defn- gen-node
  "Generate a simple node map for testing."
  [data]
  (let [content (pr-str [nil data])
        id (digest/sha2-256 content)]
    {::node/id id
     ::node/size (count content)
     ::node/links nil
     ::node/data data}))


(deftest cache-construction
  (testing "total size limit"
    (is (pos? (.total-size-limit (msc/node-cache {})))
        "has default value")
    (is (= 1024 (.total-size-limit (msc/node-cache {} :total-size-limit 1024)))
        "constructor option sets value")
    (is (thrown? IllegalArgumentException (msc/node-cache {} :total-size-limit "foo"))
        "must be a number")
    (is (thrown? IllegalArgumentException (msc/node-cache {} :total-size-limit 0))
        "must be positive"))
  (testing "node size limit"
    (is (nil? (.node-size-limit (msc/node-cache {})))
        "has no default value")
    (is (= 1024 (.node-size-limit (msc/node-cache {} :node-size-limit 1024)))
        "constructor option sets value")
    (is (thrown? IllegalArgumentException (msc/node-cache {} :node-size-limit "foo"))
        "must be a number")
    (is (thrown? IllegalArgumentException (msc/node-cache {} :node-size-limit 0))
        "must be positive"))
  (testing "base seed"
    (let [node-a (gen-node {:x 1})
          node-b (gen-node {:y 2})
          node-c (gen-node {:z 3})
          nodes [node-a node-b node-c]
          seed (zipmap (map ::node/id nodes) nodes)
          cache (msc/node-cache seed)]
      (is (satisfies? cache/CacheProtocol cache))
      (is (= 36 (msc/cache-size cache)))
      (is (= node-a (get cache (::node/id node-a)))))))


(deftest cacheability-logic
  (is (false? (msc/cacheable? (msc/node-cache {} :total-size-limit 500)
                              nil))
      "nil node should not be cacheable")
  (is (false? (msc/cacheable? (msc/node-cache {} :total-size-limit 500)
                              {::node/size 501}))
      "node larger than the total size limit should not be cacheable")
  (is (false? (msc/cacheable? (msc/node-cache {}
                                              :total-size-limit 500
                                              :node-size-limit 100)
                              {::node/size 101}))
      "node larger than the node size limit should not be cacheable")
  (is (true? (msc/cacheable? (msc/node-cache {}
                                             :total-size-limit 500
                                             :node-size-limit 100)
                             {::node/size 100}))
      "node within size limits should be cacheable")
  (is (true? (msc/cacheable? (msc/node-cache {})
                             {::node/size 100}))
      "node within default size limits should be cacheable"))


(deftest space-reaping
  (let [cache (into (msc/node-cache {} :total-size-limit 64)
                    (map (comp (juxt ::node/id identity)
                           gen-node
                           (partial array-map :i)))
                    (range 30))]
    (is (<= (msc/cache-size cache) 64)
        "cache size is smaller than total size limit")
    (is (<= (msc/cache-size (msc/reap cache 32)) 32)
        "reap cleans up at least the desired amount of free space")))


(deftest cache-seeding
  (let [node-a (gen-node {:x 1})
        node-b (gen-node {:y 2})
        node-c (gen-node {:z 3})
        nodes [node-a node-b node-c]
        seed (zipmap (map ::node/id nodes) nodes)
        cache (msc/node-cache seed)]
    (is (satisfies? cache/CacheProtocol cache))
    (is (= 36 (msc/cache-size cache)))
    (is (cache/has? cache (::node/id node-a)))
    (is (= node-a (get cache (::node/id node-a))))
    (is (= ::not-found (cache/lookup cache "foo" ::not-found)))))


(deftest lru-behavior
  (let [node-a (gen-node {:x 1})
        node-b (gen-node {:y 2})
        node-c (gen-node {:z 3})
        id-a (::node/id node-a)
        id-b (::node/id node-b)
        id-c (::node/id node-c)
        cache (-> (msc/node-cache {} :total-size-limit 30)
                  (cache/miss id-a node-a)
                  (cache/miss id-b node-b))]
    (testing "missing ids"
      (is (identical? cache (cache/hit cache id-c))))
    (testing "before filling"
      (is (cache/has? cache id-a))
      (is (cache/has? cache id-b))
      (is (not (cache/has? cache id-c))))
    (testing "with no hits"
      (let [cache (cache/miss cache id-c node-c)]
        (is (not (cache/has? cache id-a)))
        (is (cache/has? cache id-b))
        (is (cache/has? cache id-c))))
    (testing "with lookup hit"
      (let [cache (-> cache
                      (cache/hit id-a)
                      (cache/miss id-c node-c))]
        (is (cache/has? cache id-a))
        (is (not (cache/has? cache id-b)))
        (is (cache/has? cache id-c))))
    (testing "evict-least"
      ; Stretch for 100% coverage
      (let [cache (msc/node-cache {})]
        (is (identical? cache (@#'msc/evict-least cache)))))
    (testing "node eviction"
      (let [cache (cache/evict cache id-b)]
        (is (identical? cache (cache/evict cache id-b)))
        (is (cache/has? cache id-a))
        (is (not (cache/has? cache id-b)))
        (is (not (cache/has? cache id-c)))))
    (testing "uncacheable node"
      (let [big-node (gen-node {:long (apply str (range 50))})]
        (is (identical? cache (cache/miss cache (::node/id big-node) big-node)))))))
