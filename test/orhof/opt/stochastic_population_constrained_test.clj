(ns orhof.opt.stochastic-population-constrained-test
  "Tests for stochastic, population, and constrained optimization methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.opt.stochastic :as sto]
            [orhof.opt.population :as pop]
            [orhof.opt.constrained :as con]
            [orhof.opt.descent :as desc]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

(defn sphere [x] (reduce + (map #(* % %) x)))

;; --- Stochastic ---

(deftest test-noisy-descent
  (let [result (sto/noisy-descent sphere [5.0 5.0]
                 :lr 0.1 :sigma 0.01 :max-iter 1000)]
    (testing "Noisy descent converges on sphere"
      (is (< (c/vnorm (:x result)) 1.0)))))

(deftest test-mads
  (let [result (sto/mads sphere [5.0 5.0] :max-iter 500)]
    (testing "MADS converges on sphere"
      (is (< (:value result) 1.0)))))

(deftest test-cma-es
  (let [result (sto/cma-es sphere 2 :max-iter 100)]
    (testing "CMA-ES converges on sphere"
      (is (< (:value result) 1.0)))))

;; --- Population ---

(deftest test-firefly
  (let [result (pop/firefly-algorithm sphere 2 :max-iter 100)]
    (testing "Firefly algorithm finds good solution on sphere"
      (is (< (:value result) 5.0)))))

(deftest test-cuckoo-search
  (let [result (pop/cuckoo-search sphere 2 :max-iter 100)]
    (testing "Cuckoo search finds good solution on sphere"
      (is (< (:value result) 2.0)))))

;; --- Constrained ---

(deftest test-interior-point
  (let [;; Minimize x^2 + y^2 subject to x + y >= 2
        ;; Constraints must be: g(x) <= 0 form, so: 2 - x - y <= 0
        f sphere
        constraints [(fn [[x y]] (- 2.0 (+ x y)))]
        result (con/interior-point f constraints [3.0 3.0] :max-iter 200)]
    (testing "Interior point respects constraint"
      (is (>= (+ (first (:x result)) (second (:x result))) 1.5)))))

(deftest test-admm
  (let [;; ADMM splits: min f(x) + g(z) s.t. x = z
        ;; f(x) = x^2, g(z) = indicator(z = 1)
        ;; x-update: argmin_x [ x^2 + rho/2*(x - z + u)^2 ]
        ;;   derivative: 2x + rho*(x - z + u) = 0 => x = rho*(z-u)/(2+rho)
        result (con/admm
                 (fn [z u rho]
                   (/ (* rho (- z u)) (+ 2.0 rho)))
                 (fn [x u]  ;; z-update: project x+u to {1}
                   1.0)
                 0.0  ;; x0
                 :rho 1.0 :max-iter 200)]
    (testing "ADMM z converges toward constraint"
      ;; z is always 1.0 (the projection). x should approach z.
      (is (= (:z result) 1.0))
      ;; x approaches 1 as dual variable u builds up
      (is (< (Math/abs (- (double (:x result)) 1.0)) 0.5)))))

(deftest test-dual-ascent
  (let [;; min x^2 + y^2 s.t. x + y = 1
        f sphere
        eq-constraints [(fn [[x y]] (- (+ x y) 1.0))]
        result (con/dual-ascent f eq-constraints [0.0 0.0] :max-iter 200)]
    (testing "Dual ascent satisfies equality constraint approximately"
      (is (< (Math/abs (- (+ (first (:x result)) (second (:x result))) 1.0)) 0.3)))))

(deftest test-kkt-check
  (let [;; At x=[0.5, 0.5], min x^2+y^2 s.t. x+y>=1
        ;; KKT: grad f = lambda * grad g, lambda >= 0, g(x) <= 0
        violations (con/kkt-check
                     sphere
                     [(fn [[x y]] (- 1.0 (+ x y)))]  ;; g: 1-x-y <= 0
                     [0.5 0.5]
                     [1.0])]  ;; lambda=1
    (testing "KKT returns violation info"
      (is (map? violations))
      (is (contains? violations :stationarity)))))
