(ns merkledag.format-test
  (:require
    [blocks.core :as block]
    [clojure.test :refer :all]
    (merkledag
      [format :as format]
      [link :as link])
    [multicodec.core :as codec]
    [multicodec.codecs :refer [text-codec]]
    [multihash.core :as multihash]))


(def test-format
  (format/protobuf-format (text-codec)))


(deftest format-construction
  (is (thrown? IllegalArgumentException
               (format/protobuf-format
                 (reify codec/Encoder
                   (encode!
                     [_ output value]
                     nil)))))
  (is (thrown? IllegalArgumentException
               (format/protobuf-format
                 (reify codec/Decoder
                   (decode!
                     [_ input]
                     nil))))))


(deftest node-construction
  (is (nil? (format/format-node test-format nil nil)))
  (let [data "foo bar baz"
        node (format/format-node test-format nil data)]
    (is (pos? (:size node)))
    (is (nil? (:links node)))
    (is (= data (:data node))))
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
  (testing "protobuf block"
    (let [data "foo bar baz"
          links [(link/create "@context" (multihash/sha1 "foo") 123)
                 (link/create "abc" (multihash/sha1 "bar") nil)
                 (link/create "xyz" (multihash/sha2-256 "baz") 87)]
          node (format/format-node test-format links data)
          block (dissoc node :links :data)
          node' (format/parse-node test-format block)]
      (is (= (:id node) (:id node')))
      (is (= (:size node) (:size node')))
      (is (= links (:links node')))
      (is (string? (:data node')))
      (is (= data (:data node'))))))
