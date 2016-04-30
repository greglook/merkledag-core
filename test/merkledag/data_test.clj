(ns merkledag.data-test
  (:require
    [clojure.test :refer :all]
    [merkledag.data :as data]))


(deftest data-plugins
  (let [types (data/load-types!)]
    (is (map? types))
    (is (every? symbol? (keys types)))
    (is (every? map? (vals types)))))
