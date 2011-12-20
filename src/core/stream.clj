(ns core.stream
  (:use core.stream.types
        core.stream.producers
        core.stream.consumers
        core.stream.filters)
  (:require [core.stream.process :as sp]))

(def run* produce-eof)

; testing

(println
  (run*
    (sp/produce-proc-lines "ls -l"
      (to-filter print-chunks consume))))

(println
  (run*
    ((attach-filter
      (partial sp/produce-proc-lines "ls -l")
      (partial to-filter print-chunks))
      consume)))

(println
  (run*
    (sp/produce-proc-lines "ls -l" consume)))

(println
  (run*
    (sp/produce-proc-bytes "ls -l" consume)))

(println
    (run* (produce-seq 5 (range 1 10) consume)))

(println
  (run*
    (produce-seq 5 (range 1 10)
      (map* #(+ % 1) consume))))

(println
  (run*
    (produce-seq 5 (range 1 10)
      (filter* #(< % 5) consume))))

(println
  (run*
    (produce-seq 5 (range 1 10)
      (zip* consume))))

(println
  (run*
    (produce-seq 5 (range 1 10)
      (zip* consume
            (map* #(+ % 1) consume)))))

(println
  (run*
    (sp/produce-proc-lines "ls -l"
      (zip* consume
            (map* #(.toUpperCase %) consume-in-list)))))


