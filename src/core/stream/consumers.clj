(ns core.stream.consumers
  (:refer-clojure :exclude [reduce take take-while])
  (:require [clojure.core :as core])
  (:use core.stream.types))

(defn take
  ([n-elems stream]
    (take [] n-elems stream))
  ([buffer n-elems stream]
    (cond
      (empty-chunk? stream) (continue #(take buffer n-elems %))
      (eof? stream) (yield buffer eof)
      :else
        (let [taken-elems (concat buffer (core/take n-elems stream))
              new-size    (- n-elems (count stream))]
          (if (> new-size 0)
            (continue #(take taken-elems new-size %))
            (yield taken-elems (drop n-elems stream)))))))

(defn take-while
  ([a-fn stream] (take-while [] a-fn stream))
  ([buffer a-fn stream]
    (cond
      (eof? stream) (yield buffer eof)
      (empty-chunk? stream) (continue #(take-while buffer a-fn %))
      :else
        (let [taken-elems (core/take-while a-fn stream)
              remainder   (core/drop-while a-fn stream)
              new-buffer  (concat buffer taken-elems)]
          (cond
            (empty? remainder)
              (continue #(take-while new-buffer a-fn %))
            :else
              (yield new-buffer remainder))))))

(defn consume
  ([stream0] (consume [] stream0))
  ([empty-seq stream0]
    (take-while empty-seq (constantly true) stream0)))

(def consume-in-set #(consume #{} %))
(def consume-in-list #(consume () %))

(defn reduce
  ([a-fn stream]
    (cond
      (empty-chunk? stream) (continue (partial reduce a-fn))
      (eof? stream) (yield nil eof)
      :else
        (reduce a-fn (first stream) (rest stream))))
  ([a-fn zero stream]
    (cond
      (eof? stream)
        (yield zero eof)
      (empty-chunk? stream)
        (continue #(reduce a-fn zero %))
      :else
        (let [new-zero (core/reduce a-fn zero stream)]
          (continue #(reduce a-fn new-zero %))))))

(defn head [stream]
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue head)
    :else
      (yield (first stream) (rest stream))))


(defn print-chunks [stream]
  (cond
    (eof? stream)
      (yield nil eof)

    (empty-chunk? stream)
      (continue print-chunks)

    :else
      (do
        (println stream)
        (continue print-chunks))))

