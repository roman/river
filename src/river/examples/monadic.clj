(ns river.examples.monadic
  (:use [river.core])
  (:require [river.seq :as rs]
            [river.io  :as rio]))

(def drop-and-head
  (do-consumer
  ; ^ allows you to create a new consumer using
  ; a monadic notation as in clojure.algo.monads
    [_ (rs/drop-while #(< % 5))
       ; ^ drops from the stream until the condition is met
     b rs/first]
       ; ^ gets the first element after the dropping is done
    b))

(defn -main []
  (println (run
           ; ^ function to execute producers/consumers
                (rs/produce-seq (range -20 20))
                ; ^ produce a stream of numbers from a seq
                drop-and-head)))
                ; drops until < 5 and then gives the first element found
                ; (in this case 6)
