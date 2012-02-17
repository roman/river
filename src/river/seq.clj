(ns river.seq

  ^{
    :author "Roman Gonzalez"
  }

  (:refer-clojure :exclude
    [take take-while drop drop-while reduce first peek])

  (:require [clojure.core :as core])

  (:use river.core))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Utility Functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- span [pred xs]
  ((core/juxt #(core/take-while pred %) #(core/drop-while pred %)) xs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn take
  "Returns a seq of the first n elements in the stream, or all items if
  there are fewer than n."
  ([n-elems]
    (take [] n-elems))
  ([buffer0 n-elems0]
    (letfn [
      (consumer [buffer n-elems stream]
        (cond
          (eof? stream) (yield buffer eof)
          (empty-chunk? stream) (continue #((take buffer n-elems) %))
          :else
            (let [taken-elems (concat buffer (core/take n-elems stream))
                  new-size    (- n-elems (count stream))]
              (if (> new-size 0)
                (continue #(consumer taken-elems new-size %))
                (yield taken-elems (core/drop n-elems stream))))))
    ]
    #(consumer buffer0 n-elems0 %))))

(defn take-while
  "Returns a seq of successive items from the stream while (pred item)
  returns true."
  ([pred] (take-while [] pred))
  ([buffer0 pred]
    (letfn [
      (consumer [buffer stream]
        (cond
          (eof? stream) (yield buffer eof)
          (empty-chunk? stream) (continue #(consumer buffer %))
          :else
            (let [taken-elems (core/take-while pred stream)
                  remainder   (core/drop-while pred stream)
                  new-buffer  (concat buffer taken-elems)]
              (cond
                (empty? remainder)
                  (continue #(consumer new-buffer %))
                :else
                  (yield new-buffer remainder)))))
    ]
    #(consumer buffer0 %))))

(defn drop
  "Drops from the stream the first n elements."
  [n0]
  (letfn [
    (consumer [n stream]
      (cond
        (eof? stream) (yield nil stream)
        (empty-chunk? stream) (continue #(consumer n %))
        :else
          (let [new-n (- n (count stream))]
            (if (> new-n 0)
              (continue #(consumer new-n %))
              (yield nil (core/drop n stream))))))
  ]
  #(consumer n0 %)))

(defn drop-while
  "Drops elements from the stream until the first element that
  returns a falsy value on (pred item)."
  [pred]
  (fn consumer [stream]
    (cond
     (eof? stream) (yield nil eof)
     (empty-chunk? stream) (continue #(consumer %))
     :else
       (let [new-stream (core/drop-while pred stream)]
         (if (not (empty? new-stream))
           (yield nil new-stream)
           (continue #(consumer %)))))))

(defn consume
  "Consumes all the stream and returns it in a seq, when called
  empty-seq is supplied, it will serve as the initial buffer
  from where the stream is going to be stored."
  ([] (consume []))
  ([empty-seq]
    (take-while empty-seq (constantly true))))

(defn reduce
  "Consumes the stream item by item supplying each of them to the f function.
  f should receive two arguments, the accumulated result and the current
  element from the stream, if no zero is provided, then it will use the first
  element of the stream as the zero value for the accumulator."
  ([f]
    (fn consumer [stream]
      (cond
        (eof? stream) (yield nil eof)
        (empty-chunk? stream) (continue #(consumer %))
        :else
          ((reduce f (core/first stream)) (core/rest stream)))))
  ([f zero0]
    (letfn [
      (consumer [zero stream]
        (cond
          (eof? stream) (yield zero eof)
          (empty-chunk? stream) (continue #(consumer zero %))
          :else
            (let [new-zero (core/reduce f zero stream)]
              (continue #(consumer new-zero %)))))
    ]
    #(consumer zero0 %))))

(def first
  "Returns the first item in the stream, returns nil when stream has reached
  EOF."
  (fn consumer [stream]
    (cond
      (eof? stream) (yield nil eof)
      (empty-chunk? stream) (continue consumer)
      :else
        (yield (core/first stream) (core/rest stream)))))

(def peek
  "Returns the first item in the stream without actually removing it, returns
  nil when the stream has reached EOF."
  (fn consumer [stream]
    (cond
      (eof? stream) (yield nil eof)
      (empty-chunk? stream) (continue consumer)
      :else
        (yield (core/first stream) stream))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Producers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn produce-seq
  "Produces a stream from a seq, and feeds it to the given consumer,
  when chunk-size is given the seq will be streamed every chunk-size
  elements, it will stream 8 items per chunk by default when not given."
  ([a-seq consumer] (produce-seq 8 a-seq consumer))
  ([chunk-size a-seq consumer]
    (let [[input remainder] (core/split-at chunk-size a-seq)
          next-consumer (consumer input)]
      (cond
        (yield? next-consumer) next-consumer
        (continue? next-consumer)
          (if (empty? remainder)
            next-consumer
            (recur chunk-size remainder next-consumer))))))

(defn produce-iterate
  "Produces an infinite stream by applying the f function on the zero value
  indefinitely. Each chunk is going to have chunk-size items, 8 by default."
  ([f zero consumer]
    (produce-iterate 8 f zero consumer))
  ([chunk-size f zero consumer]
    (produce-seq chunk-size (core/iterate f zero) consumer)))

(defn produce-repeat
  "Produces an infinite stream that will have the value elem indefinitely.
  Each chunk is going to have chunk-size items, 8 by default."
  ([elem consumer] (produce-repeat 8 elem consumer))
  ([chunk-size elem consumer]
    (produce-seq chunk-size (core/repeat elem) consumer)))

(defn produce-replicate
  "Produces a stream that will have the elem value n times. Each chunk is
  going to have chunk-size items, 8 by default."
  ([n elem consumer] (produce-replicate 8 n elem consumer))
  ([chunk-size n elem consumer]
    (produce-seq chunk-size (core/replicate n elem) consumer)))

(defn produce-generate
  "Produces a stream with the f function, f will likely have side effects
  because it will return a new value each time. When the f function returns
  a falsy value, the function will stop producing values to the stream."
  [f consumer]
  (if-let [result (f)]
    (if (continue? consumer)
      (recur f (consumer [result]))
      consumer)
    consumer))

(defn- unfold [f zero]
  (if-let [whole-result (f zero)]
    (let [[result new-zero] whole-result]
      (cons result (core/lazy-seq (unfold f new-zero))))
    []))

(defn produce-unfold
  "Produces a stream with the f function, f will be a function that receive
  an initial zero value, and it will return a tuple with the next value and
  a new zero, the value returned will be fed to the consumer. The stream will
  stop when the f function returns a falsy value. Each chunk is going to have
  chunk-size items, 8 by default."
  ([f zero consumer] (produce-unfold 8 f zero consumer))
  ([chunk-size f zero consumer]
    (produce-seq chunk-size (unfold f zero) consumer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Filters
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn mapcat*
  "Transform the stream by applying function f to each element in the stream.
  f will be a function that receives an item and will return a seq, the
  resulting seqs will be later concatenated and be feeded to the given
  consumer."
  [f]
  (letfn [
    (feed-loop [inner-consumer0 [x & xs :as stream]]
      (cond
        (empty? stream)
          (continue #(feed-inner-consumer inner-consumer0 %))
        :else
          (let [inner-consumer (inner-consumer0 (f x))]
            (cond
              (continue? inner-consumer) (recur inner-consumer xs)
              (yield? inner-consumer) (yield inner-consumer xs)))))
    (feed-inner-consumer [inner-consumer stream]
      (cond
        (eof? stream)
          (yield (continue inner-consumer) stream)
        :else
          (feed-loop inner-consumer stream)))
    ]
    (fn to-outer-consumer [inner-consumer]
      #(feed-inner-consumer inner-consumer %))))

(defn map*
  "Transform the stream by applying function f to each element in the stream.
  f will be a function that receives an item and return another of (possibly)
  a different type, this items will be feeded to the consumer."
  [f]
  (fn to-outer-consumer [inner-consumer]
    ((mapcat* (comp vector f)) inner-consumer)))

(defn filter*
  "Removes elements from the stream by using the function pred. pred will
  receive an element from the stream and will return a boolean indicating if
  the element should be kept in the stream or not. The consumer will be
  feed with the elements of the stream in which pred returns true."
  [pred]
  (fn to-outer-consumer [inner-consumer]
    ((mapcat* (comp #(core/filter pred %) vector)) inner-consumer)))

(defn zip*
  "Multiplexes the stream into multiple consumers, each of the consumers
  will be feed by the stream that this filter receives, this will return
  a list of consumer results/continuations."
  [& inner-consumers]
  (fn outer-consumer [stream]
    (cond
      (eof? stream)
        (for [c inner-consumers] (produce-eof c))
      :else
        (apply zip* (for [c inner-consumers] (ensure-done c stream))))))

(defn ensure-inner-done [f consumer]
  (fn [stream]
    (cond
      (yield? consumer) (yield consumer stream)
      :else (f consumer stream))))

(defn drop-while*
  "Works similarly to the drop-while consumer, it will drop elements from
  the stream until pred holds false, at that point the given inner-consumer
  will be feed with the receiving stream."
  [pred]
  (letfn [
    (feed-inner-consumer [inner-consumer stream]
      (cond
        (empty? stream)
          (continue #(feed-inner-consumer inner-consumer %))
        (eof? stream)
          (yield inner-consumer eof)
        :else
          (let [new-stream (core/drop-while pred stream)]
            (if (not (empty? new-stream))
              (yield (inner-consumer new-stream) [])
              (continue #(feed-inner-consumer inner-consumer %))))))]
  (fn to-outer-consumer [consumer]
    (ensure-inner-done feed-inner-consumer consumer))))

(defn isolate*
  "Prevents the consumer from receiving more stream than the specified in
  n, as soon as n elements had been feed, the filter will feed an EOF to
  the inner-consumer."
  [n]
  (letfn [
    (feed-inner-consumer [total-count inner-consumer stream]
      (cond
      (eof? stream)
        (yield inner-consumer eof)
      (empty? stream)
        (continue #(feed-inner-consumer total-count
                                        inner-consumer
                                        %))
      :else
        (let [stream-count (count stream)]
          (if (> stream-count total-count)
            (yield (inner-consumer
                     (core/take total-count stream))
                   (core/drop total-count stream))
            (continue
              (ensure-inner-done (partial feed-inner-consumer
                                          (- total-count stream-count))
                                 (inner-consumer stream)))))))]
  (fn to-outer-consumer [consumer]
    (ensure-inner-done (partial feed-inner-consumer n)
                       consumer))))

(defn require*
  "Throws an exception if there is not at least n elements streamed to
  the inner-consumer."
  [n]
  (letfn [
    (feed-inner-consumer [total-count inner-consumer stream]
      (cond
        (eof? stream)
          (if (> total-count 0)
            (throw (Exception. "require*: minimum count wasn't satisifed"))
            (yield inner-consumer eof))
        (empty? stream)
          (continue #(feed-inner-consumer total-count
                                          inner-consumer
                                          %))
        :else
          (if (<= total-count 0)
            (yield (inner-consumer stream) [])
            (continue
              (ensure-inner-done (partial feed-inner-consumer
                                          (- total-count (count stream)))
                                 (inner-consumer stream))))))]
  (fn to-outer-consumer [inner-consumer]
    (ensure-inner-done (partial feed-inner-consumer n)
                       inner-consumer))))

(defn stream-while*
  "Streams elements to the inner-consumer until the f function returns a falsy
  value for a given item."
  [f]
  (letfn [
    (outer-consumer [inner-consumer stream]
      (cond

      (eof? stream)
      (yield inner-consumer stream)

      (empty? stream)
      (continue #(outer-consumer inner-consumer %))

      :else
      (let [[to-feed to-drop] (span f stream)]
        (if (empty? to-drop)
          (continue (ensure-inner-done outer-consumer
                                       (inner-consumer to-feed)))
          (yield (inner-consumer to-feed) to-drop)))))]

  (fn to-outer-consumer [inner-consumer]
    (ensure-inner-done outer-consumer inner-consumer))))

(defn- split-when-consumer [f]
  (do-consumer
    [first-chunks (take-while (complement f))
     last-chunk   (take 1)]
     (if (nil? last-chunk)
       first-chunks
       (concat first-chunks last-chunk))))

(defn split-when* [f inner-consumer]
  "Splits on elements satisfiying the given f function, the inner-consumer
  will receive chunks of collections from the stream."
  (to-filter (split-when-consumer f) inner-consumer))

