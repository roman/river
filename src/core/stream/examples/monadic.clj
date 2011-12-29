(ns core.stream.examples.monadic
  (require [clojure.algo.monads :as monad])

  (use [core.stream])
  (require [core.stream.seq :as sseq]
           [core.stream.io  :as sio]))

(def drop-and-head
  (monad/domonad stream-m
    ; need a `partial` to apply the pred function
    [_ (partial sseq/drop-while #(< % 5))
     b sseq/first]
    b))

(println (run* (sseq/produce-seq (range -20 20))
          drop-and-head))
