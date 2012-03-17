(defproject org.van-clj/river "0.1.2-SNAPSHOT"
  :description "A monadic stream library in Clojure (port of Haskell's enumerator)"
  :author "Roman Gonzalez"
  :repositories { "sonatype" {:url "https://oss.sonatype.org/content/repositories/snapshots/"}}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/algo.monads "0.1.3-20120206.105742-1"]]
  :eval-on-reflection true
  :dev-dependencies [[marginalia "0.7.0"]
                     [lein-marginalia "0.7.0"]])
