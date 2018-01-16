(ns merkledag.codec.edn-test
  (:require
    [clojure.test :refer :all]
    [merkledag.codec.edn :as edn]
    [multistream.codec :as codec])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(def test-types
  {'test/foo {:description "Test type"
              :reader #(vector :test/foo %)
              :writers {clojure.lang.Ratio str}}})


(deftest edn-codec
  (let [codec (edn/edn-codec test-types)]
    (testing "predicates"
      (is (false? (codec/processable? codec "/bin/")))
      (is (true? (codec/processable? codec "/edn"))))
    (testing "encoding"
      (is (= "\u0005/edn\nnil\n" (String. (codec/encode codec nil)))
          "nil value should encode to nil string")
      (is (= "\u0005/edn\nfalse\n" (String. (codec/encode codec false))))
      (is (= "\u0005/edn\n(123 foo :bar)\n" (String. (codec/encode codec '(123 foo :bar))))))
    (testing "decoding"
      (is (= {:alpha true, :beta 'bar, "foo" 123}
             (codec/decode codec (.getBytes "\u0005/edn\n{:alpha true :beta bar \"foo\" 123}\n"))))
      (is (= :foo (codec/decode codec (.getBytes "\u0005/edn\n:foo\nbar\n456\n")))
          "should only decode first value"))))
