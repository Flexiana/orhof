(ns orhof.opt.surrogate-test
  "Tests for surrogate-based optimization methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.opt.surrogate :as sur]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

(deftest test-full-factorial
  (let [samples (sur/full-factorial [[0.0 1.0] [0.0 1.0]] 3)]
    (testing "Full factorial produces correct count"
      (is (= 9 (count samples))))  ;; 3^2
    (testing "All samples within bounds"
      (is (every? (fn [[x y]] (and (<= 0 x 1) (<= 0 y 1))) samples)))))

(deftest test-latin-hypercube
  (let [samples (sur/latin-hypercube 2 10)]
    (testing "LHS produces correct count"
      (is (= 10 (count samples))))
    (testing "All samples are 2D"
      (is (every? #(= 2 (count %)) samples)))))

(deftest test-rbf-surrogate
  (let [;; Fit RBF to sin(x) at 5 points
        points [[0.0] [1.0] [2.0] [3.0] [4.0]]
        values (mapv (fn [[x]] (Math/sin x)) points)
        model  (sur/rbf-surrogate points values)]
    (testing "RBF interpolates at training points"
      (is (< (Math/abs (- (model [0.0]) 0.0)) 0.3)))
    (testing "RBF predicts between training points"
      (is (< (Math/abs (- (model [1.5]) (Math/sin 1.5))) 0.5)))))

(deftest test-gaussian-process
  (let [points [[0.0] [1.0] [2.0] [3.0] [4.0]]
        values (mapv (fn [[x]] (Math/sin x)) points)
        gp     (sur/gaussian-process points values)]
    (testing "GP returns mean and variance"
      (let [{:keys [mean variance]} (gp [1.5])]
        (is (some? mean))
        (is (some? variance))
        (is (>= variance 0.0))))))

(deftest test-expected-improvement
  (let [;; Simple: EI should be positive where uncertainty is high
        ei-fn (sur/expected-improvement
                (fn [x] {:mean 1.0 :variance 0.5})
                0.5)]  ;; best-so-far
    (testing "EI returns non-negative value"
      (is (>= (ei-fn [0.0]) 0.0)))))

(deftest test-bayesian-optimization
  (let [;; Minimize (x-0.3)^2 on [0, 1]
        f (fn [[x]] (* (- x 0.3) (- x 0.3)))
        result (sur/bayesian-optimization f [[0.0] [1.0]]
                 :n-init 5 :n-iter 10)]
    (testing "Bayesian opt finds approximate minimum"
      (is (< (:best-value result) 0.1)))))
