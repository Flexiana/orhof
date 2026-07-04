(ns orhof.opt.linear-test
  "Tests for linear and convex programming methods."
  (:require [clojure.test :refer :all]
            [orhof.opt.linear :as lp]))

(deftest test-simplex
  ;; Maximize 2x + 3y s.t. x + y <= 4, x <= 3, y <= 3, x,y >= 0
  ;; Standard form: minimize -2x - 3y s.t. Ax <= b
  (let [result (lp/simplex [-2.0 -3.0]           ;; c (minimize c^T x)
                            [[1.0 1.0]             ;; A
                             [1.0 0.0]
                             [0.0 1.0]]
                            [4.0 3.0 3.0])]        ;; b
    (testing "Simplex finds optimal LP solution"
      (is (some? (:x result)))
      ;; Optimal: x=1, y=3, obj = -2-9 = -11
      (is (< (Math/abs (- (:objective result) -11.0)) 0.1)))))

(deftest test-dual-simplex
  ;; Same problem as above, solved via dual
  (let [result (lp/dual-simplex [-2.0 -3.0]
                                 [[1.0 1.0]
                                  [1.0 0.0]
                                  [0.0 1.0]]
                                 [4.0 3.0 3.0])]
    (testing "Dual simplex finds same optimal"
      (is (some? (:x result)))
      (is (< (Math/abs (- (:objective result) -11.0)) 0.1)))))

(deftest test-qp-solver
  ;; Minimize x^2 + y^2 s.t. x + y >= 1 (i.e., -x - y <= -1)
  ;; Q = [[2 0],[0 2]], c = [0 0], A = [[-1 -1]], b = [-1]
  ;; Optimal: x = y = 0.5
  (let [result (lp/qp-solve [[2.0 0.0] [0.0 2.0]]  ;; Q (Hessian of objective)
                             [0.0 0.0]                ;; c (linear term)
                             [[-1.0 -1.0]]            ;; A (inequality constraints)
                             [-1.0])]                  ;; b
    (testing "QP solver finds constrained minimum"
      (is (< (Math/abs (- (first (:x result)) 0.5)) 0.2))
      (is (< (Math/abs (- (second (:x result)) 0.5)) 0.2)))))

(deftest test-dcp-check
  ;; Verify that a problem is DCP-compliant
  (let [;; min x^2 + y (convex objective, linear = convex)
        ;; s.t. x + y <= 1 (affine constraint — valid)
        result (lp/dcp-check
                 {:objective {:type :add
                              :args [{:type :square :arg {:type :var :name :x}}
                                     {:type :var :name :y}]}
                  :constraints [{:type :leq
                                 :lhs {:type :add
                                        :args [{:type :var :name :x}
                                               {:type :var :name :y}]}
                                 :rhs {:type :const :value 1.0}}]})]
    (testing "DCP check validates convex problem"
      (is (:valid? result)))))
