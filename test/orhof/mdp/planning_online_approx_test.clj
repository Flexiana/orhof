(ns orhof.mdp.planning-online-approx-test
  "Tests for planning extensions, online methods, and approximate VFs."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.mdp.planning :as plan]
            [orhof.mdp.online :as online]
            [orhof.mdp.approx :as approx]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; Reuse the grid-world MDP from mdp_test
(def grid-states (for [r (range 3) c (range 3)] [r c]))
(def grid-actions (fn [_] [:up :down :left :right]))
(defn grid-transition [s a]
  (let [[r c] s
        [r' c'] (case a :up [(max 0 (dec r)) c] :down [(min 2 (inc r)) c]
                     :left [r (max 0 (dec c))] :right [r (min 2 (inc c))])]
    {[r' c'] 1.0}))
(defn grid-reward [s a] (if (= s [2 2]) 0.0 -1.0))
(def test-mdp (plan/make-mdp grid-states grid-actions grid-transition grid-reward 0.9))

;; --- Planning extensions ---

(deftest test-async-value-iteration
  (let [result (plan/async-value-iteration test-mdp :max-iter 200)]
    (testing "Async VI converges"
      (is (:converged result))
      (is (>= (get (:V result) [2 2]) (get (:V result) [0 0]))))))

;; --- Online methods ---

(deftest test-forward-search
  (let [action (online/forward-search test-mdp [0 0] :depth 3)]
    (testing "Forward search returns an action"
      (is (contains? #{:up :down :left :right} action)))))

(deftest test-sparse-sampling
  (let [action (online/sparse-sampling test-mdp [0 0] :depth 2 :n-samples 10)]
    (testing "Sparse sampling returns an action"
      (is (contains? #{:up :down :left :right} action)))))

(deftest test-rollout-heuristic
  (let [action (online/rollout-with-heuristic test-mdp [0 0]
                 (fn [s] [:down :right]) ;; simple heuristic policy
                 :n-rollouts 20 :horizon 10)]
    (testing "Rollout returns an action"
      (is (contains? #{:up :down :left :right} action)))))

(deftest test-a-star
  (let [;; Simple graph: :a -> :b -> :goal
        result (online/a-star
                 :a
                 (fn [n] (case n :a [:b] :b [:goal] []))
                 (fn [_ _] 1.0)  ;; cost
                 (fn [n] (case n :a 2 :b 1 :goal 0 10))  ;; heuristic
                 (fn [n] (= n :goal)))]
    (testing "A* finds shortest path"
      (is (= :goal (:node result)))
      (is (= 2.0 (:cost result))))))

;; --- Approximate value functions ---

(deftest test-nearest-neighbor-vf
  (let [data {[0 0] 1.0 [1 1] 5.0 [2 2] 10.0}
        vf (approx/nearest-neighbor-vf data)]
    (testing "kNN VF returns closest value"
      (is (= 1.0 (vf [0 0])))
      (is (= 10.0 (vf [2 2]))))))

(deftest test-linear-regression-vf
  (let [;; f(s) = 2*s[0] + 3*s[1]
        feature-fn (fn [s] [(double (first s)) (double (second s)) 1.0])
        data [[[0 0] 0.0] [[1 0] 2.0] [[0 1] 3.0] [[1 1] 5.0]]
        vf (approx/linear-regression-vf feature-fn data)]
    (testing "Linear VF approximates known function"
      (is (< (Math/abs (- (vf [2 1]) 7.0)) 1.0)))))
