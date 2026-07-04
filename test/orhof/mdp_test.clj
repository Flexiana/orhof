(ns orhof.mdp-test
  (:require [clojure.test :refer :all]
            [orhof.mdp :as mdp]
            [orhof.core :as c]
            [orhof.test-util :as tu]))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for bandits/sampling

;; =============================================================
;; Test MDP: Simple Grid World
;; 3x3 grid, goal at (2,2), reward -1 per step, +10 at goal
;; =============================================================

(def grid-states
  (for [r (range 3) c (range 3)] [r c]))

(def grid-actions
  (fn [_] [:up :down :left :right]))

(defn grid-transition [s a]
  (let [[r c] s
        [r' c'] (case a
                  :up    [(max 0 (dec r)) c]
                  :down  [(min 2 (inc r)) c]
                  :left  [r (max 0 (dec c))]
                  :right [r (min 2 (inc c))])]
    {[r' c'] 1.0}))

(defn grid-reward [s a]
  (if (= s [2 2]) 0.0 -1.0))

(def test-mdp
  (mdp/make-mdp grid-states grid-actions grid-transition grid-reward 0.9))

;; --- Value Iteration Tests ---

(deftest test-value-iteration
  (let [result (mdp/value-iteration test-mdp :max-iter 100)
        V (:V result)
        policy (:policy result)]
    (testing "Value iteration converges"
      (is (:converged result)))
    (testing "Goal state has highest value"
      (is (>= (get V [2 2]) (get V [0 0]))))
    (testing "Policy at (0,0) moves toward goal"
      (let [a (policy [0 0])]
        (is (contains? #{:down :right} a))))))

;; --- Policy Iteration Tests ---

(deftest test-policy-iteration
  (let [result (mdp/policy-iteration test-mdp :max-iter 50)
        V (:V result)
        policy (:policy result)]
    (testing "Policy iteration converges"
      (is (:converged result)))
    (testing "Goal state has highest value"
      (is (>= (get V [2 2]) (get V [0 0]))))
    (testing "Policy at (0,0) moves toward goal"
      (let [a (policy [0 0])]
        (is (contains? #{:down :right} a))))))

;; --- Bellman Operator Tests ---

(deftest test-bellman-operator
  (let [T (mdp/bellman-operator test-mdp)
        V0 (fn [_] 0.0)
        V1 (T V0)]
    (testing "One step of Bellman gives immediate rewards"
      (is (< (V1 [0 0]) 0.0))
      (is (= (V1 [2 2]) 0.0)))))

;; --- Greedy Policy Tests ---

(deftest test-greedy-policy
  (let [result (mdp/value-iteration test-mdp)
        V-fn (fn [s] (get (:V result) s 0.0))
        policy (mdp/greedy-policy test-mdp V-fn)]
    (testing "Greedy policy extracts correct actions"
      (is (some? (policy [0 0])))
      (is (contains? #{:up :down :left :right} (policy [1 1]))))))

;; --- Bandit Tests ---

(deftest test-bandits
  (let [arms [(fn [] (if (< (rand) 0.3) 1.0 0.0))   ; 30% win
              (fn [] (if (< (rand) 0.7) 1.0 0.0))   ; 70% win
              (fn [] (if (< (rand) 0.5) 1.0 0.0))]  ; 50% win
        bandit (mdp/make-bandit arms)]

    (testing "Epsilon-greedy identifies best arm"
      (let [result (mdp/epsilon-greedy-bandit bandit :n-rounds 2000)]
        ;; Best arm (index 1) should have most pulls
        (is (> (nth (:counts result) 1) (nth (:counts result) 0)))))

    (testing "UCB1 identifies best arm"
      (let [result (mdp/ucb1-bandit bandit :n-rounds 2000)]
        (is (> (nth (:counts result) 1) (nth (:counts result) 0)))))

    (testing "Thompson sampling identifies best arm"
      (let [result (mdp/thompson-sampling bandit :n-rounds 2000)]
        ;; Alpha for best arm should be highest
        (is (> (nth (:alphas result) 1) (nth (:alphas result) 0)))))))

;; --- Discrete Filter Tests ---

(deftest test-discrete-filter
  (let [states [:a :b :c]
        transition (fn [s a]
                     (case s
                       :a {:a 0.1 :b 0.9}
                       :b {:b 0.3 :c 0.7}
                       :c {:c 1.0}))
        observation (fn [s obs]
                      (case [s obs]
                        [:a :x] 0.9
                        [:a :y] 0.1
                        [:b :x] 0.2
                        [:b :y] 0.8
                        [:c :x] 0.1
                        [:c :y] 0.9
                        0.5))
        updater (mdp/discrete-filter transition observation)
        init-belief {:a 0.5 :b 0.3 :c 0.2}
        belief' (updater init-belief :move :y)]
    (testing "Belief update produces valid distribution"
      (let [total (reduce + (vals belief'))]
        (is (< (Math/abs (- total 1.0)) 0.01))))))
