(ns orhof.val.spec-falsify-test
  "Tests for additional specification and falsification methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.val.spec :as spec]
            [orhof.val.falsify :as fal]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

(def test-system
  (spec/make-system (fn [x [d]] (+ x d)) 0.0))

;; --- Spec extensions ---

(deftest test-reachability-spec
  (let [safe-set {:lo -5.0 :hi 5.0}
        rspec (spec/reachability-spec safe-set)]
    (testing "Trajectory within safe set is satisfied"
      (is (pos? (rspec [0.0 1.0 2.0 3.0]))))
    (testing "Trajectory leaving safe set is violated"
      (is (neg? (rspec [0.0 1.0 6.0 3.0]))))))

(deftest test-cross-validation
  (let [;; Simple model: predict mean of training data
        fit-fn    (fn [data] (let [m (/ (reduce + data) (count data))]
                               (fn [x] m)))
        eval-fn   (fn [model data] (reduce + (map (fn [x] (Math/abs (- (model x) x))) data)))
        data      [1.0 2.0 3.0 4.0 5.0]
        result    (spec/cross-validation fit-fn eval-fn data :k 5)]
    (testing "Cross-validation returns scores"
      (is (= 5 (count (:scores result))))
      (is (every? number? (:scores result))))))

;; --- Falsification extensions ---

(deftest test-disturbance-falsification
  (let [safe? (fn [x] (- 2.0 (Math/abs x)))
        s (spec/always safe?)
        result (fal/disturbance-falsification test-system s 5 1
                 :sampler (fn [] [(* 1.5 (- (rand) 0.5))])
                 :n-samples 500)]
    (testing "Disturbance-based falsification finds violation"
      (is (:falsified? result)))))

(deftest test-fuzzing
  (let [safe? (fn [x] (- 2.0 (Math/abs x)))
        s (spec/always safe?)
        result (fal/fuzzing test-system s [0.0 0.0 0.0 0.0 0.0] 1
                 :mutate-fn (fn [d] (mapv #(+ % (* 0.5 (- (rand) 0.5))) d))
                 :max-iter 500)]
    (testing "Fuzzing finds violation"
      (is (:falsified? result)))))

(deftest test-shooting-method
  (let [safe? (fn [x] (- 2.0 (Math/abs x)))
        s (spec/always safe?)
        result (fal/shooting-method test-system s 5 1 :n-samples 200)]
    (testing "Shooting method evaluates candidate sequences"
      (is (some? (:robustness result))))))

(deftest test-rl-falsification
  (let [safe? (fn [x] (- 3.0 (Math/abs x)))
        s (spec/always safe?)
        result (fal/rl-falsification test-system s 5 1
                 :max-episodes 100 :max-steps 5)]
    (testing "RL falsification returns a result"
      (is (some? (:robustness result))))))
