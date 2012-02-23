(ns river.io

  ^{
    :author "Roman Gonzalez"
  }

  (:require [clojure.java.io :as io])
  (:import [java.io LineNumberReader
                    FileInputStream])

  (:use river.core)
  (:use [river.seq :only
          [produce-generate]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Producers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- io-default-options [args]
  (->> (cond
         (map? (first args)) args
         :else (apply hash-map args))
       (merge {:buffer-size 1024
               :encoding "UTF-8"})))

(defn produce-reader-chars
  "Stream characters from an input (uses clojure.java.io/reader
  internally), and streams it to the given consumer. When
  buffer-size is given, each chunk will have a buffer-size number of
  chars (defaults to 1024 chars)."
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
  "Stream lines from an input element (uses clojure.java.io/reader
  internally), and streams it to the given consumer."
  ([input & opts]
    (io!
      (let [{:keys [encoding]} (io-default-options opts)
            reader (LineNumberReader. (io/reader input
                                                 :encoding encoding))]
      (produce-generate #(.readLine reader))))))

(defn produce-input-stream-bytes
  "Stream bytes from a given input, and streams it to the
  given consumer. When buffer-size is given, each chunk will have
  buffer-size number of bytes (defaults to 1024)."
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

(defn produce-file-bytes
  "Stream bytes from a file, specified by filename, uses
  produce-input-stream-bytes internally with a FileInputStream class."
  ([filename & opts]
   (fn producer [consumer]
     (with-open [input-stream (FileInputStream. filename)]
       ((apply produce-input-stream-bytes input-stream opts) consumer)))))

(defn produce-file-chars
  "Stream characters from a file, specified by filename, uses
  produce-input-stream-chars internally with a FileInputStream class."
  ([filename & opts]
    (fn producer [consumer]
      (with-open [input-stream (FileInputStream. filename)]
        ((apply produce-reader-chars input-stream opts) consumer)))))

(defn produce-file-lines
  "Stream lines from a file, specified by filename, uses
  produce-input-stream-lines internally with a FileInputStream class."
  [filename & opts]
  (fn producer [consumer]
    (with-open [input-stream (FileInputStream. filename)]
      ((apply produce-reader-lines input-stream opts) consumer))))

(defn produce-proc-bytes
  "Stream bytes from an OS process executing the cmd command, it uses
  produce-input-stream-bytes internally."
  ([cmd & opts]
    (fn producer [consumer]
      (with-open [input-stream (-> (Runtime/getRuntime)
                                   (.exec cmd)
                                   (.getInputStream))]
        ((apply produce-input-stream-bytes input-stream opts) consumer)))))

(defn produce-proc-chars
  "Stream chars from an OS process executing the cmd command, it uses
  produce-input-stream-chars internally."
  ([cmd & opts]
    (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((apply produce-reader-chars input-stream opts) consumer)))))

(defn produce-proc-lines
  "Stream lines from an OS process executing the cmd command, it uses
  produce-input-stream-lines internally."
  [cmd & opts]
  (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((apply produce-reader-lines input-stream opts) consumer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn write-bytes-to-output-stream
  "Receives an OutputStream jk"
  [^java.io.OutputStream output-stream]
  (letfn [
    (consumer [os stream]
      (cond
      (eof? stream)
      (do
        (.close os)
        (yield nil stream))

      (empty-chunk? stream)
      (continue #(consumer os %))

      :else
      (do
        (.write os (byte-array stream))
        (continue #(consumer os %)))))]
  (continue #(consumer output-stream %))))

(defn write-chars-to-writer
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


