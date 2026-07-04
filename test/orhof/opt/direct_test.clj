(ns orhof.opt.direct-test
  "Tests for derivative-free (direct) optimization methods."
  (:require [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.opt.direct :as dir]))

(defn sphere [x] (reduce + (map #(* % %) x)))

(defn booth [[x y]]
  (+ (Math/pow (+ x (* 2 y) -7) 2)
     (Math/pow (+ (* 2 x) y -5) 2)))

(deftest test-cyclic-coordinate-search
  (let [result (dir/cyclic-coordinate-search sphere [5.0 5.0] :max-iter 200)]
    (testing "Cyclic coordinate search converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-powells-method
  (let [result (dir/powells-method sphere [5.0 5.0] :max-iter 200)]
    (testing "Powell's method converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-hooke-jeeves
  (let [result (dir/hooke-jeeves sphere [5.0 5.0] :max-iter 500)]
    (testing "Hooke-Jeeves converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-generalized-pattern-search
  (let [result (dir/generalized-pattern-search sphere [5.0 5.0] :max-iter 500)]
    (testing "GPS converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-direct-method
  (let [;; DIRECT on 1D: minimize (x-0.3)^2 on [0, 1]
        result (dir/direct-search (fn [[x]] (* (- x 0.3) (- x 0.3)))
                                  [[0.0] [1.0]]
                                  :max-iter 50)]
    (testing "DIRECT finds approximate minimum"
      (is (< (:value result) 0.01)))))
