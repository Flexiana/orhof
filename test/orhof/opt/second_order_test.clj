(ns orhof.opt.second-order-test
  "Tests for second-order (quasi-Newton) optimization methods."
  (:require [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.opt.second-order :as so]))

(defn sphere [x] (reduce + (map #(* % %) x)))

(defn rosenbrock [[x y]]
  (+ (Math/pow (- 1 x) 2) (* 100 (Math/pow (- y (* x x)) 2))))

(deftest test-secant-method
  (let [result (so/secant-method sphere [5.0 5.0] :max-iter 200)]
    (testing "Secant method converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-dfp
  (let [result (so/dfp sphere [5.0 5.0] :max-iter 200)]
    (testing "DFP converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-bfgs
  (let [result (so/bfgs sphere [5.0 5.0] :max-iter 200)]
    (testing "BFGS converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-lbfgs
  (let [result (so/lbfgs sphere [5.0 5.0] :max-iter 200)]
    (testing "L-BFGS converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

(deftest test-levenberg-marquardt
  (let [;; Minimize sum of squared residuals: r(x) = [x1-1, x2-2]
        ;; Solution: [1, 2]
        residuals (fn [[x1 x2]] [(- x1 1.0) (- x2 2.0)])
        result (so/levenberg-marquardt residuals [5.0 5.0] :max-iter 100)]
    (testing "Levenberg-Marquardt converges on least-squares"
      (is (< (Math/abs (- (first (:x result)) 1.0)) 0.5))
      (is (< (Math/abs (- (second (:x result)) 2.0)) 0.5)))))
