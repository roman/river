(ns river.core

  ^{
    :author "Roman Gonzalez"
  }

  (:require [clojure.algo.monads :as monad]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Consumer record, builder and query functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ConsumerDone [result remainder])

(def eof
  "A flag used by producers to indicate EOF"
  ::eof)

(def ^{
    :arglists '([stream]),
    :doc "Returns true when `stream` is equal to the eof flag."}
  eof? #(= eof %))

(def ^{
    :arglists '([stream]),
    :doc "Returns true when a the given `stream` is an empty chunk."}
  empty-chunk? empty?)

(defn yield?
  "Returns true when the consumer is a result rather than a
  continuation."
  [consumer] (-> (type consumer) (= ConsumerDone)))

(defn yield
  "Returns a result from a consumer, you _should_ always return values
  from a consumer using this funcion."
  [result remainder] (ConsumerDone. result remainder))

(defn has-remainder?
  "Returns true when the remainder of a consumer yield result is not EOF."
  [result] (not (eof? (:remainder result))))

(defn no-remainder?
  "Returns true when the remainder of a consumer yield result is EOF."
  [result] (eof? (:remainder result)))

(defn empty-remainder?
  "Returns true when the remainder of a consumer yield result is not EOF and
  it is empty."
  [result]
  (and (not (no-remainder? result))
       (empty? (:remainder result))))

(def ^{
    :arglists '([consumer])
    :doc "Returns true when the `consumer` is a continuation function."}
  continue? fn?)

(def ^{
  :arglists '([continuation])
  :doc "Returns a continuation from a consumer, you _should_ always return
  continuations from a consumer using this function."}
  continue identity)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Utility functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ensure-done
  "Checks if the `consumer` is a `yield` result, if that's the case this value
  is returned, otherwise it will call the `consumer`'s continuation with the
  given `stream` as it's input."
  [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Monadic implementation of Consumer
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bind-consumer
  "Receives a `consumer` and a continuation function, the result of the
  `consumer ` is then given as an argument to the `f` function, and this
  function must return a new consumer. This function is used for monadic
  notation of consumers."
  [consumer f]
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
    (comp #(bind-consumer % f) consumer)))

(monad/defmonad consumer-m
  [ m-result (fn result-fn [v] (yield v []))
    m-bind   (fn bind-fn [consumer f]
               (bind-consumer consumer f))
  ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Basic Producers/Consumers/Filters
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

(defn print-chunks
  "A consumer that prints the chunks is receiving into standard output,
  this consumer will consume all the stream and it will yield a nil value."
  [stream]
  (cond
    (eof? stream) (yield nil eof)
    (empty-chunk? stream) (continue print-chunks)
    :else
      (do
        (println stream)
        (continue print-chunks))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Function to run consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run
  "Allows to terminate ask for termination of producer, filter or consumer."
  [& more]
  (produce-eof (reduce #(%2 %1) (reverse more))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Composing consumers together
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro do-consumer
  "Binds the consumer-m monadic implementation to the domonad macro,
  check `clojure.algo.monads/domonad` for further info.

  Example:

    (def new-consumer
        (do-consumer [
          _ (river.seq/drop-while #(not= 0))
          n (river.seq/first)
        ]
        result))

    (run (river.seq/produce-seq [20 3 4 0 5 6])
         new-consumer)

    ; #river.core.ConsumerDone { :result 5 :remainder (6) }"
  [steps result]
  `(monad/domonad consumer-m ~steps ~result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Concatanating producers together
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concat-producer
  "Concatenates two ore more producers, creating new producer
  that's going stream both producers.

  Example:

    (def new-producer
        (concat-producer (river.seq/produce-seq (range 1 10))
                         (river.seq/produce-seq (range 11 20))))
    (run new-producer river.seq/consume)
    ; river.core.ConsumerDone { :result (range 1 20) :remainder eof }"
  [& producers]
  (fn new-producer [consumer]
    (reduce #(%2 %1) consumer producers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Filter functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concat-stream
  "Concatenates two streams together; whenever a stream gets concatenated
  with `river.core/eof`, the latter is returned."
  [s1 s2]
  (cond
  (or (= s1 eof)
      (= s2 eof)) eof
  :else (concat s1 s2)))

;(defn ensure-inner-done
;  ([f inner-consumer] (ensure-inner-done [] f inner-consumer))
;  ([extra f inner-consumer]
;    (fn ensure-inner-done-consumer [stream]
;      (cond
;
;      (yield? inner-consumer)
;      (yield inner-consumer (concat-stream extra stream))
;
;      (continue? inner-consumer)
;      (f inner-consumer (concat-stream extra stream))
;
;      :else
;      (throw
;        (Exception.
;          "ensure-inner-done: invalid consumer (not yield nor continue)"))))))

(defn to-filter
  "Transforms a consumer into a filter by feeding the outer input elements
  into the provided consumer until it yields an inner input, passes that to
  the inner consumer and then loops."
  [consumer0*]
  (letfn [
    (loop-consumer* [acc consumer* outer-stream]
      ; ^ this function will feed all the stream possible
      ; to the filter consumer (consumer*), once the whole
      ; stream is empty, we return whatever the consumer* was
      ; able to parse from it, and the current state of
      ; consumer*
      (cond

      (and (empty-chunk? outer-stream)
           (continue? consumer*)) [acc consumer*]

      (yield? consumer*)
        (recur (conj acc (:result consumer*))
               consumer0*
               (concat (:remainder consumer*) outer-stream))

      (continue? consumer*)
        (recur acc (consumer* outer-stream) [])))

    (outer-consumer [consumer* inner-consumer outer-stream]
      (cond

      (yield? inner-consumer)
        (yield inner-consumer outer-stream)

      (continue? inner-consumer)
        (cond

        (eof? outer-stream)
        (let [final-result (produce-eof consumer*)]
          (yield (inner-consumer [(:result final-result)])
                 outer-stream))

        (empty-chunk? outer-stream)
        (continue #(outer-consumer consumer* inner-consumer %))

        :else
        (let [[inner-stream consumer1*] (loop-consumer* []
                                                        consumer*
                                                        outer-stream)]

          (recur consumer1* (inner-consumer inner-stream) []))
      :else
        (throw
          (Exception.
            "to-filter: invalid inner consumer (not yield nor continue)")))))]

  (fn to-outer-consumer [inner-consumer]
    #(outer-consumer consumer0* inner-consumer %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ## Binding filters to producers & consumers
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn *c
  "Binds one or more filters to a consumer."
  ([a-filter consumer]
    (letfn [
      (check [step]
        (cond
          (continue? step)
          (do
            (produce-eof step))
          (yield? step) step))]
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
  "Binds one or more filters to a producer."
  ([producer a-filter]
    (fn new-producer [consumer]
      (let [new-consumer (produce-eof (producer (a-filter consumer)))]
        (cond
          (yield? new-consumer)
            (:result new-consumer)
          :else
            (throw (Exception. "p*: missbehaving consumer"))))))

  ([producer a-filter & more]
    (reduce p* (p* producer a-filter) more)))


