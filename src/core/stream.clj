(ns core.stream
  (:require [clojure.algo.monads :as monad]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ConsumerDone [result remainder])

(def eof ::eof)
(def eof? #(= ::eof %))

(def empty-chunk? empty?)

(defn yield?
  [step] (-> (type step) (= ConsumerDone)))

(defn yield [result remainder]
  (ConsumerDone. result remainder))

(defn has-remainder? [result]
  (not (eof? (:remainder result))))

(defn no-remainder? [result]
  (eof? (:remainder result)))

(defn empty-remainder? [result]
  (and (not (no-remainder? result))
       (empty? (:remainder result))))

(def continue? fn?)
(def continue identity)

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

(monad/defmonad stream-m
  [ m-result (fn [v] (yield v []))
    m-bind   (fn bind-fn [step f]
               (cond
                 (and (yield? step)
                      (empty-remainder? step))
                   (f (:result step))

                 (yield? step)
                   (let [step-2 (f (:result step))]
                     (cond
                       (continue? step-2)
                         (step-2 (:remainder step))
                       (yield? step-2)
                         (yield (:result step-2)
                                (:remainder step))))

                 (continue? step)
                   (comp #(bind-fn % f) step)))
  ])

(defn produce-eof [consumer]
  (cond
    (yield? consumer) consumer
    (continue? consumer)
      (let [result (consumer eof)]
        (if (continue? result)
          (throw (Exception. "ERROR: Missbehaving consumer"))
          result))))

(defn- gen-filter-fn [filter-consumer0 filter-consumer inner-consumer]
  (cond
    (yield? inner-consumer) inner-consumer
    :else
      (cond
        (yield? filter-consumer)
          (let [filter-result       (:result filter-consumer)
                filter-remainder    (:remainder filter-consumer)
                next-inner-consumer (inner-consumer filter-result)]

            (if (no-remainder? filter-consumer)
              (recur filter-consumer0
                     filter-consumer
                     (ensure-done next-inner-consumer eof))

              (recur filter-consumer0
                     (filter-consumer0 filter-remainder)
                     next-inner-consumer)))

        :else
          (fn filter-fn [stream]
            (gen-filter-fn filter-consumer0
                           (filter-consumer stream)
                           inner-consumer)))))

(defn to-filter [filter-consumer0 inner-consumer]
  (gen-filter-fn filter-consumer0 filter-consumer0 inner-consumer))

(defn is-eof? [stream]
  (cond
    (eof? stream) (yield true eof)
    :else (yield false stream)))

(defn print-chunks [stream]
  (cond
    (eof? stream)
      (yield nil eof)

    (empty-chunk? stream)
      (continue print-chunks)

    :else
      (do
        (println stream)
        (continue print-chunks))))

(def run* produce-eof)
