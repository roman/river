(ns core.stream.io
  (:import [java.io LineNumberReader 
                    InputStreamReader
                    FileInputStream])
  (:use core.stream)
  (:use [core.stream.seq :only
          [produce-generate]]))

(defn produce-input-stream-bytes
  ([input-stream consumer]
    (produce-input-stream-bytes 1024 input-stream consumer))

  ([buffer-size input-stream consumer0]
    (let [buffer (byte-array buffer-size)]
      (loop [consumer consumer0]
        (let [n-bytes (.read input-stream buffer)]
          (if (and (>= n-bytes 0) (continue? consumer))
            (recur (consumer (take n-bytes (vec buffer))))
            consumer))))))

(defn produce-input-stream-lines [input-stream consumer]
  (let [reader (LineNumberReader.
                (InputStreamReader. input-stream))]
    (produce-generate #(.readLine reader) consumer)))

(defn produce-file-bytes [file-name consumer]
  (with-open [input-stream (FileInputStream. file-name)]
    (produce-input-stream-bytes input-stream consumer)))

(defn produce-file-lines [file-name consumer]
  (with-open [input-stream (FileInputStream. file-name)]
    (produce-input-stream-lines input-stream consumer)))

(defn produce-proc-bytes [cmd consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    (produce-input-stream-bytes input-stream consumer)))

(defn produce-proc-lines [cmd consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    (produce-input-stream-lines input-stream consumer)))

