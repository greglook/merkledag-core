(ns merkledag.codec.cbor-test
  (:require
    [alphabase.hex :as hex]
    [clojure.test :refer :all]
    [merkledag.codec.cbor :as cbor]
    [multistream.codec :as codec]
    [multistream.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest cbor-codec
  (let [codec (cbor/cbor-codec {})]
    (testing "predicates"
      (is (false? (codec/processable? codec "/bin/")))
      (is (true? (codec/processable? codec "/cbor/"))))
    (testing "encoding"
      (is (= "072f63626f722f0af6" (hex/encode (codec/encode codec nil))))
      (is (= "072f63626f722f0af4" (hex/encode (codec/encode codec false))))
      (is (= "072f63626f722f0aa0" (hex/encode (codec/encode codec {}))))
      (is (= "072f63626f722f0a83187bd82763666f6fd827643a626172"
             (hex/encode (codec/encode codec '(123 foo :bar))))))
    (testing "decoding"
      (is (= {} (codec/decode codec (hex/decode "072f63626f722f0aa0"))))
      (is (= '(123 foo :bar)
             (codec/decode codec (hex/decode "072f63626f722f0a83187bd82763666f6fd827643a626172")))))))
