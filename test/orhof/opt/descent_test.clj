(ns orhof.opt.descent-test
  "Tests for first-order descent method variants."
  (:require [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.opt.descent :as desc]))

(defn sphere [x] (reduce + (map #(* % %) x)))

(defn rosenbrock [[x y]]
  (+ (Math/pow (- 1 x) 2) (* 100 (Math/pow (- y (* x x)) 2))))

;; --- Nesterov Momentum ---

(deftest test-nesterov-momentum
  (let [result (desc/nesterov-momentum sphere [5.0 5.0]
                 :lr 0.01 :beta 0.9 :max-iter 2000)]
    (testing "Nesterov converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

;; --- AdaGrad ---

(deftest test-adagrad
  (let [result (desc/adagrad sphere [5.0 5.0]
                 :lr 0.5 :max-iter 1000)]
    (testing "AdaGrad converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

;; --- RMSProp ---

(deftest test-rmsprop
  (let [result (desc/rmsprop sphere [5.0 5.0]
                 :lr 0.01 :max-iter 2000)]
    (testing "RMSProp converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))

;; --- Adadelta ---

(deftest test-adadelta
  (let [result (desc/adadelta sphere [5.0 5.0]
                 :max-iter 3000)]
    (testing "Adadelta converges on sphere (no learning rate needed)"
      (is (< (c/vnorm (:x result)) 1.0)))))

;; --- Hypergradient Descent ---

(deftest test-hypergradient-descent
  (let [result (desc/hypergradient-descent sphere [5.0 5.0]
                 :lr 0.01 :max-iter 2000)]
    (testing "Hypergradient descent converges on sphere"
      (is (< (c/vnorm (:x result)) 0.5)))))
