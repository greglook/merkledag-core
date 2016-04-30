(ns merkledag.node-test
  (:require
    [blocks.core :as block]
    [blocks.data :refer [clean-block]]
    [clojure.test :refer :all]
    [merkledag.data :as data]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multicodec.core :as codec]
    [multihash.digest :as digest]))


(def test-codec
  (node/node-codec data/core-types))


(deftest node-codec
  (testing "Encoder"
    (is (false? (codec/encodable? test-codec nil)))
    (is (false? (codec/encodable? test-codec "foo")))
    (is (false? (codec/encodable? test-codec {})))
    (is (false? (codec/encodable? test-codec {:links []})))
    (is (false? (codec/encodable? test-codec {:data nil})))
    (is (true? (codec/encodable? test-codec {:data "123"})))
    (is (true? (codec/encodable? test-codec {:links [(link/create "foo" (digest/sha1 "foo") 3)]})))
    (is (thrown? IllegalArgumentException
                 (codec/encode test-codec {:links []})))
    (is (thrown? IllegalArgumentException
                 (codec/encode test-codec {:data nil}))))
  (testing "Decoder"
    (is (false? (codec/decodable? test-codec "/bin")))
    (is (false? (codec/decodable? test-codec "/edn")))
    (is (true? (codec/decodable? test-codec "/merkledag/v1")))))


(deftest block-formatting
  (testing "nil value"
    (is (nil? (node/format-block test-codec nil))
        "formats as nil"))
  (testing "string value"
    (let [block (node/format-block test-codec "foo bar baz")]
      (is (= 11 (:size block)))
      (is (= "foo bar baz" (slurp (block/open block))))
      (is (contains? block :encoding)
          "block contains an encoding key")
      (is (nil? (:encoding block))
          "formats with nil encoding")))
  (testing "node with data value"
    (let [node (node/format-block test-codec {:data "foo bar baz"})]
      (is (= 28 (:size node)))
      (is (= "foo bar baz" (:data node)))
      (is (nil? (:links node)))
      (is (= ["/edn"] (:encoding node)))
      ))
  (testing "node with links"
    (let [node (node/format-block test-codec {:links [(link/create "foo" (digest/sha1 "foo") 3)]})]
      (is (nil? (:data node)))
      (is (vector? (:links node)))
      (is (= 1 (count (:links node)))))))


(deftest block-parsing
  (testing "nil block"
    (is (nil? (node/parse-block test-codec nil))))
  (testing "bad decode"
    (let [block (block/read! "foo bar baz")
          bad-codec (reify codec/Decoder
                      (decode! [_ input] "foo"))
          codec' (assoc test-codec :mux bad-codec)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (node/parse-block codec' block)))))
  (testing "node with links"
    (let [node (node/format-block test-codec {:links [(link/create "foo" (digest/sha1 "foo") 3)]})
          block (clean-block node)
          node' (node/parse-block test-codec block)]
        (is (nil? (:links block)))
        (is (= (:links node) (:links node'))))))
