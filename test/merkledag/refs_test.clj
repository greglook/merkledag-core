(ns merkledag.refs-test
  (:require
    [blocks.core :as block]
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [merkledag.link :as link]
    [merkledag.refs :as refs]
    (merkledag.refs
      [file :refer [file-ref-tracker]]
      [memory :refer [memory-ref-tracker]])
    [multihash.core :as multihash]
    [multihash.digest :as digest]))


(defn test-ref-tracker
  [tracker]
  (let [id-a (digest/sha1 "foo")
        id-b (digest/sha2-256 "bar")]
    (is (empty? (refs/list-refs tracker nil))
        "starts empty")
    (testing "read non-exsistent ref"
      (is (nil? (refs/get-ref tracker "foo")))
      (is (nil? (refs/get-ref tracker "bar" 123)))
      (is (nil? (refs/get-ref-history tracker "baz"))))
    (testing "set new ref"
      (let [v (refs/set-ref! tracker "foo" id-a)]
        ;(is (s/validate refs/RefVersion v))
        (is (= id-a (:value v)))
        (is (= "foo" (:name v)))))
    (testing "read existing ref"
      (let [v (refs/get-ref tracker "foo")]
        ;(is (s/validate refs/RefVersion v))
        (is (= id-a (:value v)))
        (is (= "foo" (:name v)))))
    (testing "set ref to same id"
      (let [v1 (refs/set-ref! tracker "bar" id-a)
            v2 (refs/set-ref! tracker "bar" id-a)]
        (is (= v1 v2) "version should not change")))
    (testing "update ref with new id"
      (let [v1 (refs/get-ref tracker "bar")
            v2 (refs/set-ref! tracker "bar" id-b)]
        ;(is (s/validate refs/RefVersion v2))
        (is (not= v1 v2) "version should change")
        (is (pos? (compare (:version v2) (:version v1)))
            "second version should come after first")))
    (testing "ref listing"
      (let [refs (refs/list-refs tracker {})]
        (is (= 2 (count refs)))
        (is (= ["bar" "foo"] (map :name refs)))
        (is (= [id-b id-a] (map :value refs)))))
    (testing "get ref versions"
      (let [hist (refs/get-ref-history tracker "bar")]
        (is (= 2 (count hist)))
        (is (= (first hist) (refs/get-ref tracker "bar")))
        (is (= (second hist) (refs/get-ref tracker "bar" (:version (second hist)))))))
    (testing "write ref tombstone"
      (let [v (refs/set-ref! tracker "foo" nil)]
        ;(is (s/validate refs/RefVersion v))
        (is (= 1 (count (refs/list-refs tracker nil)))
            "list only returns non-nil refs by default")
        (is (= 2 (count (refs/list-refs tracker {:include-nil true})))
            "list returns nil refs with option")
        (is (nil? (:value (refs/get-ref tracker "foo"))))))
    (testing "delete nonexistent ref"
      (is (false? (refs/delete-ref! tracker "baz"))))
    (testing "delete existing ref"
      (is (true? (refs/delete-ref! tracker "foo")))
      (is (nil? (refs/get-ref tracker "foo"))))))


(deftest memory-tracker-spec
  (test-ref-tracker (memory-ref-tracker)))


(deftest file-tracker-spec
  (let [tmpdir (io/file "target" "test" "tmp"
                        (str "file-tracker." (System/currentTimeMillis)))]
    (test-ref-tracker (file-ref-tracker tmpdir))))
