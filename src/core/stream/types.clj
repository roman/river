(ns core.stream.types)

(defrecord ConsumerDone [result remainder])

(defn yield? 
  [step] (-> (type step) (= ConsumerDone)))

(defn yield [result remainder]
  (ConsumerDone. result remainder))

(def continue? fn?)
(def continue identity)

(def eof nil)
(def eof? nil?)

(def empty-chunk? empty?)

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

