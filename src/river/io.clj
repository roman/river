(ns river.io

  ^{
    :author "Roman Gonzalez"
  }

;; Standard Lib ;;;;

  (:require [clojure.java.io :as io])
  (:import [java.io LineNumberReader
                    FileInputStream])

;; Local Lib ;;;;;;;

  (:use river.core)
  (:use [river.seq :only
          [produce-generate]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Producers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn produce-reader-chars
  "Stream characters from an input (uses clojure.java.io/reader
  internally), and streams it to the given consumer. When
  buffer-size is given, each chunk will have a buffer-size number of
  chars (defaults to 1024 chars)."
  ([input]
    (produce-reader-chars 1024 input))
  ([buffer-size input]
    (fn producer [consumer0]
      (io!
        (let [reader (io/reader input)
              buffer (char-array buffer-size)]
        (loop [consumer consumer0]
          (let [n-chars (.read reader buffer)]
          (if (and (>= n-chars 0) (continue? consumer))
            (recur (consumer (take n-chars (vec buffer))))
            consumer))))))))

(defn produce-reader-lines
  "Stream lines from an input element (uses clojure.java.io/reader
  internally), and streams it to the given consumer."
  ([input]
    (io!
      (let [reader (LineNumberReader. (io/reader input))]
      (produce-generate #(.readLine reader))))))

(defn produce-input-stream-bytes
  "Stream bytes from a given input, and streams it to the
  given consumer. When buffer-size is given, each chunk will have
  buffer-size number of bytes (defaults to 1024)."
  ([input]
    (produce-input-stream-bytes 1024 input))

  ([buffer-size input]
    (fn [consumer0]
      (io!
       (let [input-stream (io/input-stream input)
             buffer       (byte-array buffer-size)]
         (loop [consumer consumer0]
           (let [n-bytes (.read input-stream buffer)]
             (if (and (>= n-bytes 0) (continue? consumer))
               (recur (consumer (take n-bytes (vec buffer))))
               consumer))))))))

(defn produce-file-bytes
  "Stream bytes from a file, specified by file-name, uses
  produce-input-stream-bytes internally with a FileInputStream class."
  [file-name]
  (fn producer [consumer]
    (with-open [input-stream (FileInputStream. file-name)]
      ((produce-input-stream-bytes input-stream) consumer))))

(defn produce-file-chars
  "Stream characters from a file, specified by file-name, uses
  produce-input-stream-chars internally with a FileInputStream class."
  [file-name]
  (fn producer [consumer]
    (with-open [input-stream (FileInputStream. file-name)]
      ((produce-reader-chars input-stream ) consumer))))

(defn produce-file-lines
  "Stream lines from a file, specified by file-name, uses
  produce-input-stream-lines internally with a FileInputStream class."
  [file-name]
  (fn producer [consumer]
    (with-open [input-stream (FileInputStream. file-name)]
      ((produce-reader-lines input-stream) consumer))))

(defn produce-proc-bytes
  "Stream bytes from an OS process executing the cmd command, it uses
  produce-input-stream-bytes internally."
  [cmd]
  (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((produce-input-stream-bytes input-stream) consumer))))

(defn produce-proc-chars
  "Stream chars from an OS process executing the cmd command, it uses
  produce-input-stream-chars internally."
  [cmd]
  (fn producer [consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    ((produce-reader-chars input-stream) consumer))))

(defn produce-proc-lines
  "Stream lines from an OS process executing the cmd command, it uses
  produce-input-stream-lines internally."
  [cmd]
  (fn producer [consumer]
    (with-open [input-stream (-> (Runtime/getRuntime)
                                 (.exec cmd)
                                 (.getInputStream))]
      ((produce-reader-lines input-stream ) consumer))))

