(ns river.core

  ^{
    :author "Roman Gonzalez"
  }

  (:require [clojure.algo.monads :as monad]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Consumer record, builder and query functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ConsumerDone [result remainder])

(def ^{:doc "The EOF value used by the stream algorithms"}
  eof ::eof)

(def ^{:doc "Returns true when the eof value is given."}
  eof? #(= eof %))

(def ^{:doc "Returns true when a chunk is empty."}
  empty-chunk? empty?)

(defn yield?
  "Returns true when the consumer is a result rather than a
  continuation."
  [consumer] (-> (type consumer) (= ConsumerDone)))

(defn yield
  "Returns a result from a consumer, the only way to return results is
  by using the yield function"
  [result remainder] (ConsumerDone. result remainder))

(defn has-remainder?
  "Returns true when the remainder of a consumer result is not EOF."
  [result] (not (eof? (:remainder result))))

(defn no-remainder?
  "Returns true when the remainder of a consumer is EOF."
  [result] (eof? (:remainder result)))

(defn empty-remainder?
  "Returns true when the remainder of a consumer result is not EOF and
  it is empty."
  [result]
  (and (not (no-remainder? result))
       (empty? (:remainder result))))

(def
  ^{:doc "Returns true when the consumer is a continuation."}
  continue? fn?)

(def
  ^{:doc "Returns a continuation from a consumer." }
  continue identity)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Utility functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ensure-done
  "Checks if the consumer has yielded a result, if that's the case it just
  returns the given consumer, otherwise it will call the consumer's
  continuation with the given stream as it's input."
  [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Monadic implementation of Consumer
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(monad/defmonad river-m
  [ m-result (fn [v] (yield v []))
    m-bind   (fn bind-fn [consumer f]
               (cond
                 (and (yield? consumer)
                      (empty-remainder? consumer))
                   (f (:result consumer))

                 (yield? consumer)
                   (let [next-consumer (f (:result consumer))]
                     (cond
                       (continue? next-consumer)
                         (next-consumer (:remainder consumer))
                       (yield? next-consumer)
                         (yield (:result next-consumer)
                                (:remainder consumer))))

                 (continue? consumer)
                   (comp #(bind-fn % f) consumer)))
  ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Basic Producers/Consumers/Filters
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn produce-eof
  "Feeds an EOF to the given consumer, in case the consumer doesn't yield
  a result, an exception is thrown."
  [consumer]
  (cond
    (yield? consumer) consumer
    (continue? consumer)
      (let [result (consumer eof)]
        (if (continue? result)
          (throw (Exception. "ERROR: Missbehaving consumer"))
          result))))

(defn is-eof?
  "A consumer that yields a boolean that tells if the feed has reached
  the EOF."
  [stream]
  (cond
    (eof? stream) (yield true eof)
    :else (yield false stream)))

(defn print-chunks [stream]
  "A consumer that prints the chunks is receiving into standard output,
  this consumer will consume all the stream and it will yield a nil value."
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue print-chunks)
    :else
      (do
        (println stream)
        (continue print-chunks))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Utility macros and functions to run consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run
  "Allows to terminate ask for termination of producer, filter or consumer."
  [& more]
  (produce-eof (reduce #(%2 %1) (reverse more))))

(defmacro do-consumer [steps result]
  "Binds the river-m monadic implementation to the domonad macro,
  check clojure.algo.monads/domonad for further info."
  `(monad/domonad river-m ~steps ~result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Filter functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concat-stream [s1 s2]
  (cond
  (or (= s1 eof)
      (= s2 eof)) eof
  :else (concat s1 s2)))

(defn ensure-inner-done
  ([f consumer] (ensure-inner-done [] f consumer))
  ([extra f consumer]
    (fn [stream]
      (cond
      (yield? consumer) (yield consumer (concat-stream extra stream))
      :else (f consumer (concat-stream extra stream))))))

(defn to-filter
  "Transforms a consumer into a filter by feeding the outer input elements
  into the provided consumer until it yields an inner input, passes that to
  the inner consumer and then loops."
  [consumer0*]
  (letfn [
    (loop-consumer* [acc consumer* stream]
      ; ^ this function will feed all the stream possible
      ; to the filter consumer (consumer*), once the whole
      ; stream is empty, we return whatever the consumer* was
      ; able to parse from it, and the current state of
      ; consumer*
      (let [new-stream (concat (:remainder consumer*) stream)]
        (cond
        (empty? new-stream) [acc consumer*]
        (yield? consumer*)
        (recur (conj acc (:result consumer*))
               consumer0*
               (concat (:remainder consumer*) stream))
        (continue? consumer*)
          (recur acc (consumer* stream) []))))

    (outer-consumer [consumer* inner-consumer stream]
      (cond
        (eof? stream)
        (let [final-result (produce-eof consumer*)]
          (yield (inner-consumer [(:result final-result)])
                 stream))

        (empty? stream)
        (continue #(outer-consumer consumer* inner-consumer %))

        :else
        (let [[new-stream consumer1*] (loop-consumer* [] consumer* stream)]
          (ensure-inner-done (partial outer-consumer consumer1*)
                             (inner-consumer new-stream)))))]

  (fn to-outer-consumer [inner-consumer]
    (ensure-inner-done (partial outer-consumer consumer0*)
                       inner-consumer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn *c
  "Binds a filter to a consumer."
  ([a-filter consumer]
    (letfn [
      (check [step]
        (cond
          (continue? step) (recur (produce-eof step))
          (yield? step) step
          :else
            (throw (Exception. "Something terrible happened!"))))]
    (do-consumer [
      :let [outer-consumer (a-filter consumer)]
      inner-consumer outer-consumer
      result (check inner-consumer)]
     result)))

  ([a-filter b-filter & filters]
    (let [more (->> filters (cons b-filter) (cons a-filter))
          [consumer a-filter & more] (reverse more)]
      (reduce #(*c %2 %1) (*c a-filter consumer) more))))

(defn p*
  "Binds a filter to a producer."
  ([producer a-filter]
    (fn new-producer [consumer]
      (let [new-consumer (produce-eof (producer (a-filter consumer)))]
        (cond
          (yield? new-consumer)
            (:result new-consumer)
          :else
            (throw (Exception. "attach-filter: missbehaving consumer"))))))

  ([producer a-filter & more]
    (reduce p* (p* producer a-filter) more)))


