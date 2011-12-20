(ns core.iteratee.types)

(defrecord IterateeResult [result remainder])

(defn yield? 
  [step] (-> (type step) (= IterateeResult)))

(defn yield [result remainder]
  (IterateeResult. result remainder))

(def continue? fn?)
(def continue identity)

(def eof nil)
(def eof? nil?)

(def empty-chunk? empty?)

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

