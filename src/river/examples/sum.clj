(ns river.examples.sum

  (require [clojure.string :as string])

  (use [river.core])
  (require [river.seq :as rs]
           [river.io  :as rio]))

(def words* (rs/mapcat* #(string/split % #"\s+")))
; ^ a filter that applies a split to whatever it recieves
; in the stream, this is assuming the stream is of strings

(def numbers* (rs/map* #(Integer/parseInt %)))
; ^ a fitler that transforms each received item into an
; Integer using the parseInt function, this is assuming
; that the stream is of strings

(defn produce-numbers-from-file
  ([] (produce-numbers-from-file "input.in"))
  ([file-path]
    (p*
    ; ^ whe bind a producer with some filters
    ; using the p* function.
        (rio/produce-file-lines file-path)
        ; ^ produces a stream of lines from a file path
        words*
        ; ^ applies the words filter
        numbers*)))
        ; ^ applies the number stream

(defn -main []
  (println (run 
           ; ^ executes a group of producers/consumers
                (produce-numbers-from-file)
                ; ^ produce a stream of numbers from a file
                (rs/produce-seq (range 1 10))
                ; ^ produce a stream of numbers from a seq
                (rs/reduce + 0))))
                ; ^ sums up the numbers ignoring where they come from
