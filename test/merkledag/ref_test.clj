(ns merkledag.ref-test
  (:require
    [blocks.core :as block]
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [merkledag.link :as link]
    [merkledag.ref :as ref]
    (merkledag.ref
      [file :refer [file-ref-tracker]]
      [memory :refer [memory-ref-tracker]])
    [multihash.core :as multihash]
    [multihash.digest :as digest]))


(defn test-ref-tracker
  [tracker]
  (let [id-a (digest/sha1 "foo")
        id-b (digest/sha2-256 "bar")]
    (is (empty? (ref/list-refs tracker nil))
        "starts empty")
    (testing "read non-exsistent ref"
      (is (nil? (ref/get-ref tracker "foo")))
      (is (nil? (ref/get-ref tracker "bar" 123)))
      (is (nil? (ref/get-history tracker "baz"))))
    (testing "set new ref"
      (let [v (ref/set-ref! tracker "foo" id-a)]
        ;(is (s/validate ref/RefVersion v))
        (is (= id-a (::ref/value v)))
        (is (= "foo" (::ref/name v)))))
    (testing "read existing ref"
      (let [v (ref/get-ref tracker "foo")]
        ;(is (s/validate ref/RefVersion v))
        (is (= id-a (::ref/value v)))
        (is (= "foo" (::ref/name v)))))
    (testing "set ref to same id"
      (let [v1 (ref/set-ref! tracker "bar" id-a)
            v2 (ref/set-ref! tracker "bar" id-a)]
        (is (= v1 v2) "version should not change")))
    (testing "update ref with new id"
      (let [v1 (ref/get-ref tracker "bar")
            v2 (ref/set-ref! tracker "bar" id-b)]
        ;(is (s/validate ref/RefVersion v2))
        (is (not= v1 v2) "version should change")
        (is (pos? (compare (::ref/version v2) (::ref/version v1)))
            "second version should come after first")))
    (testing "ref listing"
      (let [refs (ref/list-refs tracker {})]
        (is (= 2 (count refs)))
        (is (= ["bar" "foo"] (map ::ref/name refs)))
        (is (= [id-b id-a] (map ::ref/value refs)))))
    (testing "get ref versions"
      (let [hist (ref/get-history tracker "bar")]
        (is (= 2 (count hist)))
        (is (= (first hist) (ref/get-ref tracker "bar")))
        (is (= (second hist) (ref/get-ref tracker "bar" (::ref/version (second hist)))))))
    (testing "write ref tombstone"
      (let [v (ref/set-ref! tracker "foo" nil)]
        ;(is (s/validate ref/RefVersion v))
        (is (= 1 (count (ref/list-refs tracker nil)))
            "list only returns non-nil refs by default")
        (is (= 2 (count (ref/list-refs tracker {:include-nil true})))
            "list returns nil refs with option")
        (is (nil? (::ref/value (ref/get-ref tracker "foo"))))))
    (testing "delete nonexistent ref"
      (is (false? (ref/delete-ref! tracker "baz"))))
    (testing "delete existing ref"
      (is (true? (ref/delete-ref! tracker "foo")))
      (is (nil? (ref/get-ref tracker "foo"))))))


(deftest memory-tracker-spec
  (test-ref-tracker (memory-ref-tracker)))


(deftest file-tracker-spec
  (let [tmpdir (io/file "target" "test" "tmp"
                        (str "file-tracker." (System/currentTimeMillis)))]
    (test-ref-tracker (file-ref-tracker tmpdir))))
