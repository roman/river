(ns core.iteratee.process
  (:use [core.iteratee.types :only
          [continue continue? yield yield? eof eof?]]

        [core.iteratee.enumerators :only
          [enum-input-stream-bytes enum-input-stream-lines]]))

(defn- proc-input-stream [cmd]
   (-> (Runtime/getRuntime)
       (.exec cmd)
       (.getInputStream)))

(defn enum-proc-bytes [cmd consumer]
  (enum-input-stream-bytes
      (proc-input-stream cmd) consumer))

(defn enum-proc-lines [cmd consumer]
  (enum-input-stream-lines
      (proc-input-stream cmd) consumer))
