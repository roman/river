(ns core.stream.types
  (:require [clojure.algo.monads :as m]))

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

(def continue? fn?)
(def continue identity)

(defn ensure-done [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

(m/defmonad stream-m
  [ m-result (fn [v] (yield v []))
    m-bind   (fn bind-fn [step gn]
               (cond
                 (and (yield? step)
                      (no-remainder? step))
                   (gn (:result step))

                 (and (yield? step)
                      (has-remainder? step))
                   (let [step-2 (gn (:result step))]
                     (cond
                       (continue? step-2)
                         (step-2 (:remainder step))
                       (yield? step-2)
                         (yield (:result step-2)
                                (:remainder step))))
                 (continue? step)
                   (comp #(bind-fn % gn) step)))

  ])

