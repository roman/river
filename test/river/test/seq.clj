(ns river.test.seq
  (:use [clojure.test])

  (:use river.core)
  (:require [river.seq :as rs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Producers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest produce-seq-test
  (let [result (run (rs/produce-seq 5 (range 1 20))
                    rs/consume)]
    (is (= (range 1 20) (:result result)))
    (is (= eof (:remainder result)))))

(deftest produce-iterate-test
  (let [result (run (rs/produce-iterate inc 1)
                    (rs/take 30))]
    (is (= (range 1 31) (:result result)))
    (is (= [31 32] (:remainder result)))))

(deftest produce-repeat-test
  (let [result (run (rs/produce-repeat "hello")
                     rs/peek)]
    (is (= "hello" (:result result)))
    (is (= (replicate 8 "hello") (:remainder result)))))

(deftest produce-replicate-test
  (let [result (run (rs/produce-replicate 10 "hello")
                    rs/consume)]
    (is (= (replicate 10 "hello") (:result result)))
    (is (= eof (:remainder result)))))

(defn binary-unfold [n]
  (if (<= n 0)
    nil
    [(mod n 2) (int (/ n 2))]))

(deftest produce-unfold-test
  (let [result (run (rs/produce-unfold binary-unfold 8)
                    rs/consume)]
    (is (= [0 0 0 1] (:result result)))
    (is (= eof (:remainder result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- not-fizzbuzz [a]
  (not (and (= (mod a 3) 0)
            (= (mod a 5) 0))))

(deftest take-test
  (let [result (run (rs/produce-seq 5 (range 1 20))
                    (rs/take 7))]
    (is (= (range 1 8) (:result result)))
    (is (= [8 9 10] (:remainder result)))))

(deftest take-while-test
  (let [result (run (rs/produce-seq 6 (range 1 20))
                    (rs/take-while not-fizzbuzz))]
    (is (= (range 1 15)  (:result result)))
    (is (= (range 15 19) (:remainder result)))))

(deftest drop-test
  (let [result (run (rs/produce-seq 3 (range 1 20))
                    (rs/drop 5))]
    (is (nil? (:result result)))
    (is (= [6] (:remainder result)))))

(deftest drop-while-test
  (let [result (run (rs/produce-seq 7 (range 1 20))
                    (rs/drop-while #(<= % 10)))]
    (is (nil? (:result result)))
    (is (= (range 11 15) (:remainder result)))))

(deftest reduce-test
  (let [result (run (rs/produce-seq 7 (range 1 5))
                    (rs/reduce + 0))]
    (is (= 10 (:result result)))
    (is (= eof (:remainder result)))))

(deftest first-test
  (let [result (run (rs/produce-seq 7 (range 21 30))
                     rs/first)]
    (is (= 21 (:result result)))
    (is (= (range 22 28) (:remainder result)))))

(deftest peek-test
  (let [result (run (rs/produce-seq 7 (range 1 20))
                    rs/peek)]
    (is (= 1 (:result result)))
    (is (= (range 1 8) (:remainder result)))))

(deftest zip-test
  (let [new-consumer (*c (rs/filter* #(odd? %))
                         (rs/mapcat* #(vector % %))
                         rs/consume)
        result (run (rs/produce-seq 7 (range 1 4))
                    (rs/zip new-consumer
                            rs/consume))]
    (is (= [[1 1 3 3] [1 2 3]] (:result result)))
    (is (= eof (:remainder result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Filters
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest mapcat*-test
  (let [result (run (rs/produce-seq 7 (range 1 4))
                    (*c (rs/mapcat* #(vector % %))
                        rs/consume))]
    (is (= [1 1 2 2 3 3] (:result result)))
    (is (= eof (:remainder result))))
  (let [result (run (p* (rs/produce-seq 7 (range 1 4))
                        (rs/mapcat* #(vector % %)))
                    rs/consume)]
    (is (= [1 1 2 2 3 3] (:result result)))
    (is (= eof (:remainder result)))))

(deftest map*-test
  (let [result (run (p* (rs/produce-seq 7 (range 1 10))
                        (rs/map* #(+ % 10)))
                    rs/consume)]
    (is (= (range 11 20) (:result result)))
    (is (= eof (:remainder result))))
  (let [result (run (rs/produce-seq 7 (range 1 10))
                    (*c (rs/map* #(+ % 10))
                        rs/consume))]
    (is (= (range 11 20) (:result result)))
    (is (= eof (:remainder result)))))


(deftest filter*-test
  (let [result (run (rs/produce-seq (range 0 11))
                    (*c (rs/filter* #(= 0 (mod % 2)))
                        rs/consume))]
    (is (= [0 2 4 6 8 10] (:result result)))
    (is (= eof (:remainder result))))
  (let [result (run (p* (rs/produce-seq (range 0 11))
                        (rs/filter* #(= 0 (mod % 2))))
                    (rs/take 5))]
    (is (= [0 2 4 6 8] (:result result)))
    (is (= [] (:remainder result)))))

(deftest drop-while*-test
  (let [result (run (p* (rs/produce-seq 6 (range 1 20))
                        (rs/drop-while* not-fizzbuzz))
                    rs/first)]
    (is (= 15 (:result result)))
    (is [16 17 18] (:remainder result)))
  (let [result (run (rs/produce-seq 6 (range 1 20))
                    (*c (rs/drop-while* not-fizzbuzz)
                        rs/first))]
    (is (= 15 (:result result)))
    (is [16 17 18] (:remainder result))))


(deftest isolate*-test
  (let [result (run (rs/produce-seq 7 (range 1 10000))
                    (*c (rs/isolate* 5)
                        rs/consume))]
    (is (= (range 1 6) (:result result)))
    (is (= [6 7] (:remainder result))))
  (let [result (run (p* (rs/produce-seq 7 (range 1 10000))
                        (rs/isolate* 5))
                    rs/consume)]
    (is (= (range 1 6) (:result result)))
    (is (= eof (:remainder result)))))

(deftest isolate*-with-less-than-needed
  (let [result (run (p* (rs/produce-seq 1 (range 1 4))
                        (rs/isolate* 5))
                     rs/consume)]
    (is (= [1 2 3] (:result result)))
    (is (= eof (:remainder result))))
  (let [result (run (rs/produce-seq 1 (range 1 4))
                    (*c (rs/isolate* 5)
                        rs/consume))]
    (is (= [1 2 3] (:result result)))
    (is (= eof (:remainder result)))))

(deftest require*-test
    (is (thrown-with-msg? Exception #"require*"
                 (run (p* (rs/produce-seq 2 (range 1 8))
                          (rs/require* 8))
                      rs/consume)))

    (is (thrown-with-msg? Exception #"require*"
                 (run (rs/produce-seq 2 (range 1 8))
                      (*c (rs/require* 8)
                          rs/consume)))))

(deftest require*-with-more-than-needed-test
  (let [result (run (p* (rs/produce-seq (range 1 8))
                        (rs/require* 1))
                    rs/consume)]
    (is (yield? result))
    (is (= [1 2 3 4 5 6 7] (:result result))))
  (let [result (run (rs/produce-seq (range 1 8))
                    (*c (rs/require* 1)
                         rs/consume))]
    (is (yield? result))
    (is (= [1 2 3 4 5 6 7] (:result result)))))

(deftest stream-while*-test
  (let [result (run (p* (rs/produce-seq 10 (range 1 20))
                        (rs/stream-while* not-fizzbuzz))
                    rs/consume)]
    (is (= (range 1 15) (:result result)))
    (is (range 15 20) (:remainder result)))
  (let [result (run (rs/produce-seq 10 (range 1 20))
                    (*c (rs/stream-while* not-fizzbuzz)
                         rs/consume))]
    (is (= (range 1 15) (:result result)))
    (is (range 15 20) (:remainder result))))


(deftest split-when*-test
  (let [result (run (p* (rs/produce-seq 10 (range 1 12))
                        (rs/split-when* #(= 0 (mod % 3))))
                    rs/consume)]
    (is (= [[1 2 3] [4 5 6] [7 8 9] [10 11]] (:result result)))
    (is eof (:remainder result)))
  (let [result (run (rs/produce-seq 10 (range 1 12))
                    (*c (rs/split-when* #(= 0 (mod % 3)))
                        rs/consume))]
    (is (= [[1 2 3] [4 5 6] [7 8 9] [10 11]] (:result result)))
    (is eof (:remainder result))))

