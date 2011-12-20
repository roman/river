(ns core.stream

  (:require [clojure.algo.monads :as m])

;;;;;;;;;;;;;;;;;;;;

  (:use core.stream.types
        core.stream.producers
        core.stream.consumers
        core.stream.filters)
  (:require [core.stream.process :as sp])
  (:import [core.stream.types ConsumerDone]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def run* produce-eof)


(m/defmonad stream-m
  [ m-result (fn [v] (ConsumerDone. v []))
    m-bind   (fn bind-fn [step gn]
               (cond
                 (and (yield? step)
                      (no-remainder? step))
                   (gn (:result step))

                 (and (yield? step)
                      (has-remainder? step))
                   (let [step-2 (gn (:result step))]
                     (cond
                       (continue? step-2)
                         (step-2 (:remainder step))
                       (yield? step-2)
                         (yield (:result step-2)
                                (:remainder step))))
                 (continue? step)
                   (comp #(bind-fn % gn) step)))

  ])

; testing

;(println
;  (run*
;    (sp/produce-proc-lines "ls -l"
;      (to-filter print-chunks consume))))

;(println
;  (run*
;    ((attach-filter
;      (partial sp/produce-proc-lines "ls -l")
;      (partial to-filter print-chunks))
;      consume)))

;(println
;  (run*
;    (sp/produce-proc-lines "ls -l" consume)))

;(println
;  (run*
;    (sp/produce-proc-bytes "ls -l" consume)))

;(println
;    (run* (produce-seq 5 (range 1 10) consume)))

;(println
;  (run*
;    (produce-seq 5 (range 1 10)
;      (map* #(+ % 1) consume))))

;(println
;  (run*
;    (produce-seq 5 (range 1 10)
;      (filter* #(< % 5) consume))))

;(println
;  (run*
;    (produce-seq 5 (range 1 10)
;      (zip* consume))))

;(println
;  (run*
;    (produce-seq 5 (range 1 10)
;      (zip* consume
;            (map* #(+ % 1) consume)))))

;(println
;  (run*
;    (sp/produce-proc-lines "ls -l"
;      (zip* consume
;            (map* #(.toUpperCase %) consume-in-list)))))

;(println
;  (run*
;    (produce-seq 5 (range 1 10)
;      (m/domonad stream-m [a head] a))))

;(def two-heads
;  (m/domonad stream-m
;    [a head
;     b head]
;    [a b]))
;
;(println
;  (run*
;    (produce-seq 5 (range 1 10) two-heads)))


