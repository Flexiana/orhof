(ns orhof.validate-test
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.validate :as val]
            [orhof.optimize :as opt]
            [orhof.core :as c]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; =============================================================
;; Test System: Simple 1D integrator x' = x + d
;; =============================================================

(def test-system
  (val/make-system
   (fn [state disturbance]
     (+ state (first disturbance)))
   0.0))

;; =============================================================
;; Temporal Logic Specification Tests
;; =============================================================

(deftest test-always
  (let [safe? (fn [x] (- 5.0 (Math/abs x)))  ; robustness: positive if |x| < 5
        spec (val/always safe?)]
    (testing "Always-safe trajectory"
      (is (pos? (spec [0.0 1.0 2.0 3.0 4.0]))))
    (testing "Unsafe trajectory"
      (is (neg? (spec [0.0 1.0 2.0 6.0 3.0]))))))

(deftest test-eventually
  (let [at-goal? (fn [x] (- 1.0 (Math/abs (- x 10.0))))  ; positive if near 10
        spec (val/eventually at-goal?)]
    (testing "Eventually reaches goal"
      (is (pos? (spec [0.0 5.0 10.0 5.0]))))
    (testing "Never reaches goal"
      (is (neg? (spec [0.0 1.0 2.0 3.0]))))))

(deftest test-spec-combinators
  (let [safe? (fn [x] (- 5.0 (Math/abs x)))
        fast? (fn [x] (- x 2.0))  ; positive if x > 2
        combined (val/spec-and safe? fast?)]
    (testing "Both satisfied"
      (is (pos? (combined 3.0))))
    (testing "Only one satisfied"
      (is (neg? (combined 1.0))))
    (testing "Neither satisfied"
      (is (neg? (combined 6.0))))))

(deftest test-bounded-temporal
  (let [safe? (fn [x] (- 5.0 (Math/abs x)))
        spec (val/bounded-always safe? 1 3)]
    (testing "Safe in window [1,3]"
      (is (pos? (spec [10.0 1.0 2.0 3.0 10.0]))))
    (testing "Unsafe in window [1,3]"
      (is (neg? (spec [10.0 1.0 6.0 3.0 10.0]))))))

;; =============================================================
;; Rollout Tests
;; =============================================================

(deftest test-rollout
  (let [traj (val/rollout test-system [[1.0] [2.0] [3.0]])]
    (testing "Rollout produces correct trajectory"
      (is (= (count traj) 4))  ; initial + 3 steps
      (is (= (first traj) 0.0))
      (is (= (nth traj 1) 1.0))
      (is (= (nth traj 2) 3.0))
      (is (= (nth traj 3) 6.0)))))

;; =============================================================
;; Falsification Tests
;; =============================================================

(deftest test-random-falsification
  (let [safe? (fn [x] (- 3.0 (Math/abs x)))
        spec (val/always safe?)
        result (val/random-falsification test-system spec 5 1
                                          :n-samples 500 :bounds [-2.0 2.0])]
    (testing "Random falsification finds violation"
      ;; With disturbances up to 2.0 per step over 5 steps,
      ;; it should be able to exceed |x| > 3
      (is (:falsified? result))
      (is (neg? (:robustness result))))))

(deftest test-optimization-falsification
  (let [safe? (fn [x] (- 2.0 (Math/abs x)))  ; |x| < 2
        spec (val/always safe?)
        ;; Use simulated annealing — good for non-smooth objectives
        optimizer (fn [f x0]
                    (opt/simulated-annealing f x0
                      :max-iter 5000
                      :neighbor-fn (fn [x] (mapv #(+ % (* 1.0 (- (rand) 0.5))) x))
                      :temp-schedule (fn [t] (* 10.0 (Math/exp (* -0.001 t))))))
        result (val/falsify test-system spec optimizer 5 1)]
    (testing "Optimization-based falsification finds violation"
      ;; SA returns best-x/best-value, extract properly
      (is (:falsified? result))
      (is (neg? (:robustness result))))))

;; =============================================================
;; Sampling Tests
;; =============================================================

(deftest test-rejection-sampler
  (let [;; Target: standard normal (unnormalized)
        target (fn [x] (Math/exp (- (* 0.5 x x))))
        ;; Proposal: uniform on [-4, 4]
        proposal-sampler (fn [] (- (* 8.0 (rand)) 4.0))
        proposal-pdf (fn [_] (/ 1.0 8.0))
        M (* 8.0 1.0)  ; max of target / proposal
        sampler (val/rejection-sampler target proposal-sampler proposal-pdf M)
        samples (repeatedly 1000 sampler)
        mean (/ (reduce + samples) 1000.0)]
    (testing "Rejection sampler produces approximately normal samples"
      (is (< (Math/abs mean) 0.3)))))

(deftest test-metropolis-hastings
  (let [;; Target: standard normal
        target (fn [x] (Math/exp (- (* 0.5 x x))))
        proposal (fn [x] (+ x (* 0.5 (- (rand) 0.5))))
        sampler (val/metropolis-hastings target proposal 0.0 :burn-in 1000)
        samples (sampler 2000)
        mean (/ (reduce + samples) 2000.0)]
    (testing "MH sampler produces approximately normal samples"
      (is (< (Math/abs mean) 1.0)))))

;; =============================================================
;; Reachability Tests
;; =============================================================

(deftest test-linear-reachability
  (let [A [[0.9 0.0] [0.0 0.9]]  ; stable linear system
        B [[1.0 0.0] [0.0 1.0]]  ; identity input matrix
        w-set (val/interval-set [-0.1 -0.1] [0.1 0.1])
        u-set (val/interval-set [0.0 0.0] [0.0 0.0])
        init-set (val/interval-set [1.0 1.0] [2.0 2.0])
        reach-sets (val/linear-reachability A B w-set u-set init-set 5)]
    (testing "Reachability produces correct number of sets"
      (is (= (count reach-sets) 6)))  ; initial + 5 steps
    (testing "Initial set is first"
      (is (= (:lo (first reach-sets)) [1.0 1.0])))))

;; =============================================================
;; Sensitivity Analysis Tests
;; =============================================================

(deftest test-robustness-sensitivity
  (let [safe? (fn [x] (- 5.0 (Math/abs x)))
        spec (val/always safe?)
        disturbance [0.5 0.5 0.5]
        sensitivity (val/robustness-sensitivity test-system spec disturbance 1)]
    (testing "Sensitivity has correct dimension"
      (is (= (count sensitivity) 3)))
    (testing "Sensitivity values are finite"
      (is (every? #(Double/isFinite %) sensitivity)))))

;; =============================================================
;; Monitor Tests
;; =============================================================

(deftest test-monitor
  (let [safe? (fn [x] (- 5.0 (Math/abs x)))
        spec (val/always safe?)
        monitor (val/make-monitor spec 5)]
    (testing "Monitor starts safe"
      (let [v (monitor 0.0)]
        (is (= (:verdict v) :safe))))
    (testing "Monitor detects violation"
      (monitor 1.0)
      (monitor 2.0)
      (monitor 3.0)
      (let [v (monitor 6.0)]
        (is (= (:verdict v) :unsafe))))))
