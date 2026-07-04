(ns orhof.test-util
  "Shared test helpers."
  (:require [clojure.test :refer [use-fixtures]]))

(defn with-seeded-rand
  "clojure.test :each fixture that makes clojure.core/rand deterministic for the
   duration of each test, so stochastic algorithms (GA, bandits, sampling, ...)
   produce repeatable results. rand-int and rand-nth delegate to rand, so seeding
   rand alone covers them. Default seed 42; every test starts from the same seed."
  ([t] (with-seeded-rand 42 t))
  ([seed t]
   (let [rng (java.util.Random. seed)]
     (with-redefs [rand (fn ([]  (.nextDouble rng))
                          ([n] (* n (.nextDouble rng))))]
       (t)))))
