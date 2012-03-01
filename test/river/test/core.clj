(ns river.test.core
  (:use clojure.test)

  (:require [river.seq :as rs])
  (:use river.core))

(deftest test-*c-holds-eof-rule
  (let [filters* [(rs/mapcat* #(vec % %))
                  (rs/map* identity)
                  (rs/filter* (constantly true))
                  (rs/isolate* 10)
                  (rs/drop-while* (constantly true))
                  (rs/require* 0)
                  (rs/stream-while* (constantly true))
                  (rs/split-when* (constantly false))]

        consumers [rs/consume
                   (rs/take 2)
                   (rs/take-while (constantly true))
                   (rs/drop 2)
                   (rs/drop-while (constantly true))
                   rs/first
                   rs/peek]]
    (doseq [f filters*
          c consumers]
        (is (yield? (run (*c f c)))
            (str "expected " f " and " c " to yield a result")))))

(deftest p*-multiple-arity-test
  (let [result (run (p* (rs/produce-seq (range 1 1000))
                        (rs/filter* #(odd? %))
                        (rs/isolate* 10))
                    rs/consume)]
    (is (= [1 3 5 7 9 11 13 15 17 19] (:result result)))
    (is eof (:remainder result))))

(deftest *c-multiple-arity-test
  (let [result (run (rs/produce-seq (range 1 1000))
                    (*c (rs/filter* #(odd? %))
                        (rs/isolate* 10)
                        rs/consume))]
    (is (= [1 3 5 7 9 11 13 15 17 19] (:result result)))
    (is eof (:remainder result))))

(deftest concat-producers-test
  (let [producer (concat-producer (rs/produce-seq (range 1 20))
                                  (rs/produce-seq (range 20 37)))
        result (run producer
                    rs/consume)]
    (is (= (range 1 37) (:result result)))))

