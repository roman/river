(ns river.test.core
  (:use clojure.test)

  (:require [river.seq :as rs])
  (:use river.core))

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

