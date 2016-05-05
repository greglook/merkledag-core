(ns merkledag.link-test
  (:require
    [blocks.core :as block]
    [clojure.test :refer :all]
    [merkledag.link :as link]
    [multihash.core :as multihash]
    [multihash.digest :as digest])
  (:import
    (merkledag.link
      LinkIndex
      MerkleLink)))


;; ## Merkle Link Tests

(deftest link-construction
  (testing "non-string name"
    (is (thrown? IllegalArgumentException
                 (link/create :foo nil nil))))
  (testing "illegal name character"
    (is (thrown? IllegalArgumentException
                 (link/create "foo/bar" nil nil))))
  (testing "non-multihash target"
    (is (thrown? IllegalArgumentException
                 (link/create "foo" :bar nil))))
  (testing "non-integer tsize"
    (is (thrown? IllegalArgumentException
                 (link/create "foo" nil 123.0))))
  (testing "valid args"
    (is (instance? MerkleLink (link/create "foo" nil nil))
        "should create broken link")
    (is (instance? MerkleLink (link/create "foo" (digest/sha2-256 "foo") 123))
        "should create valid link")))


(deftest link-translation
  (is (nil? (link/link->form nil)))
  (is (nil? (link/form->link nil)))
  (is (thrown? Exception (link/form->link #{:a "b"})))
  (let [target (digest/sha2-256 "foo")
        link (link/create "foo" target 123)]
    (is (= ["foo" target 123] (link/link->form link)))
    (is (= link (link/form->link ["foo" target 123])))))


(deftest link-properties
  (let [mhash (digest/sha2-256 "foo bar baz")
        a (link/create "foo" mhash 123)
        b (link/create "foo" mhash nil)
        c (link/create "bar" mhash 123)
        d (link/create "foo" (digest/sha2-256 "qux") 123)]
    (testing "keyword lookup"
      (is (= "foo" (:name a)) "should return name for :name")
      (is (= mhash (:target a)) "should return target for :target")
      (is (= 123 (:tsize a)) "should return total size for :tsize")
      (is (= ::not-found (get b :something ::not-found))
          "should return not-found for other keywords"))
    (testing "equality"
      (is (= a a) "should be reflexive")
      (is (= a b) "should not consider total size")
      (is (not= a c) "should consider name")
      (is (not= a d) "should consider target"))
    (testing "hash code"
      (is (= (hash a) (hash b)) "should not consider total size")
      (is (not= (hash a) (hash c)) "should consider name")
      (is (not= (hash a) (hash d)) "should consider target"))
    (testing "comparison"
      (is (zero? (compare a a)) "should be reflexive")
      (is (zero? (compare a b)) "should not consider total size")
      (is (neg? (compare c a)) "should sort by name first")
      (is (pos? (compare a d)) "should sort by target second"))
    (testing "metadata"
      (is (nil? (meta a)) "starts empty")
      (let [a' (vary-meta a assoc ::foo "bar")]
        (is (= a a') "should not affect equality")
        (is (= "bar" (::foo (meta a'))))))))



;; ## Link Tables

(deftest link-table
  (let [a (link/create "a" (digest/sha1 "foo") 20)
        b (link/create "b" (digest/sha1 "bar") 87)
        c (link/create "c" (digest/sha1 "baz") 11)
        table [a b c]]
    (testing "validate links"
      (is (= table (link/validate-links! table)))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"multiple targets for the same name"
            (link/validate-links! (conj table (link/create "c" (digest/sha1 "qux") 1)))))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"illegal names"
            (link/validate-links! (conj table (MerkleLink. "d/e" (digest/sha1 "qux") 1 nil))))))
    (testing "compaction"
      (is (= [a b c] (link/compact-links [b a nil c b nil]))))
    (testing "resolve-name"
      (testing "with nil name"
        (is (nil? (link/resolve-name [(link/create "" (:target a) nil)] nil))))
      (testing "with explicit table"
        (is (= a (link/resolve-name table "a")))
        (is (nil? (link/resolve-name table "d")))))
    (testing "update-link"
      (is (= ::x (link/update-link ::x nil))
          "nil link should not change argument")
      (is (= {:links [a], :data nil}
             (link/update-link nil a))
          "nil table should update to one link vector")
      (is (= {:links [b c a], :data nil}
             (link/update-link {:links [b c]} a))
          "new link should append to table")
      (let [b' (link/create "b" (digest/sha1 "qux") 32)]
        (is (= {:links [a b' c], :data #{b'}}
               (link/update-link {:links table, :data #{b}} b'))
            "new link should replace existing link position")))))



;; ## Link Indexes

(deftest link-discovery
  (let [a (link/create "foo" (digest/sha1 "foo") nil)
        b (link/create "bar" (digest/sha1 "bar") 384)
        c (link/create "baz" (digest/sha2-256 "baz") 123)]
    (is (= #{a b c} (link/find-links {:foo a, :bar [b 123 #{c}]})))))


(deftest link-indices
  (let [table [(link/create "foo" (digest/sha1 "foo") nil)
               (link/create "bar" (digest/sha1 "bar") 384)
               (link/create "baz" (digest/sha2-256 "baz") 123)]]
    (testing "construction and lookup"
      (is (instance? LinkIndex (link/link-index 0)))
      (is (= (link/link-index 0) (link/link-index table (first table))))
      (is (= (link/link-index 2) (link/link-index table (last table))))
      (is (nil? (link/link-index nil (first table))))
      (is (nil? (link/link-index table (link/create "qux" (digest/sha1 "foo") nil)))))
    (testing "walk replacement"
      (let [data-with-links
            {:foo #{(nth table 0) (nth table 1)}
             :baz {(nth table 1) :bad
                   (nth table 2) :ok}}
            data-with-indexes
            {:foo #{(link/link-index 0) (link/link-index 1)}
             :baz {(link/link-index 1) :bad
                   (link/link-index 2) :ok}}]
        (is (= data-with-indexes (link/replace-links table data-with-links)))
        (is (= data-with-links (link/resolve-indexes table data-with-indexes))))
      (is (thrown? Exception (link/replace-links table [(link/create "qux" (digest/sha1 "qux") nil)])))
      (is (thrown? Exception (link/resolve-indexes table [(link/link-index 5)]))))))



;; ## Link Utilities

(deftest link-targeting
  (testing "multihashes"
    (let [mh (digest/sha1 "foo")
          link (link/link-to mh "a")]
      (is (= "a" (:name link)))
      (is (= mh (:target link)))
      (is (nil? (:tsize link)))))
  (testing "merkle links"
    (let [ml (link/create "x" (digest/sha1 "bar") 123)
          link (link/link-to ml "b")]
      (is (= "b" (:name link)))
      (is (= (:target ml) (:target link)))
      (is (= 123 (:tsize link)))))
  (testing "block"
    (let [block (block/read! "abcd1234")]
      (testing "without links"
        (let [link (link/link-to block "x")]
          (is (= "x" (:name link)))
          (is (= (:id block) (:target link)))
          (is (= (:size block) (:tsize link)))))
      (testing "with links"
        (let [block' (assoc block :links [(link/create "x" nil 5)
                                          (link/create "y" nil 8)
                                          (link/create "z" nil 7)])
              link (link/link-to block' "g")]
          (is (= "g" (:name link)))
          (is (= (:id block) (:target link)))
          (is (= (+ (:size block) 20) (:tsize link))))))))
