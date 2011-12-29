(ns core.stream.test.io
  (:use [clojure.test])

  (:use [core.stream])
  (:require [core.stream.io :as sio]
            [core.stream.seq :as sseq]))

(deftest produce-proc-lines-test
  (let [result (run* (sio/produce-proc-lines "pwd")
                     sseq/first)]
    ; The name of the project (where the lein test is being executed)
    (is (re-find #"clj-stream" (:result result)))))
