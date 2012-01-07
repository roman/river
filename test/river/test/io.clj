(ns river.test.io
  (:use [clojure.test])

  (:use [river.core])
  (:require [river.io :as rio]
            [river.seq :as rs]))

(deftest produce-proc-lines-test
  (let [result (run* (rio/produce-proc-lines "pwd")
                     rs/first)]
    ; The name of the project (where the lein test is being executed)
    (is (re-find #"river" (:result result)))))
