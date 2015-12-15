(ns merkledag.format-test
  (:require
    [blocks.core :as block]
    [byte-streams :refer [bytes=]]
    [clojure.test :refer :all]
    (merkledag
      [format :as format]
      [link :as link]
      [test-utils :refer [random-bytes]])
    [multihash.core :as multihash])
  (:import
    blocks.data.PersistentBytes
    java.nio.ByteBuffer))


(def test-format
  (format/protobuf-edn-format))


(deftest node-construction
  (is (nil? (format/format-node test-format nil nil)))
  (let [data (random-bytes 32)
        node (format/format-node test-format nil data)]
    (is (= 41 (:size node))) ; 32 + 9 byte field overhead
    (is (nil? (:links node)))
    (is (bytes= data (:data node))))
  (let [data "the quick red fox jumped over the lazy dog"
        links [(link/create "@context" (multihash/sha1 "foo") 123)
               (link/create "abc" (multihash/sha1 "bar") nil)
               (link/create "xyz" (multihash/sha2-256 "baz") 87)]
        node (format/format-node test-format links data)]
    (is (pos? (:size node)))
    (is (= links (:links node)))
    (is (= data (:data node))))
  (let [links [(link/create "abc" (multihash/sha1 "xyz") 852)]
        node (format/format-node test-format links nil)]
    (is (pos? (:size node)))
    (is (= links (:links node)))
    (is (nil? (:data node)))))


(deftest node-parsing
  (testing "raw block"
    (let [block (block/read! "this is not valid protobuf")
          node (format/parse-node test-format block)]
      (is (nil? (:links node)))
      (is (nil? (:data node)))
      (is (= block (dissoc node :links :data)))))
  (testing "protobuf block with binary data"
    (let [data (PersistentBytes/wrap (random-bytes 32))
          links [(link/create "@context" (multihash/sha1 "foo") 123)
                 (link/create "abc" (multihash/sha1 "bar") nil)
                 (link/create "xyz" (multihash/sha2-256 "baz") 87)]
          node (format/format-node test-format links data)
          block (dissoc node :links :data)
          node' (format/parse-node test-format block)]
      (is (= (:id node) (:id node')))
      (is (= (:size node) (:size node')))
      (is (= links (:links node')))
      (is (instance? PersistentBytes (:data node')))
      (is (bytes= data (:data node')))))
  (testing "protobuf block with edn data"
    (let [data {:foo "bar", :baz 123}
          node (format/format-node test-format nil data)
          block (dissoc node :links :data)
          node' (format/parse-node test-format block)]
      (is (nil? (:links node')))
      (is (= data (:data node'))))))
