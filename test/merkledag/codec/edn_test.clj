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
  (let [edn (edn/edn-codec test-types)
        test-encode #(let [baos (ByteArrayOutputStream.)]
                       (codec/encode! edn baos %)
                       (String. (.toByteArray baos)))
        test-decode #(codec/decode edn (.getBytes %))]
    (testing "predicates"
      (is (true? (codec/encodable? edn "foo")))
      (is (true? (codec/encodable? edn :bar)))
      (is (true? (codec/encodable? edn 'qux)))
      (is (true? (codec/decodable? edn (:header edn))))
      (is (false? (codec/decodable? edn "/bin"))))
    (testing "encoding"
      (is (= "nil\n" (test-encode nil))
          "nil value should encode to nil string")
      (is (= "false\n" (test-encode false)))
      (is (= "(123 foo :bar)\n" (test-encode '(123 foo :bar)))))
    (testing "decoding"
      (is (= {:alpha true, :beta 'bar, "foo" 123} (test-decode "{:alpha true :beta bar \"foo\" 123}")))
      (is (= :foo (test-decode ":foo\nbar\n456\n"))
          "should only decode first value"))))
