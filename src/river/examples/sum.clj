(ns river.examples.sum

  (require [clojure.string :as string])

  (use [core.stream])
  (require [core.stream.seq :as sseq]
           [core.stream.io  :as sio]))

(def words*   (partial sseq/mapcat* #(string/split % #"\s+")))

(def numbers* (partial sseq/map* #(Integer/parseInt %)))

(defn produce-numbers-from-file
  ([consumer] (produce-numbers-from-file "input.in" consumer))
  ([file-path consumer]
  ; running producers and filters without the run* macro
  ; decorating each filter.
    (sio/produce-file-lines file-path
      (words*
        (numbers* consumer)))))

(println (run* ; producing input from a file
           produce-numbers-from-file
           ; producing input from a seq
           (sseq/produce-seq (range 1 10))
           ; consuming numbers from both input sources
           (sseq/reduce + 0)))
