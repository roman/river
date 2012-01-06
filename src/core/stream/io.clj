(ns core.stream.io

  ^{
    :author "Roman Gonzalez"
  }

;; Standard Lib ;;;;

  (:import [java.io LineNumberReader
                    BufferedReader
                    InputStreamReader
                    FileInputStream])

;; Local Lib ;;;;;;;

  (:use core.stream)
  (:use [core.stream.seq :only
          [produce-generate]]))

;; Producers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn produce-input-stream-bytes
  "Stream bytes from a given input-stream, and streams it to the
  given consumer. When buffer-size is given, each chunk will have
  buffer-size number of bytes (defaults to 1024)."
  ([input-stream consumer]
    (produce-input-stream-bytes 1024 input-stream consumer))

  ([buffer-size input-stream consumer0]
   (io!
    (let [buffer (byte-array buffer-size)]
      (loop [consumer consumer0]
        (let [n-bytes (.read input-stream buffer)]
          (if (and (>= n-bytes 0) (continue? consumer))
            (recur (consumer (take n-bytes (vec buffer))))
            consumer)))))))

(defn produce-input-stream-chars
  "Stream characters from a given input-stream, and streams it to the
  given consumer. When buffer-size is given, each chunk will have
  buffer-size number of characters (defaults to 1024)."
  ([input-stream consumer]
   (produce-input-stream-chars 1024 input-stream consumer))

  ([buffer-size input-stream consumer0]
    (io!
      (let [reader (BufferedReader. 
                     (InputStreamReader. input-stream))
            buffer (char-array buffer-size)]
        (loop [consumer consumer0]
          (let [n-chars (.read reader buffer)]
            (if (and (>= n-chars 0) (continue? consumer))
              (recur (consumer (take n-chars (vec buffer))))
              consumer)))))))

(defn produce-input-stream-lines
  "Stream lines from a given input-stream, and streams it to the
  given consumer. The stream will stop as soon as the
  input-stream returns EOF."
  [input-stream consumer]
  (let [reader (LineNumberReader.
                (InputStreamReader. input-stream))]
    (io!
      (produce-generate #(.readLine reader) consumer))))

(defn produce-file-bytes
  "Stream bytes from a file, specified by file-name, uses
  produce-input-stream-bytes internally with a FileInputStream class."
  [file-name consumer]
  (with-open [input-stream (FileInputStream. file-name)]
    (produce-input-stream-bytes input-stream consumer)))

(defn produce-file-chars
  "Stream characters from a file, specified by file-name, uses
  produce-input-stream-chars internally with a FileInputStream class."
  [file-name consumer]
  (with-open [input-stream (FileInputStream. file-name)]
    (produce-input-stream-chars input-stream consumer)))

(defn produce-file-lines
  "Stream lines from a file, specified by file-name, uses
  produce-input-stream-lines internally with a FileInputStream class."
  [file-name consumer]
  (with-open [input-stream (FileInputStream. file-name)]
    (produce-input-stream-lines input-stream consumer)))

(defn produce-proc-bytes
  "Stream bytes from an OS process executing the cmd command, it uses
  produce-input-stream-bytes internally."
  [cmd consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    (produce-input-stream-bytes input-stream consumer)))

(defn produce-proc-chars
  "Stream chars from an OS process executing the cmd command, it uses
  produce-input-stream-chars internally."
  [cmd consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    (produce-input-stream-chars input-stream consumer)))

(defn produce-proc-lines
  "Stream lines from an OS process executing the cmd command, it uses
  produce-input-stream-lines internally."
  [cmd consumer]
  (with-open [input-stream (-> (Runtime/getRuntime)
                               (.exec cmd)
                               (.getInputStream))]
    (produce-input-stream-lines input-stream consumer)))

