(ns merkledag.link-test
  (:require
    [blocks.core :as block]
    [clojure.test :refer :all]
    [merkledag.link :as link]
    [multihash.core :as multihash])
  (:import
    (merkledag.link
      LinkIndex
      MerkleLink)))


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
    (is (instance? MerkleLink (link/create "foo" (multihash/sha2-256 "foo") 123))
        "should create valid link")))


(deftest link-translation
  (is (nil? (link/link->form nil)))
  (is (nil? (link/form->link nil)))
  (is (thrown? Exception (link/form->link #{:a "b"})))
  (let [target (multihash/sha2-256 "foo")
        link (link/create "foo" target 123)]
    (is (= ["foo" target 123] (link/link->form link)))
    (is (= link (link/form->link ["foo" target 123])))))


(deftest link-properties
  (let [mhash (multihash/sha2-256 "foo bar baz")
        a (link/create "foo" mhash 123)
        b (link/create "foo" mhash nil)
        c (link/create "bar" mhash 123)
        d (link/create "foo" (multihash/sha2-256 "qux") 123)]
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


(deftest link-targeting
  (testing "multihashes"
    (let [mh (multihash/sha1 "foo")
          link (link/link-to mh "a")]
      (is (= "a" (:name link)))
      (is (= mh (:target link)))
      (is (nil? (:tsize link)))))
  (testing "merkle links"
    (let [ml (link/create "x" (multihash/sha1 "bar") 123)
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


(deftest link-table
  (let [a (link/create "a" (multihash/sha1 "foo") 20)
        b (link/create "b" (multihash/sha1 "bar") 87)
        c (link/create "c" (multihash/sha1 "baz") 11)
        table [a b c]]
    (testing "resolve-name"
      (testing "with nil name"
        (is (nil? (link/resolve-name [(link/create "" (:target a) nil)] nil))))
      (testing "with explicit table"
        (is (= a (link/resolve-name table "a")))
        (is (nil? (link/resolve-name table "d")))))
    (testing "update-links"
      (is (= table (link/update-links table nil))
          "nil link should not change table")
      (is (= [a] (link/update-links nil a))
          "nil table should update to one link vector")
      (is (= [b c a] (link/update-links [b c] a))
          "new link should append to table")
      (let [b' (link/create "b" (multihash/sha1 "qux") 32)]
        (is (= [a b' c] (link/update-links table b'))
            "new link should replace existing link position")))))


(deftest link-indices
  (let [table [(link/create "foo" (multihash/sha1 "foo") nil)
               (link/create "bar" (multihash/sha1 "bar") 384)
               (link/create "baz" (multihash/sha2-256 "baz") 123)]]
    (testing "construction and lookup"
      (is (instance? LinkIndex (link/link-index 0)))
      (is (= (link/link-index 0) (link/link-index table (first table))))
      (is (= (link/link-index 2) (link/link-index table (last table))))
      (is (nil? (link/link-index nil (first table))))
      (is (nil? (link/link-index table (link/create "qux" (multihash/sha1 "foo") nil)))))
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
      (is (thrown? Exception (link/replace-links table [(link/create "qux" (multihash/sha1 "qux") nil)])))
      (is (thrown? Exception (link/resolve-indexes table [(link/link-index 5)]))))))
