(ns river.core
  (:require [clojure.algo.monads :as monad]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ConsumerDone [result remainder])

(def ^{:doc "The EOF value used by the stream algorithms"}
  eof ::eof)
(def ^{:doc "Returns true when the eof value is given."}
  eof? #(= eof %))

(def ^{:doc "Returns true when a chunk is empty."}
  empty-chunk? empty?)

(defn yield?
  "Returns true when the consumer is a result rather than a continuation."
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

(defn ensure-done
  "Checks if the consumer has yielded a result, if that's the case it just
  returns the given consumer, otherwise it will call the consumer's
  continuation with the given stream as it's input."
  [consumer stream]
  (cond
    (continue? consumer) (consumer stream)
    (yield? consumer) consumer))

(monad/defmonad stream-m
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

(defn to-filter
  "Transforms a consumer into a filter by feeding the outer input elements
  into the provided consumer until it yields an inner input, passes that to
  the inner consumer and then loops."
  [filter-consumer0 inner-consumer]
  (gen-filter-fn filter-consumer0 filter-consumer0 inner-consumer))

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

(def run produce-eof)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- partialize-consumer [consumer]
  (if (seq? consumer)
    (cons `partial consumer)
    consumer))

(defn- ensure-in-list [producer-or-filter]
  (if (seq? producer-or-filter)
    producer-or-filter
    (list producer-or-filter)))

(defmacro nest-pfc
  ([consumers]
    (if (vector? consumers)
      (map partialize-consumer consumers)
      (partialize-consumer consumers)))
  ([producer-or-filter0 & more]
    (let [producer-or-filter (ensure-in-list producer-or-filter0)]
    ; when the last item (consumer) is
    ; a vector (multiple consumers), then we just concat
    ; that to the producer/filter.
    ; Multiple consumers is used at this moment in the zip* filter
    (if (and (nil? (next more))
             (vector? (first more)))

      (concat producer-or-filter
              (first more))

      (concat producer-or-filter
              `((nest-pfc ~@more)))))))

(defmacro run* [& more]
  `(run (nest-pfc ~@more)))
