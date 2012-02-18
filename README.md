# river [![Build Status](https://secure.travis-ci.org/roman/river.png)](http://travis-ci.org/roman/river)

river tries to provide an implementation of Oleg's Iteratee
library. The terminology was changed in order to offer a more intuitive API for
the end user, however if you are familiar with Haskell's Iteratees, this might
help you get started with this library:

* _Iteratee_ will be called _Consumer_
* _Enumerator_ will be called _Producer_
* _Enumeratee_ will be called _Filter_

The library is initally divided in 3 namespaces

* `river.core` provides:
  * Basic constructs to build your own consumers/producers/filters.
  * A monad implemenation so that you can build monadic consumers.
  * Basic consumers for debugging streams.
  * A macro to easily execute the stream generation, composition and
    execution.

* `river.seq` provides:
  * All the expected combinators that are available for lazy seqs.
  * Handy filters for the streams like `map\*`, `filter\*`, etc.

* `river.io` provides:
  * Ways to produce binary (byte) streams out of Java's `InputStream`s.
  * Produce line streams out of Java's `InputStream`s.
  * Produce input out of files (binary or lines).
  * Produce input out of commands (binary or lines).

The code provided in this library is still beta, there is a lot of room
for improvement and new features, it serves as a starting point to exploit
the Iteratee concepts in Clojure.

## Install

[org.van-clj/river "0.1.0"]

## Usage

To execute a consumer you will use the `run` function:

```clojure
(river.core/run (river.seq/produce-seq (range 1 100))
                (river.core/*c (river.seq/filter* #(= 0 (mod % 2)))
                               (river.seq/take 5)))

; => #river.core/ConsumerDome {:result (2 4 6 8 10), :remainder (12 14)}
```

The code above streams a seq from 1 to 99, then calls `\*c` to bind a filter
to a consumer that takes 5 items from the feed. The result of this execution
will be a `ConsumerDone` record that has the yielded _result_ and the
_remainder_ of the given chunks.

### Stream

The _stream_ is what the consumer receive as an input, the stream could
either be a seq of items (called chunks), or an EOF signal, represented by
`river.core/eof`.

### Consumers

The _consumer_ is the one that process the stream, and it will either _yield_
a result (using `river.core/yield`) or a _continuation_ (using
`river.core/continue`).

`river.core/yield` will be used when the consumer is done consuming from
the stream, two values are returned with the yield, one being the _result_
value, and the second being the _remainder_ of the stream that wasn't consumed,
this is kept in order to compose several consumers together using the
monadic interface.

`river.core/continue` will be used when the consumer hasn't received enough
chunks to yield a result, some consumers might consume part of the stream, some
others would be greedy and consume all the available stream.

### Producers

The _producer_ generates the stream that the consumer will use, they normally
consume a resource like a `lazy-seq`, file, socket, etc; and then transmits it
over to the _consumer_. The advantage of using producers is that the stream
generation is kept independent from the consumption, so the _consumer_ doesn't
need to know where the data is coming from as long as it is valid.

It stops consuming from the given resource as soon as the _consumer_ returns a
_yield_ value.


### Filters

The _filter_ transforms the stream into something different, it either changes
the type of the stream, or modifies the way the input is given.

* * *

One of the powerful characteristics of this stream architecture is that both
Producers and Filters are also consumers, they receive a consumer as a
parameter and they enhance the consumer behavior, they could be perceived as
some sort of _decorator_ in the OO world.

## Examples

### Building filters on the fly

Say for example you want to be able to sum a list of numbers, but this
numbers may come from different resources, some from stdin, others from a
list, etc. To implement this using the river library, you would do
something like the following:

```clojure
(ns river.examples.sum

  (require [clojure.string :as string])

  (use [river.core])
  (require [river.seq :as rs]
           [river.io  :as rio]))

(def words* (rs/mapcat* #(string/split % #"\s+")))
; ^ a filter that applies a split to whatever it recieves
; in the stream, this is assuming the stream is of strings

(def numbers* (rs/map* #(Integer/parseInt %)))
; ^ a fitler that transforms each received item into an
; Integer using the parseInt function, this is assuming
; that the stream is of strings

(defn produce-numbers-from-file
  ([] (produce-numbers-from-file "input.in"))
  ([file-path]
    (p*
    ; ^ whe bind a producer with some filters
    ; using the p* function.
        (rio/produce-file-lines file-path)
        ; ^ produces a stream of lines from a file path
        words*
        ; ^ applies the words filter
        numbers*)))
        ; ^ applies the number filter and transform
        ; the stream from strings to numbers

(defn -main []
  (println (run 
           ; ^ executes a group of producers/consumers
                (produce-numbers-from-file)
                ; ^ produce a stream of numbers from a file
                (rs/produce-seq (range 1 10))
                ; ^ produce a stream of numbers from a seq
                (rs/reduce + 0))))
                ; ^ sums up the numbers ignoring where they come from
```

### Building consumers using monadic interface ###

Sometimes we want to create a new consumer composing simple consumers together,
we can do that using the monadic API:

```clojure
(ns river.examples.monadic
  (:use [river.core])
  (:require [river.seq :as rs]
            [river.io  :as rio]))

(def drop-and-head
  (do-consumer
  ; ^ allows you to create a new consumer using
  ; a monadic notation as in clojure.algo.monads
    [_ (rs/drop-while #(< % 5))
       ; ^ drops from the stream until the condition is met
     b rs/first]
       ; ^ gets the first element after the dropping is done
    b))

(defn -main []
  (println (run
           ; ^ function to execute producers/consumers
                (rs/produce-seq (range -20 20))
                ; ^ produce a stream of numbers from a seq
                drop-and-head)))
                ; drops until < 5 and then gives the first element found
                ; (in this case 6)
```

## License

Copyright (C) 2012 Roman Gonzalez

Distributed under the Eclipse Public License, the same as Clojure.
