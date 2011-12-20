(ns core.stream.types)

(defrecord ConsumerDone [result remainder])

(def eof :eof)
(def eof? #(= :eof %))

(def empty-chunk? empty?)

(defn yield?
  [step] (-> (type step) (= ConsumerDone)))

(defn yield [result remainder]
  (ConsumerDone. result remainder))

(defn has-remainder? [result]
  (not (eof? (:remainder result))))

(defn no-remainder? [result]
  (eof? (:remainder result)))

(def continue? fn?)
(def continue identity)

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

