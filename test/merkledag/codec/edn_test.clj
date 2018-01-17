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
          "should only decode first value"))
    (testing "eof behavior"
      (let [bais (ByteArrayInputStream. (byte-array 0))]
        (with-open [decoder (codec/decode-byte-stream codec nil bais)]
          (let [err-type (try
                           (codec/read! decoder)
                           nil
                           (catch Exception ex
                             (:type (ex-data ex))))]
            (do-report
              {:type (if (= err-type ::codec/eof) :pass :fail)
               :message "EOF read should throw correct ex-info type"
               :expected ::codec/eof
               :actual err-type}))
          (binding [codec/*eof-guard* (Object.)]
            (is (identical? codec/*eof-guard* (codec/read! decoder)))))))))
