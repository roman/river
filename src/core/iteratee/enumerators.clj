(ns core.iteratee.enumerators
  (:use [core.iteratee.types :only
          [continue continue? yield yield? eof]])

  (:import [java.io LineNumberReader InputStreamReader]))

(defn enum-eof [consumer]
  (cond
    (yield? consumer) consumer
    (continue? consumer)
      (let [result (consumer eof)]
        (if (continue? result)
          (throw Exception "ERROR: Missbehaving consumer")
          result))))

(defn enum-seq [chunk-size a-seq consumer]
  (let [[input remainder] (split-at chunk-size a-seq)
        next-consumer (consumer input)]
    (cond
      (yield? next-consumer) next-consumer
      (continue? next-consumer)
        (if (empty? remainder)
          next-consumer
          (enum-seq chunk-size remainder next-consumer)))))

(defn enum-input-stream-bytes
  ; TODO: have 3 arity method for buffer size
  [input-stream consumer0]
  (let [buffer (byte-array 512)]
  ; TODO: replace with loop
  (letfn [
    (go [consumer]
      (let [n-bytes (.read input-stream buffer)]
        (if (>= n-bytes 0)
          (let [next-consumer (->> buffer
                              vec
                              (take n-bytes)
                              consumer)]
            (cond
              (continue? next-consumer)
                (recur next-consumer)
              :else
                next-consumer))
          consumer)))
    ]
    (go consumer0))))


(defn enum-input-stream-lines
  [input-stream consumer0]
  (let [line-reader (LineNumberReader.
                      (InputStreamReader. input-stream))]
  (letfn [
    (go [consumer]
      (if-let [a-line (.readLine line-reader)]
        (let [next-consumer (consumer [a-line])]
        (cond
          (continue? next-consumer) (recur next-consumer)
          :else next-consumer))
        consumer))
    ]
    (go consumer0))))

