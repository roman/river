(ns core.stream.producers
  (:use [core.stream.types :only
          [continue continue? yield yield? eof]])

  (:import [java.io LineNumberReader InputStreamReader]))

(defn produce-eof [consumer]
  (cond
    (yield? consumer) consumer
    (continue? consumer)
      (let [result (consumer eof)]
        (if (continue? result)
          (throw (Exception. "ERROR: Missbehaving consumer"))
          result))))

(defn produce-seq [chunk-size a-seq consumer]
  (let [[input remainder] (split-at chunk-size a-seq)
        next-consumer (consumer input)]
    (cond
      (yield? next-consumer) next-consumer
      (continue? next-consumer)
        (if (empty? remainder)
          next-consumer
          (produce-seq chunk-size remainder next-consumer)))))

(defn- gen-input-stream-bytes-producer [input-stream buffer-size]
  (let [buffer (byte-array buffer-size)]
    (fn producer [consumer]
      (let [n-bytes (.read input-stream buffer)]
        (if (>= n-bytes 0)
          (let [next-consumer (->> 
                                buffer
                                vec
                                (take n-bytes)
                                consumer)]
             (cond
               (continue? next-consumer)
                 (recur next-consumer)
               :else
                 next-consumer))
          consumer)))))

(defn produce-input-stream-bytes
  ([input-stream consumer0] 
    (produce-input-stream-bytes 512 input-stream consumer0))

  ([buffer-size input-stream consumer0]
    (let [buffer (byte-array buffer-size)]
      ((gen-input-stream-bytes-producer input-stream buffer-size) 
        consumer0))))

(defn- gen-input-stream-line-producer [input-stream]
  (let [line-reader (LineNumberReader. (InputStreamReader. input-stream))]
    (fn producer [consumer]
      (if-let [a-line (.readLine line-reader)]
        (let [next-consumer (consumer [a-line])]
        (cond
          (continue? next-consumer) (recur next-consumer)
          :else next-consumer))
        consumer))))


(defn produce-input-stream-lines
  [input-stream consumer0]
    ((gen-input-stream-line-producer input-stream) 
      consumer0))

