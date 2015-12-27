(ns merkledag.data-test
  (:require
    [clojure.test :refer :all]
    [merkledag.data :as data]))


(deftest type-registration
  (is (thrown? IllegalArgumentException
               (data/register-types! :foo)))
  (is (map? (data/register-types! {clojure.lang.Symbol str})))
  (is (= str (get data/data-types clojure.lang.Symbol)))
  (is (map? (data/reset-types!)))
  (is (= data/core-types data/data-types)))
