(ns core.stream.test.seq
  (:use [clojure.test])

  (:use core.stream)
  (:require [core.stream.seq :as stream]))

;; Producers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest produce-seq-test
  (let [result (run* (stream/produce-seq 5 (range 1 20))
                     stream/consume)]
    (is (= (range 1 20) (:result result)))
    (is (= eof (:remainder result)))))

(deftest produce-iterate-test
  (let [result (run* (stream/produce-iterate inc 1)
                     (stream/take 30))]
    (is (= (range 1 31) (:result result)))
    (is (= [31 32] (:remainder result)))))

(deftest produce-repeat-test
  (let [result (run* (stream/produce-repeat "hello")
                     stream/peek)]
    (is (= "hello" (:result result)))
    (is (= (replicate 8 "hello") (:remainder result)))))

(deftest produce-replicate-test
  (let [result (run* (stream/produce-replicate 10 "hello")
                     stream/consume)]
    (is (= (replicate 10 "hello") (:result result)))
    (is (= eof (:remainder result)))))

(defn binary-unfold [n]
  (if (<= n 0)
    nil
    [(mod n 2) (int (/ n 2))]))

(deftest produce-unfold-test
  (let [result (run* (stream/produce-unfold binary-unfold 8)
                     stream/consume)]
    (is (= [0 0 0 1] (:result result)))
    (is (= eof (:remainder result)))))

;; Consumers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- not-fizzbuzz [a]
  (not (and (= (mod a 3) 0)
            (= (mod a 5) 0))))

(deftest take-test
  (let [result (run* (stream/produce-seq 5 (range 1 20))
                     (stream/take 7))]
    (is (= (range 1 8) (:result result)))
    (is (= [8 9 10] (:remainder result)))))

(deftest take-while-test
  (let [result (run* (stream/produce-seq 6 (range 1 20))
                     (stream/take-while not-fizzbuzz))]
    (is (= (range 1 15)  (:result result)))
    (is (= (range 15 19) (:remainder result)))))

(deftest drop-test
  (let [result (run* (stream/produce-seq 3 (range 1 20))
                     (stream/drop 5))]
    (is (nil? (:result result)))
    (is (= [6] (:remainder result)))))

(deftest drop-while-test
  (let [result (run* (stream/produce-seq 7 (range 1 20))
                     (stream/drop-while #(<= % 10)))]
    (is (nil? (:result result)))
    (is (= (range 11 15) (:remainder result)))))

(deftest reduce-test
  (let [result (run* (stream/produce-seq 7 (range 1 5))
                     (stream/reduce + 0))]
    (is (= 10 (:result result)))
    (is (= eof (:remainder result)))))

(deftest first-test
  (let [result (run* (stream/produce-seq 7 (range 21 30))
                     stream/first)]
    (is (= 21 (:result result)))
    (is (= (range 22 28) (:remainder result)))))

(deftest peek-test
  (let [result (run* (stream/produce-seq 7 (range 1 20))
                     stream/peek)]
    (is (= 1 (:result result)))
    (is (= (range 1 8) (:remainder result)))))

;; Filters  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest mapcat*-test
  (let [result (run* (stream/produce-seq 7 (range 1 4))
                     (stream/mapcat* #(vector % %))
                     stream/consume)]
    (is (= [1 1 2 2 3 3] (:result result)))
    (is (= eof (:remainder result)))))

(deftest map*-test
  (let [result (run* (stream/produce-seq 7 (range 1 10))
                     (stream/map* #(+ % 10))
                     stream/consume)]
    (is (= (range 11 20) (:result result)))
    (is (= eof (:remainder result)))))


(deftest filter*-test
  (let [result (run* (stream/produce-seq (range 0 11))
                     (stream/filter* #(= 0 (mod % 2)))
                     (stream/take 5))]
    (is (= [0 2 4 6 8] (:result result)))
    (is (= [10] (:remainder result)))))


(deftest zip*-test
  (let [result (run* (stream/produce-seq 7 (range 1 4))
                     (stream/zip*)
                     [(stream/mapcat* #(vector % %) stream/consume)
                      stream/consume])]
    (is (= [[1 1 2 2 3 3] [1 2 3]] (map :result result)))
    (is (= [eof eof] (map :remainder result)))))


(deftest drop-while*-test
  (let [result (run* (stream/produce-seq 6 (range 1 20))
                     (stream/drop-while* not-fizzbuzz)
                     stream/first)]
    (is (= 15 (:result result)))
    (is [16 17 18] (:remainder result))))


(deftest isolate*-test
  (let [result (run* (stream/produce-seq 7 (range 1 10000))
                     (stream/isolate* 5)
                     stream/consume)]
    (is (= (range 1 6) (:result result)))
    (is (= eof (:remainder result)))))


(deftest require*-test
  (is (thrown? Exception
               (run* (stream/produce-seq 2 (range 1 8))
                     (stream/require* 8)
                     stream/consume))))


(deftest stream-while*-test
  (let [result (run* (stream/produce-seq 10 (range 1 20))
                     (stream/stream-while* not-fizzbuzz)
                     stream/consume)]
    (is (= (range 1 15) (:result result)))
    (is (range 15 20) (:remainder result))))


(deftest split-when*-test
  (let [result (run* (stream/produce-seq 10 (range 1 12))
                     (stream/split-when* #(= 0 (mod % 3)))
                     stream/consume)]
    (is (= [[1 2 3] [4 5 6] [7 8 9] [10 11]] (:result result)))
    (is eof (:remainder result))))

