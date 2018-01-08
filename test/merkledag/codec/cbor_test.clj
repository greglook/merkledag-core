(ns merkledag.codec.cbor-test
  (:require
    [alphabase.hex :as hex]
    [clojure.test :refer :all]
    [merkledag.codec.cbor :as cbor]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest cbor-codec
  (let [codec (cbor/cbor-codec {})
        test-encode #(let [baos (ByteArrayOutputStream.)]
                       (with-open [stream (codec/encode-byte-stream codec :edn baos)]
                         (codec/write! stream %))
                       (hex/encode (.toByteArray baos)))
        test-decode #(let [bais (ByteArrayInputStream. (hex/decode %))]
                       (is (= "/cbor/" (header/read! bais)))
                       (with-open [stream (codec/decode-byte-stream codec (:header codec) bais)]
                         (codec/read! stream)))]
    (testing "predicates"
      (is (false? (codec/processable? codec "/bin/")))
      (is (true? (codec/processable? codec "/cbor/"))))
    (testing "encoding"
      (is (= "072f63626f722f0af6" (test-encode nil)))
      (is (= "072f63626f722f0af4" (test-encode false)))
      (is (= "072f63626f722f0aa0" (test-encode {})))
      (is (= "072f63626f722f0a83187bd82763666f6fd827643a626172"
             (test-encode '(123 foo :bar)))))
    (testing "decoding"
      (is (= {} (test-decode "072f63626f722f0aa0")))
      (is (= '(123 foo :bar)
             (test-decode "072f63626f722f0a83187bd82763666f6fd827643a626172"))))))
