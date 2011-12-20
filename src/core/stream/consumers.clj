(ns core.stream.consumers
  (:use core.stream.types))

(defn print-chunks [stream]
  (cond
    (eof? stream)
      (yield nil nil)

    (empty-chunk? stream)
      (continue print-chunks)

    :else
      (do
        (println stream)
        (continue print-chunks))))


(defn- consume-helper [acc stream]
  (let [new-acc (conj acc stream)]
    (cond
      (eof? stream) (yield (concat acc) nil)
      :else
        (continue #(consume-helper new-acc %)))))

(defn consume
  ([stream0] (consume [] stream0))
  ([empty-seq stream0]
    (consume-helper empty-seq stream0)))

(def consume-in-set #(consume #{} %))
(def consume-in-list #(consume () %))

