(ns merkledag.core-test
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-store]]
    [clojure.test :refer :all]
    [merkledag.codecs.edn :as edn]
    [merkledag.core :as merkle]
    [merkledag.data :as data]
    [merkledag.refs.memory :refer [memory-tracker]]
    [multihash.core :as multihash]
    [puget.dispatch :as dispatch]
    [puget.printer :as puget])
  (:import
    blocks.data.Block
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(def dprint-opts
  {:print-color true
   :print-handlers
   (dispatch/chained-lookup
     {MerkleLink (puget/tagged-handler 'data/link (juxt :name :target :tsize))
      Multihash (puget/tagged-handler 'data/hash multihash/base58)}
     (edn/types->print-handlers data/core-types)
     puget/common-handlers)})


(defmacro dprint
  [v]
  `(do
     (print "DEBUG: ")
     (puget/pprint (quote ~v) dprint-opts)
     (puget/pprint ~v dprint-opts)))


(def hash-1 (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh"))
(def hash-2 (multihash/decode "Qmd8kgzaFLGYtTS1zfF37qKGgYQd5yKcQMyBeSa8UkUz4W"))


(defn make-test-repo
  []
  (merkle/graph-repo
    :store (memory-store)
    :refs (memory-tracker)))


(deftest node-construction
  (let [codec (:codec (make-test-repo))]
    (testing "with no data"
      (is (nil? (merkle/node* codec nil))))
    (testing "with only data"
      (let [node (merkle/node* codec
                   {:data/type :bar})]
        (is (instance? Block node))
        (is (nil? (:links node)))
        (is (= {:data/type :bar} (:data node)))))
    (testing "with only links"
      (let [node (merkle/node* codec
                   [(merkle/link* "@context" hash-1)]
                   nil)]
        (is (instance? Block node))
        (is (vector? (:links node)))
        (is (= 1 (count (:links node))))
        (is (every? (partial instance? MerkleLink) (:links node)))
        (is (= "@context" (get-in node [:links 0 :name])))
        (is (nil? (:data node)))))
    (testing "with data and table-only links"
      (let [node (merkle/node* codec
                   [(merkle/link* "@context" hash-1)]
                   {:data/type :abc/xyz
                    :data/ident "foo-bar"})]
        (is (instance? Block node))
        (is (vector? (:links node)))
        (is (= 1 (count (:links node))))
        (is (every? (partial instance? MerkleLink) (:links node)))
        (is (= "@context" (get-in node [:links 0 :name])))
        (is (= :abc/xyz (get-in node [:data :data/type])))))
    (testing "with links in data"
      (let [foo-block (block/read! "foo thing")
            node (merkle/node* codec
                   nil
                   {:data/type :abc/xyz
                    :thing (merkle/link* "foo" foo-block)})]
        (is (instance? Block node))
        (is (= 1 (count (:links node))))
        (is (every? (partial instance? MerkleLink) (:links node)))
        (is (= ["foo"] (map :name (:links node))))
        (is (= [(:id foo-block)] (map :target (:links node))))
        (is (= :abc/xyz (get-in node [:data :data/type])))))
    (testing "with data and links"
      (let [foo-block (block/read! "foo thing")
            node (merkle/node* codec
                   [(merkle/link* "qux" hash-1)
                    (merkle/link* "foo" foo-block)]
                   {:data/type :abc/xyz
                    :thing (merkle/link* "foo" foo-block)})]
        (is (instance? Block node))
        (is (= 2 (count (:links node))))
        (is (every? (partial instance? MerkleLink) (:links node)))
        (is (= ["qux" "foo"] (map :name (:links node))))
        (is (= [hash-1 (:id foo-block)] (map :target (:links node))))
        (is (= (second (:links node)) (get-in node [:data :thing])))))))
