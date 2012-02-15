(ns river.test.core
  (:use clojure.test)

  (:require [river.seq :as rs])
  (:use river.core))

(deftest test-gen-producer
  (let [producer (gen-producer #(rs/produce-seq (range 1 5) %)
                               #(rs/produce-seq (range 5 10) %)
                               #(rs/filter* even? %))
        result (run (producer rs/consume))]
    (is (= [6 8 2 4] (:result result)))))

(deftest test-gen-producer>
  (let [producer (gen-producer> (rs/produce-seq (range 1 5))
                                (rs/produce-seq (range 5 10))
                                (rs/filter* even?))
        result (run (producer rs/consume))]
    (is (= [6 8 2 4] (:result result)))))
