(ns core.iteratee.iteratees
  (:use core.iteratee.types))

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


(defn consume [empty-seq stream0]
  (letfn [
    (go [acc stream]
      (let [new-acc (conj acc stream)]
      (cond
        (eof? stream) (yield (concat acc) nil)
        :else
          (continue #(go new-acc %)))))
  ]
  (go empty-seq stream0)))


(def consume-in-set #(consume #{} %))
(def consume-in-vector #(consume [] %))
(def consume-in-list #(consume () %))

