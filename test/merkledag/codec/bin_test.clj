(ns merkledag.codec.bin-test
  (:require
    [byte-streams :refer [bytes=]]
    [clojure.test :refer :all]
    [merkledag.codec.bin :as bin]
    [merkledag.test-utils :refer [random-bytes]]
    [multicodec.core :as codec])
  (:import
    blocks.data.PersistentBytes
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)
    java.nio.ByteBuffer))


(deftest binary-data
  (is (true? (bin/binary? (byte-array 10))))
  (is (true? (bin/binary? (ByteBuffer/wrap (random-bytes 5)))))
  (is (true? (bin/binary? (PersistentBytes/wrap (random-bytes 5)))))
  (is (false? (bin/binary? "string")))
  (is (false? (bin/binary? :keyword))))


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
