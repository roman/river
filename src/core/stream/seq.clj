(ns core.stream.seq

;;;;;;;;;;;;;;;;;;;;

  (:refer-clojure :exclude
    [take take-while drop drop-while reduce first peek])

  (:require [clojure.core :as core])
  (:require [clojure.algo.monads :as monad])

;;;;;;;;;;;;;;;;;;;;

  (:use core.stream))

;; Consumers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
            (yield taken-elems (core/drop n-elems stream)))))))

(defn take-while
  ([f stream] (take-while [] f stream))
  ([buffer f stream]
    (cond
      (eof? stream) (yield buffer eof)
      (empty-chunk? stream) (continue #(take-while buffer f %))
      :else
        (let [taken-elems (core/take-while f stream)
              remainder   (core/drop-while f stream)
              new-buffer  (concat buffer taken-elems)]
          (cond
            (empty? remainder)
              (continue #(take-while new-buffer f %))
            :else
              (yield new-buffer remainder))))))

(defn drop [n stream]
  (cond
    (eof? stream) (yield nil stream)
    :else
      (let [new-n (- n (count stream))]
        (if (> new-n 0)
          (continue #(drop new-n %))
          (yield nil (core/drop n stream))))))

(defn drop-while [f stream]
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue #(drop-while f %))
    :else
      (let [new-stream (core/drop-while f stream)]
        (if (not (empty? new-stream))
          (yield nil new-stream)
          (continue #(drop-while f %))))))

(defn consume
  ([stream0] (consume [] stream0))
  ([empty-seq stream0]
    (take-while empty-seq (constantly true) stream0)))

(defn reduce
  ([f stream]
    (cond
      (empty-chunk? stream) (continue (partial reduce f))
      (eof? stream) (yield nil eof)
      :else
        (reduce f (core/first stream) (core/rest stream))))
  ([f zero stream]
    (cond
      (eof? stream)
        (yield zero eof)
      (empty-chunk? stream)
        (continue #(reduce f zero %))
      :else
        (let [new-zero (core/reduce f zero stream)]
          (continue #(reduce f new-zero %))))))

(defn first [stream]
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue first)
    :else
      (yield (core/first stream) (core/rest stream))))

(defn peek [stream]
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue peek)
    :else
      (yield (core/peek stream) stream)))

;; Producers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn produce-seq [chunk-size a-seq consumer]
  (let [[input remainder] (core/split-at chunk-size a-seq)
        next-consumer (consumer input)]
    (cond
      (yield? next-consumer) next-consumer
      (continue? next-consumer)
        (if (empty? remainder)
          next-consumer
          (recur chunk-size remainder next-consumer)))))

(defn produce-iterate [f zero consumer]
  (cond 
    (yield? consumer) consumer
    :else
    (recur f (f zero) (consumer [zero]))))

(defn produce-repeat [elem consumer]
  (cond
    (yield? consumer) consumer
    :else
      (recur elem (consumer [elem]))))

(defn produce-replicate [times elem consumer]
  (if (or (= times 0) (yield? consumer))
    consumer
    (recur (dec times) elem (consumer [elem]))))

(defn produce-generate [f consumer]
  (if-let [result (f)]
    (if (yield? consumer)
      consumer 
      (recur f (consumer [result])))
    consumer))

(defn produce-unfold [f zero consumer]
  (if-let [whole-result (f zero)]
    (if (yield? consumer)
      consumer
      (let [[new-zero result] whole-result] 
        (recur f new-zero (consumer result))))))

;; Filters  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mapcat* [map-fn inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream)
        (inner-consumer eof)
      :else
        (mapcat* map-fn (inner-consumer (mapcat map-fn stream))))))

(defn map* [map-fn inner-consumer]
  (mapcat* (comp vector map-fn) inner-consumer))

(defn filter* [filter-fn inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream) (inner-consumer eof)
      :else
        (filter* filter-fn (inner-consumer (filter filter-fn stream))))))

(defn zip* [& inner-consumers]
  (fn outer-consumer [stream]
    (cond
      (eof? stream)
        (for [c inner-consumers] (produce-eof c))
      :else
        (apply zip* (for [c inner-consumers] (ensure-done c stream))))))

(defn drop-while* [f inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream) (inner-consumer eof)
      :else
        (let [result (core/drop-while f stream)]
          (if (-> result empty? not)
            (inner-consumer result)
            (drop-while* f inner-consumer))))))

(defn isolate* [total-chunks inner-consumer]
  (fn outer-consumer [stream]
    (let [stream-count (count stream)]
      (if (> stream-count total-chunks)
        (produce-eof (inner-consumer (core/take total-chunks stream)))
        (isolate* (- total-chunks stream-count) (inner-consumer stream))))))

(defn require* [total-chunks inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (and (eof? stream)
           (> total-chunks 0))
        (throw (Exception. "ERROR: require* wasn't satisfied"))

      (<= total-chunks 0)
        (inner-consumer stream)

      :else
        (let [new-total-chunks (- total-chunks (count stream))]
          (require* new-total-chunks (inner-consumer stream))))))

(defn stream-while* [f inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream) (inner-consumer eof)
      :else
        (let [result (core/take-while f stream)]
          (if (= result stream)
              (stream-while* f (inner-consumer result))
              (produce-eof (inner-consumer result)))))))

(defn- split-when-consumer [f]
  (monad/domonad stream-m
    [first-chunks #(take-while (comp not f) %)
     last-chunk   first]
     (if (nil? last-chunk)
       [first-chunks]
       [(concat first-chunks [last-chunk])])))

(defn split-when* [f inner-consumer]
  (to-filter (split-when-consumer f) inner-consumer))

