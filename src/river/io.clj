(ns river.io

  ^{
    :author "Roman Gonzalez"
    :doc "
    This namespace provides `producers` that will generate a stream out
    of a java `InputStream` or a `Reader`, it also offers consumers to write
    a stream of bytes to a java `OutputStream` and a stream of characters
    to a `Writer`."
  }

  (:require [clojure.java.io :as io])
  (:import [java.io LineNumberReader
                    FileInputStream])

  (:use river.core)
  (:use [river.seq :only
          [produce-generate]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Producers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- io-default-options [args]
  (->> (cond
         (map? (first args)) args
         :else (apply hash-map args))
       (merge {:buffer-size 1024
               :encoding "UTF-8"})))

(defn produce-reader-chars
  "Produces characters from an input (uses clojure.java.io/reader
  internally), and streams it to the given consumer. Possible `opts` are:

  * `:buffer-size`: the size of each chunk (defaults to 1024 characters).
  * `clojure.java.io/reader` options.

  Example:

    ; returns a yield with a seq of characters
    (run (produce-reader-chars \"filename.txt\") consume)
  "
  ([input & opts]
    (let [{:keys [buffer-size encoding]} (io-default-options opts)]
      (fn producer [consumer0]
        (io!
          (let [reader (io/reader input :encoding encoding)
                buffer (char-array buffer-size)]
          (loop [consumer consumer0]
            (let [n-chars (.read reader buffer)]
            (if (and (>= n-chars 0) (continue? consumer))
              (recur (consumer (take n-chars (vec buffer))))
              consumer)))))))))

(defn produce-reader-lines
  "Produces lines from an `input` element (uses `clojure.java.io/reader`
  internally), and streams it to the given consumer. Possible `opts` are:

  * `clojure.java.io/reader` options.

  Example:

    ; returns a yield with a seq of lines
    (run (produce-reader-lines \"filename.txt\") consume)
  "
  ([input & opts]
    (io!
      (let [{:keys [encoding]} (io-default-options opts)
            reader (LineNumberReader. (io/reader input
                                                 :encoding encoding))]
      (produce-generate #(.readLine reader))))))

(defn produce-input-stream-bytes
  "Produces bytes from a given `input` element (uses
  `clojure.java.io/input-stream` interally), and streams it to the
  given consumer. Possible `opts` are:

  * `:buffer-size`: the size of each chunk (defaults to 1024 bytes).

  Example:

    ; returns a yield with a seq of bytes
    (run (produce-reader-bytes \"filename.txt\") consume)
  "
  ([input & opts]
    (let [{:keys [buffer-size encoding]} (io-default-options opts)]
    (fn [consumer0]
      (io!
       (let [input-stream (io/input-stream input :encoding encoding)
             buffer       (byte-array buffer-size)]
         (loop [consumer consumer0]
           (let [n-bytes (.read input-stream buffer)]
             (if (and (>= n-bytes 0) (continue? consumer))
               (recur (consumer (take n-bytes (vec buffer))))
               consumer)))))))))

(defn produce-proc-bytes
  "Produces bytes from an OS process executing the `cmd` command, it uses
  `produce-input-stream-bytes` internally.

  Example:

    ; returns a yield with a seq of bytes
    (run (produce-proc-bytes \"ls -l\" :chunk-size 20) consume)
  "
  ([^String cmd & opts]
    (fn producer [consumer]
      (with-open [input-stream (-> (Runtime/getRuntime)
                                   (.exec cmd)
                                   (.getInputStream))]
        ((apply produce-input-stream-bytes input-stream opts) consumer)))))

(defn produce-proc-chars
  "Produces chars from an OS process executing the `cmd` command, it uses
  `produce-input-stream-chars` internally.

  Example:

    ; returns a yield with a seq of characters
    (run (produce-proc-chars \"ls -l\" :chunk-size 20) consume)
  "
  ([^String cmd & opts]
    (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((apply produce-reader-chars input-stream opts) consumer)))))

(defn produce-proc-lines
  "Produces lines from an OS process executing the `cmd` command, it uses
  `produce-input-stream-lines` internally.

  Example:

    ; returns a yield with a seq of lines
    (run (produce-proc-lines \"ls -l\" :chunk-size 20) consume)
  "
  [cmd & opts]
  (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((apply produce-reader-lines input-stream opts) consumer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn write-bytes-to-output-stream
  "Consumes a stream of bytes and writes over to the given `output-stream`.

  **WARNING**: The caller has to close the `output-stream`, this consumer won't
  close it once the stream is finished."
  [^java.io.OutputStream output-stream]
  (letfn [
    (consumer [os ^java.io.OutputStream stream]
      (cond
      (eof? stream)
      (yield nil stream)
      (empty-chunk? stream)
      (continue #(consumer os %))

      :else
      (do
        (.write os (byte-array stream))
        (continue #(consumer os %)))))]
  (continue #(consumer output-stream %))))

(defn write-chars-to-writer
  "Consumes a stream of characters and writes over to the given `writer`.

  **WARNING**: The caller has to close the `writer`, this consumer won't
  close it once the stream is finished."
  [^java.io.Writer writer]
  (letfn [
    (consumer [wr stream]
      (cond
      (eof? stream)
      (yield nil stream)

      (empty? stream)
      (continue #(consumer wr %))

      :else
      (do
        (.write wr (char-array stream))
        (continue #(consumer wr %)))))]
  (continue #(consumer writer %))))


