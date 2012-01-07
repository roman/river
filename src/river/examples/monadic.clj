(ns river.examples.monadic
  (:use [river.core]
  (:require [river.seq :as rs]
            [river.io :as  rio]))

(def drop-and-head
  (do-consumer
    [_ (rs/drop-while #(< % 5))
     b rs/first]
    b))

(println (run* (rs/produce-seq (range -20 20))
               drop-and-head))
