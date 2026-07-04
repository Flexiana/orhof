(ns orhof.optimize-test
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.diff :as d]
            [orhof.optimize :as opt]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; =============================================================
;; Test Functions
;; =============================================================

(defn rosenbrock
  "Rosenbrock function: f(x,y) = (1-x)² + 100(y-x²)²
   Minimum at [1, 1] with value 0."
  [[x y]]
  (+ (Math/pow (- 1 x) 2)
     (* 100 (Math/pow (- y (* x x)) 2))))

(defn sphere
  "Sphere function: f(x) = Σ xᵢ². Minimum at origin."
  [x]
  (reduce + (map #(* % %) x)))

(defn rastrigin
  "Rastrigin function. Many local minima. Global min at origin."
  [x]
  (+ (* 10 (count x))
     (reduce + (map (fn [xi] (- (* xi xi) (* 10 (Math/cos (* 2 Math/PI xi))))) x))))

(defn booth
  "Booth function: min at [1, 3] = 0."
  [[x y]]
  (+ (Math/pow (+ x (* 2 y) -7) 2)
     (Math/pow (+ (* 2 x) y -5) 2)))

;; =============================================================
;; Derivative Tests
;; =============================================================

(deftest test-forward-difference
  (let [f  (fn [x] (* x x))
        f' (d/forward-difference f)]
    (is (< (abs (- (f' 3.0) 6.0)) 0.001)
        "Derivative of x² at x=3 should be ~6")))

(deftest test-central-difference
  (let [f  (fn [x] (* x x x))
        f' (d/central-difference f)]
    (is (< (abs (- (f' 2.0) 12.0)) 0.001)
        "Derivative of x³ at x=2 should be ~12")))

(deftest test-gradient
  (let [grad-sphere (d/gradient sphere)]
    (is (< (c/vnorm (c/v- (grad-sphere [1.0 2.0 3.0]) [2.0 4.0 6.0])) 0.001)
        "Gradient of sphere at [1,2,3] should be [2,4,6]")))

(deftest test-auto-diff
  (let [f  (fn [x] (d/dual* x x))  ;; x²
        f' (d/auto-diff f)]
    (is (< (abs (- (f' 3.0) 6.0)) 1e-10)
        "Auto-diff of x² at x=3 should be exactly 6")))

;; =============================================================
;; Bracketing Tests
;; =============================================================

(deftest test-golden-section
  (let [f   (fn [x] (* (- x 3) (- x 3)))
        min (opt/golden-section-search f 0 10)]
    (is (< (abs (- min 3.0)) 0.001)
        "Golden section should find min of (x-3)² at x=3")))

(deftest test-fibonacci-search
  (let [f   (fn [x] (* (- x 2) (- x 2)))
        min (opt/fibonacci-search f -5 10)]
    (is (< (abs (- min 2.0)) 0.01)
        "Fibonacci search should find min of (x-2)² at x=2")))

(deftest test-bisection
  (let [f    (fn [x] (- (* x x) 4))
        root (opt/bisection-method f 0 10)]
    (is (< (abs (- root 2.0)) 0.001)
        "Bisection should find root of x²-4 at x=2")))

;; =============================================================
;; Gradient-Based Optimizer Tests
;; =============================================================

(deftest test-gradient-descent-sphere
  (let [result (opt/gradient-descent sphere [5.0 5.0] :lr 0.1 :max-iter 500)]
    (is (< (c/vnorm (:x result)) 0.1)
        "GD should find minimum of sphere near origin")))

(deftest test-steepest-descent-booth
  (let [result (opt/steepest-descent booth [0.0 0.0] :max-iter 500)]
    (is (< (c/vnorm (c/v- (:x result) [1.0 3.0])) 0.5)
        "Steepest descent should find Booth minimum near [1,3]")))

(deftest test-conjugate-gradient-sphere
  (let [result (opt/conjugate-gradient sphere [5.0 5.0] :max-iter 200)]
    (is (< (c/vnorm (:x result)) 0.1)
        "CG should find minimum of sphere near origin")))

(deftest test-adam-sphere
  (let [result (opt/adam sphere [5.0 5.0] :lr 0.01 :max-iter 5000)]
    (is (< (c/vnorm (:x result)) 1.0)
        "Adam should find minimum of sphere near origin")))

(deftest test-momentum-gd-sphere
  (let [result (opt/momentum-gd sphere [5.0 5.0] :lr 0.01 :max-iter 1000)]
    (is (< (c/vnorm (:x result)) 0.5)
        "Momentum GD should find minimum of sphere near origin")))

;; =============================================================
;; Direct Method Tests
;; =============================================================

(deftest test-nelder-mead-sphere
  (let [result (opt/nelder-mead sphere [5.0 5.0] :max-iter 500)]
    (is (< (c/vnorm (:x result)) 0.1)
        "Nelder-Mead should find minimum of sphere near origin")))

(deftest test-nelder-mead-booth
  (let [result (opt/nelder-mead booth [0.0 0.0] :max-iter 500)]
    (is (< (:value result) 0.1)
        "Nelder-Mead should find Booth minimum with low value")))

;; =============================================================
;; Stochastic Method Tests
;; =============================================================

(deftest test-simulated-annealing-sphere
  (let [result (opt/simulated-annealing sphere [5.0 5.0]
                 :max-iter 20000
                 :neighbor-fn (fn [x] (mapv #(+ % (* 0.5 (- (rand) 0.5))) x))
                 :temp-schedule (fn [t] (* 200.0 (Math/exp (* -0.0005 t)))))]
    (is (< (:best-value result) 2.0)
        "SA should find a good solution for sphere")))

(deftest test-cross-entropy-sphere
  (let [result (opt/cross-entropy-method sphere 2 :max-iter 50)]
    (is (< (:value result) 1.0)
        "CEM should find a good solution for sphere")))

;; =============================================================
;; Population Method Tests
;; =============================================================

(deftest test-genetic-algorithm-sphere
  (let [result (opt/genetic-algorithm sphere 2 :max-iter 100)]
    (is (< (:value result) 2.0)
        "GA should find a good solution for sphere")))

(deftest test-particle-swarm-sphere
  (let [result (opt/particle-swarm sphere 2 :max-iter 100)]
    (is (< (:value result) 1.0)
        "PSO should find a good solution for sphere")))

(deftest test-differential-evolution-sphere
  (let [result (opt/differential-evolution sphere 2 :max-iter 100)]
    (is (< (:value result) 2.0)
        "DE should find a good solution for sphere")))

;; =============================================================
;; Constrained Optimization Tests
;; =============================================================

(deftest test-penalty-method
  (let [;; Minimize x² + y² subject to x + y >= 1
        f           sphere
        constraints [(fn [[x y]] (max 0 (- 1 (+ x y))))]
        penalized   (opt/penalize f constraints 100.0)
        result      (opt/gradient-descent penalized [2.0 2.0] :lr 0.01 :max-iter 2000)]
    (is (> (+ (first (:x result)) (second (:x result))) 0.8)
        "Penalty method should satisfy x+y >= 1 approximately")))

;; =============================================================
;; Factory Pattern Test
;; =============================================================

(deftest test-make-optimizer
  (let [solver (opt/make-optimizer :nelder-mead :max-iter 500)
        result (solver sphere [5.0 5.0])]
    (is (< (:value result) 0.1)
        "Factory-created optimizer should work")))

;; =============================================================
;; HOF Pattern Tests: Composition and Transformation
;; =============================================================

(deftest test-transform-problem
  (let [;; Transform: minimize f(x) → minimize f(x + offset)
        offset  [3.0 3.0]
        transform (fn [problem]
                    (assoc problem :objective
                           (fn [x] ((:objective problem) (c/v+ x offset)))))
        solver  (fn [problem] (opt/nelder-mead (:objective problem) [0.0 0.0] :max-iter 500))
        result  (c/transform-problem transform solver {:objective sphere})]
    (is (< (:value result) 0.1)
        "Transform-problem pattern should work")))

(deftest test-compose-pipeline
  (let [;; Pipeline: normalize → optimize → denormalize
        scale   10.0
        normalize   (fn [x] (mapv #(/ % scale) x))
        denormalize (fn [x] (mapv #(* % scale) x))
        pipeline    (c/compose-pipeline normalize
                                        (fn [x] (:x (opt/nelder-mead sphere x :max-iter 300)))
                                        denormalize)
        result      (pipeline [50.0 50.0])]
    (is (< (c/vnorm result) 1.0)
        "Compose-pipeline should chain transformations")))
