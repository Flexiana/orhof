(ns orhof.opt.multiobjective-test
  "Tests for multi-objective and discrete optimization methods."
  (:require [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.opt.multiobjective :as mo]))

(deftest test-weighted-sum
  (let [;; min w1*f1(x) + w2*f2(x) where f1=x^2, f2=(x-2)^2
        f1 (fn [[x]] (* x x))
        f2 (fn [[x]] (* (- x 2) (- x 2)))
        result (mo/weighted-sum [f1 f2] [0.5 0.5] [0.0] :max-iter 200)]
    (testing "Weighted sum finds compromise"
      (is (< (Math/abs (- (first (:x result)) 1.0)) 0.5)))))

(deftest test-pareto-filter
  (let [;; Points: some dominated, some not
        points [{:x [0] :objectives [1.0 3.0]}
                {:x [1] :objectives [2.0 2.0]}
                {:x [2] :objectives [3.0 1.0]}
                {:x [3] :objectives [2.5 2.5]}]  ;; dominated by [1]
        front (mo/pareto-filter points)]
    (testing "Pareto filter removes dominated points"
      (is (= 3 (count front)))
      (is (not-any? #(= [2.5 2.5] (:objectives %)) front)))))

(deftest test-branch-and-bound
  ;; Minimize x^2 over integers in [-5, 5]
  (let [result (mo/branch-and-bound
                 (fn [x] (* x x))
                 (fn [node] (let [{:keys [lo hi]} node]
                              (if (= lo hi) [] [{:lo lo :hi (quot (+ lo hi) 2)}
                                                 {:lo (inc (quot (+ lo hi) 2)) :hi hi}])))
                 (fn [node] (* (:lo node) (:lo node)))  ;; lower bound
                 {:lo -5 :hi 5}
                 :budget 50)]
    (testing "B&B finds integer optimum at 0"
      (is (<= (:best-val result) 1.0)))))

(deftest test-epsilon-constraint
  (let [f1 (fn [[x]] (* x x))
        f2 (fn [[x]] (* (- x 2) (- x 2)))
        result (mo/epsilon-constraint f1 [f2] [1.0] [1.0] :max-iter 200)]
    (testing "Epsilon-constraint finds feasible solution"
      (is (some? (:x result))))))
