(ns orhof.val.sampling-reach-explain-test
  "Tests for sampling, reachability, and explainability methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.val.sampling :as samp]
            [orhof.val.reach :as reach]
            [orhof.val.explain :as expl]
            [orhof.val.spec :as spec]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; --- Sampling ---

(deftest test-gibbs-sampling
  (let [;; 2D target: bivariate normal approx
        ;; Sample x|y ~ N(0.5*y, 0.75), y|x ~ N(0.5*x, 0.75)
        conditionals {:x (fn [state] (+ (* 0.5 (:y state)) (* (Math/sqrt 0.75) (- (rand) 0.5) 2)))
                      :y (fn [state] (+ (* 0.5 (:x state)) (* (Math/sqrt 0.75) (- (rand) 0.5) 2)))}
        samples (samp/gibbs-sampling conditionals {:x 0.0 :y 0.0} 500 :burn-in 100)]
    (testing "Gibbs produces correct count"
      (is (= 500 (count samples))))
    (testing "Samples have expected keys"
      (is (every? #(and (contains? % :x) (contains? % :y)) samples)))))

(deftest test-hmc
  (let [;; Sample from N(0, 1) using HMC
        log-prob (fn [x] (- (* 0.5 (first x) (first x))))
        grad-log-prob (fn [x] [(- (first x))])
        samples (samp/hmc log-prob grad-log-prob [0.0]
                  :n-samples 200 :step-size 0.1 :n-leapfrog 10)]
    (testing "HMC produces samples"
      (is (= 200 (count samples))))
    (testing "Mean is approximately 0"
      (let [mean (/ (reduce + (map first samples)) 200.0)]
        (is (< (Math/abs mean) 1.0))))))

(deftest test-direct-mc-estimation
  (let [result (samp/direct-mc-estimation
                 (fn [] (- (* 4.0 (rand)) 2.0))  ;; sample from U(-2,2)
                 (fn [x] (> x 1.5))               ;; event: x > 1.5
                 5000)]
    (testing "Direct MC estimates probability"
      ;; P(X > 1.5) = 0.5/4 = 0.125 for U(-2,2)
      (is (< (Math/abs (- (:probability result) 0.125)) 0.05)))))

(deftest test-sequential-mc
  (let [;; Propagate particles through simple dynamics
        result (samp/sequential-mc
                 (fn [x] (+ x (* 0.1 (- (rand) 0.5))))  ;; dynamics
                 (fn [x obs] (Math/exp (- (* 2 (Math/abs (- x obs))))))  ;; likelihood
                 (vec (repeatedly 100 #(* 4 (- (rand) 0.5))))  ;; initial particles
                 [1.0 1.0 1.0])]  ;; observations
    (testing "SMC returns particles"
      (is (= 100 (count result))))))

;; --- Reachability ---

(deftest test-zonotope-propagation
  (let [;; Zonotope: center + generators
        z0 (reach/make-zonotope [0.0 0.0] [[1.0 0.0] [0.0 1.0]])
        A  [[0.9 0.1] [0.0 0.9]]
        result (reach/zonotope-propagation A z0 3)]
    (testing "Zonotope propagation returns correct steps"
      (is (= 4 (count result))))  ;; initial + 3 steps
    (testing "Generators grow"
      (is (>= (count (:generators (last result)))
              (count (:generators (first result))))))))

(deftest test-ellipsoid-propagation
  (let [e0 (reach/make-ellipsoid [0.0 0.0] [[1.0 0.0] [0.0 1.0]])
        A  [[0.9 0.0] [0.0 0.9]]
        result (reach/ellipsoid-propagation A e0 3)]
    (testing "Ellipsoid propagation returns correct steps"
      (is (= 4 (count result))))))

(deftest test-bfs-reachability
  (let [;; Simple graph: 1->2->3->4
        result (reach/bfs-reachability
                 1
                 (fn [n] (case n 1 [2] 2 [3] 3 [4] []))
                 #{4})]
    (testing "BFS finds reachable goal"
      (is (:reached? result))
      (is (contains? (:visited result) 4)))))

(deftest test-probabilistic-reachability
  (let [;; 3-state chain: P(1->2)=0.8, P(2->3)=0.9
        result (reach/probabilistic-reachability
                 {1 {2 0.8 1 0.2}
                  2 {3 0.9 2 0.1}
                  3 {3 1.0}}
                 {1 1.0}
                 3)]
    (testing "Probabilistic reachability propagates"
      (is (= 4 (count result)))
      ;; State 3 should have positive probability after 2 steps
      (is (> (get (nth result 2) 3 0.0) 0.5)))))

;; --- Explainability ---

(deftest test-permutation-importance
  (let [;; Model: f(x) = x[0] + 2*x[1]
        model (fn [x] (+ (first x) (* 2 (second x))))
        data [[1.0 2.0] [2.0 3.0] [3.0 4.0] [4.0 5.0]]
        labels [5.0 8.0 11.0 14.0]
        importance (expl/permutation-importance model data labels)]
    (testing "Feature 1 (weight=2) is more important than feature 0 (weight=1)"
      (is (> (second importance) (first importance))))))

(deftest test-counterfactual
  ;; counterfactual uses a random walk toward the desired class (the model is a
  ;; non-differentiable step function), so seed the RNG for a deterministic test.
  (let [rng (java.util.Random. 3)
        model (fn [[x]] (if (> x 0) :positive :negative))
        result (with-redefs [rand (fn ([]  (.nextDouble rng))
                                       ([n] (* n (.nextDouble rng))))]
                 ;; Find counterfactual for x=-0.1 (want :positive, close to boundary)
                 (expl/counterfactual model [-0.1] :positive
                   :max-iter 200 :lr 0.05))]
    (testing "Counterfactual reaches the desired class"
      (is (= :positive (model (:x result))))
      ;; and it moved toward positive territory (right of the start)
      (is (> (first (:x result)) -0.1)))))

(deftest test-conformal-prediction
  (let [;; Simple model: predict x
        model (fn [x] x)
        cal-data (mapv (fn [i] {:x (double i) :y (+ (double i) (* 0.1 (- (rand) 0.5)))})
                       (range 20))
        predictor (expl/conformal-predictor model cal-data :alpha 0.1)]
    (testing "Conformal predictor returns prediction set"
      (let [{:keys [lo hi]} (predictor 5.0)]
        (is (< lo 5.0))
        (is (> hi 5.0))))))
