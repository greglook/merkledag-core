(ns merkledag.codec.edn-test
  (:require
    [clojure.test :refer :all]
    [merkledag.codec.edn :as edn]
    [multicodec.core :as codec])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(def test-types
  {'test/foo {:description "Test type"
              :reader #(vector :test/foo %)
              :writers {clojure.lang.Ratio str}}})


(deftest edn-codec
  (let [codec (edn/edn-codec test-types)
        test-encode #(let [baos (ByteArrayOutputStream.)]
                       (with-open [stream (codec/encode-stream codec :edn baos)]
                         (codec/write! stream %))
                       (String. (.toByteArray baos)))
        test-decode #(let [bais (ByteArrayInputStream. (.getBytes %))]
                       (with-open [stream (codec/decode-stream codec (:header codec) bais)]
                         (codec/read! stream)))]
    (testing "predicates"
      (is (false? (codec/processable? codec "/bin/")))
      (is (true? (codec/processable? codec "/edn"))))
    (testing "encoding"
      (is (= "\u0005/edn\nnil\n" (test-encode nil))
          "nil value should encode to nil string")
      (is (= "\u0005/edn\nfalse\n" (test-encode false)))
      (is (= "\u0005/edn\n(123 foo :bar)\n" (test-encode '(123 foo :bar)))))
    (testing "decoding"
      (is (= {:alpha true, :beta 'bar, "foo" 123}
             (test-decode "{:alpha true :beta bar \"foo\" 123}")))
      (is (= :foo (test-decode ":foo\nbar\n456\n"))
          "should only decode first value"))))
