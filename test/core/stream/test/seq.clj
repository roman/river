(ns core.stream.test.seq
  (:use [clojure.test])

  (:use core.stream)
  (:require [core.stream.seq :as stream]))


(deftest produce-seq-test
  (let [result (run*
                  (stream/produce-seq 5 (range 1 20)
                   stream/consume))]
    (is (= (range 1 20) (:result result)))
    (is (= eof (:remainder result)))))


(deftest take-test
  (let [result (run*
                  (stream/produce-seq 5 (range 1 20)
                    #(stream/take 7 %)))]
    (is (= (range 1 8) (:result result)))
    (is (= [8 9 10] (:remainder result)))))

(defn- not-fizzbuzz [a]
  (not (and (= (mod a 3) 0)
            (= (mod a 5) 0))))

(deftest take-while-test
  (let [result (run*
                  (stream/produce-seq 6 (range 1 20)
                    #(stream/take-while not-fizzbuzz %)))]
    (is (= (range 1 15)  (:result result)))
    (is (= (range 15 19) (:remainder result)))))


(deftest reduce-test
  (let [result (run*
                  (stream/produce-seq 7 (range 1 5)
                    #(stream/reduce + 0 %)))]
    (is (= 10 (:result result)))
    (is (= eof (:remainder result)))))

(deftest first-test
  (let [result (run*
                 (stream/produce-seq 7 (range 21 30)
                    stream/first))]
    (is (= 21 (:result result)))
    (is (= (range 22 28) (:remainder result)))))


(deftest map*-test
  (let [result (run*
                 (stream/produce-seq 7 (range 1 10)
                    (stream/map* #(+ % 10) stream/consume)))]
    (is (= (range 11 20) (:result result)))
    (is (= eof (:remainder result)))))

(deftest mapcat*-test
  (let [result (run*
                 (stream/produce-seq 7 (range 1 4)
                   (stream/mapcat* #(vector % %) stream/consume)))]
    (is (= [1 1 2 2 3 3] (:result result)))
    (is (= eof (:remainder result)))))

(deftest zip*-test
  (let [result (run*
                  (stream/produce-seq 7 (range 1 4)
                    (stream/zip* (stream/mapcat* #(vector % %)
                                                 stream/consume)
                                 stream/consume)))]
    (is (= [[1 1 2 2 3 3] [1 2 3]] (map :result result)))
    (is (= [eof eof] (map :remainder result)))))


(deftest drop-while*-test
  (let [result (run*
                  (stream/produce-seq 6 (range 1 20)
                    (stream/drop-while* not-fizzbuzz
                                        stream/first)))]
    (is (= 15 (:result result)))
    (is [16 17 18] (:remainder result))))


(deftest isolate*-test
  (let [result (run*
                 (stream/produce-seq 7 (range 1 10000)
                    (stream/isolate* 5 stream/consume)))]
    (is (= (range 1 6) (:result result)))
    (is (= eof (:remainder result)))))

(deftest require*-test
  (is (thrown? Exception
               (run*
                 (stream/produce-seq 2 (range 1 8)
                 (stream/require* 8 stream/consume))))))

(deftest stream-while*-test
  (let [result (run* 
                  (stream/produce-seq 10 (range 1 20)
                    (stream/stream-while* not-fizzbuzz stream/consume)))]
    (is (= (range 1 15) (:result result)))
    (is (range 15 20) (:remainder result))))

(deftest split-when*-test
  (let [result (run*
                  (stream/produce-seq 10 (range 1 12)
                    (stream/split-when* #(= 0 (mod % 3)) 
                      stream/consume)))]
    (is (= [[1 2 3] [4 5 6] [7 8 9] [10 11]] (:result result)))
    (is eof (:remainder result))))

