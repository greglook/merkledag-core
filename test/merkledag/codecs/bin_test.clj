(ns merkledag.codecs.bin-test
  (:require
    [byte-streams :refer [bytes=]]
    [clojure.test :refer :all]
    [merkledag.codecs.bin :as bin]
    [merkledag.test-utils :refer [random-bytes]]
    [multicodec.core :as codec])
  (:import
    blocks.data.PersistentBytes
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)
    java.nio.ByteBuffer))


(deftest encodable-data
  (let [bin (bin/bin-codec)]
    (is (true? (codec/encodable? bin (byte-array 10))))
    (is (true? (codec/encodable? bin (ByteBuffer/wrap (random-bytes 5)))))
    (is (true? (codec/encodable? bin (PersistentBytes/wrap (random-bytes 5)))))
    (is (false? (codec/encodable? bin "string")))
    (is (false? (codec/encodable? bin :keyword)))))


(deftest bin-codec
  (let [bin (bin/bin-codec)]
    (testing "encoding"
      (testing "byte arrays"
        (let [data (random-bytes 32)]
          (is (bytes= data (codec/encode bin data))
              "should encode bytes one-to-one")))
      (testing "byte buffers"
        (let [data (random-bytes 32)
              buffer (ByteBuffer/wrap data)]
          (is (bytes= data (codec/encode bin buffer))
              "should encode bytes one-to-one")))
      (testing "persistent bytes"
        (let [data (random-bytes 32)
              pbytes (PersistentBytes/wrap data)]
          (is (bytes= pbytes (codec/encode bin pbytes))
              "should encode bytes one-to-one"))))
    (testing "decoding"
      (let [data (random-bytes 32)
            decoded (codec/decode bin data)]
        (is (instance? PersistentBytes decoded)
            "should decode bytes into PersistentBytes")
        (is (bytes= data decoded)
            "should decode bytes one-to-one")))))
