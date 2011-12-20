(ns core.iteratee
  (:use core.iteratee.types
        core.iteratee.enumerators
        core.iteratee.iteratees
        core.iteratee.enumeratees)
  (:require [core.iteratee.process :as itp]))

(def run* produce-eof)

; testing

(println
  (run*
    (itp/produce-proc-lines "ls -l"
      (to-filter print-chunks consume))))

(println
  (run*
    (itp/produce-proc-lines "ls -l" consume)))

(println
  (run*
    (itp/produce-proc-bytes "ls -l" consume)))

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
    (itp/produce-proc-lines "ls -l"
      (zip* consume
            (map* #(.toUpperCase %) consume-in-list)))))


