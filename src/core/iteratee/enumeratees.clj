(ns core.iteratee.enumeratees
  (:use core.iteratee.types
        [core.iteratee.enumerators :only
          [enum-eof]]))

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

(defn map* [map-fn inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream)
        (inner-consumer eof)
      :else
        (map* map-fn (inner-consumer (map map-fn stream))))))

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
        (for [c inner-consumers] (enum-eof c))
      :else
        (apply zip* (for [c inner-consumers] (ensure-done c stream))))))

(defn drop-while* [a-fn inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream) (inner-consumer eof)
      :else
        (let [result (drop-while a-fn stream)]
          (if (-> result empty? not)
            (inner-consumer result)
            (drop-while* a-fn inner-consumer))))))

(defn stream-while* [a-fn inner-consumer]
  (fn outer-consumer [stream]
    (cond
      (eof? stream) (inner-consumer eof)
      :else
        (let [result (take-while a-fn stream)]
          (if (= result stream)
              (stream-while* a-fn (inner-consumer result))
              (enum-eof (inner-consumer result)))))))

(defn- isolate-helper [chunk-count total-chunks inner-consumer]
  (fn outer-consumer [stream]
    (if (>= chunk-count total-chunks)
      (enum-eof inner-consumer)
      (isolate-helper (inc chunk-count)
                      total-chunks
                      (inner-consumer stream)))))

(defn isolate* [total-chunks inner-consumer]
  (isolate-helper 0 total-chunks inner-consumer))

(defn to-enumeratee [consumer0 inner-consumer0]
  (letfn [
    (go [consumer]
      (fn outer-consumer [stream]
        (cond
          (eof? stream) (inner-consumer0 eof)
          :else
            (let [next-consumer (consumer stream)]
            (cond
              (continue? next-consumer)
                (go next-consumer)
              :else
                (to-enumeratee
                  consumer0
                  (inner-consumer0 (:result next-consumer))))))))
  ]
  (cond 
    (yield? inner-consumer0) inner-consumer0
    :else (go consumer0))))
  

(defn to-enumarator [enumerator enumeratee]
  #(enumerator (enumeratee %)))

