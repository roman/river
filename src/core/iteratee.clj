(ns core.iteratee
  (:use core.iteratee.types
        core.iteratee.enumerators
        core.iteratee.iteratees
        core.iteratee.enumeratees)
  (:require [core.iteratee.process :as itp]))

(def run* enum-eof)

; testing

;(println
;  (run*
;    (itp/enum-proc-lines "ls -l"
;      (to-enumeratee print-chunks #(consume [] %)))))

(println
  (run*
    (itp/enum-proc-lines "ls -l" #(consume [] %))))


;(println
;    (run* (enum-seq 5 (range 1 10) #(consume [] %))))

;(println
;  (run*
;    (enum-seq 5 (range 1 10)
;      (map* #(+ % 1) #(consume [] %)))))

;(println
;  (run*
;    (enum-seq 5 (range 1 10)
;      (filter* #(< % 5) #(consume [] %)))))

;(println
;  (run*
;    (enum-seq 5 (range 1 10)
;      (zip* #(consume [] %)))))

;(println
;  (run*
;    (enum-seq 5 (range 1 10)
;      (zip* #(consume [] %) 
;            (map* #(+ % 1) #(consume [] %))))))

;(println
;  (run*
;    (itp/enum-proc-lines "ls -l"
;      (zip* consume-in-vector 
;            (map* #(.toUpperCase %) consume-in-list)))))


