(ns river.test.io
  (:use [clojure.test])

  (:use [river.core])
  (:require [river.io :as sio]
            [river.seq :as sseq]))

(deftest produce-proc-lines-test
  (let [result (run* (sio/produce-proc-lines "pwd")
                     sseq/first)]
    ; The name of the project (where the lein test is being executed)
    (is (re-find #"clj-stream" (:result result)))))
