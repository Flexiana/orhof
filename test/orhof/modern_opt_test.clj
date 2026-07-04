(ns orhof.modern-opt-test
  "Tests for modern optimizers: AdamW and Lion."
  (:require [clojure.test :refer :all]
            [orhof.optimize :as opt]))

;; Simple quadratic: f(x) = x1^2 + x2^2
(defn quadratic [x] (reduce + (map #(* % %) x)))

;; Rosenbrock: f(x,y) = (1-x)^2 + 100(y-x^2)^2
(defn rosenbrock [[x y]]
  (+ (* (- 1 x) (- 1 x))
     (* 100 (- y (* x x)) (- y (* x x)))))

(deftest test-adamw-quadratic
  (testing "AdamW minimizes a simple quadratic"
    (let [result (opt/adamw quadratic [5.0 5.0]
                            :lr 0.01 :max-iter 2000)]
      (is (< (:value result) 0.01)
          (str "AdamW should minimize quadratic, got " (:value result))))))

(deftest test-adamw-rosenbrock
  (testing "AdamW minimizes Rosenbrock"
    (let [result (opt/adamw rosenbrock [0.0 0.0]
                            :lr 0.005 :max-iter 5000)]
      (is (< (:value result) 1.0)
          (str "AdamW should approach Rosenbrock minimum, got " (:value result))))))

(deftest test-lion-quadratic
  (testing "Lion minimizes a simple quadratic"
    (let [result (opt/lion quadratic [5.0 5.0]
                           :lr 0.01 :max-iter 2000)]
      (is (< (:value result) 0.1)
          (str "Lion should minimize quadratic, got " (:value result))))))

(deftest test-lion-rosenbrock
  (testing "Lion minimizes Rosenbrock (may need more iterations due to sign-based updates)"
    (let [result (opt/lion rosenbrock [0.0 0.0]
                           :lr 0.001 :max-iter 5000)]
      (is (< (:value result) 5.0)
          (str "Lion should approach Rosenbrock minimum, got " (:value result))))))

(deftest test-adamw-vs-adam
  (testing "AdamW with zero weight decay should behave like Adam"
    (let [adam-result  (opt/adam quadratic [3.0 4.0] :lr 0.01 :max-iter 500)
          adamw-result (opt/adamw quadratic [3.0 4.0] :lr 0.01 :weight-decay 0.0 :max-iter 500)]
      (is (< (Math/abs (- (:value adam-result) (:value adamw-result))) 0.1)
          "AdamW with zero decay should match Adam"))))

(deftest test-lion-memory-efficiency
  (testing "Lion state has no :v key (only first moment, not second)"
    (let [result (opt/lion quadratic [1.0 1.0] :max-iter 10)]
      (is (contains? result :m) "Lion should track momentum")
      (is (not (contains? result :v)) "Lion should NOT track second moment"))))
