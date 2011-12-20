(ns core.iteratee.enumeratees
  (:use core.iteratee.types
        [core.iteratee.enumerators :only
          [produce-eof]]))

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
        (for [c inner-consumers] (produce-eof c))
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
              (produce-eof (inner-consumer result)))))))

(defn- gen-isolate-fn [chunk-count total-chunks inner-consumer]
  (fn outer-consumer [stream]
    (if (>= chunk-count total-chunks)
      (produce-eof inner-consumer)
      (gen-isolate-fn (inc chunk-count)
                      total-chunks
                      (inner-consumer stream)))))

(defn isolate* [total-chunks inner-consumer]
  (gen-isolate-fn 0 total-chunks inner-consumer))

(defn to-filter [consumer0 inner-consumer0]
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
                (to-filter
                  consumer0
                  (inner-consumer0 (:result next-consumer))))))))
  ]
  (cond 
    (yield? inner-consumer0) inner-consumer0
    :else (go consumer0))))
  

(defn attach-filter [producer a-filter]
  #(producer (a-filter %)))

