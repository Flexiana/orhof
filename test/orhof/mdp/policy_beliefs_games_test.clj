(ns orhof.mdp.policy-beliefs-games-test
  "Tests for policy search, belief state, and game theory methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.mdp.policy :as pol]
            [orhof.mdp.beliefs :as bel]
            [orhof.mdp.games :as games]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; --- Policy Search ---

(deftest test-evolution-strategies
  (let [;; Optimize a simple policy: f(theta) = -sum(theta^2)
        ;; Best theta = [0, 0]
        result (pol/evolution-strategies
                 (fn [theta] (- (reduce + (map #(* % %) theta))))
                 [5.0 5.0]
                 :max-iter 200 :n-samples 50 :lr 0.05)]
    (testing "ES finds good parameters"
      (is (< (c/vnorm (:x result)) 4.0)))))

(deftest test-reward-to-go
  (let [;; Compute returns with reward-to-go (future rewards only)
        rewards [1.0 2.0 3.0 4.0]
        returns (pol/reward-to-go rewards 0.99)]
    (testing "Reward-to-go computes discounted future returns"
      (is (= 4 (count returns)))
      ;; Last return = last reward
      (is (< (Math/abs (- (last returns) 4.0)) 0.01))
      ;; First return > last (includes all future)
      (is (> (first returns) (last returns))))))

(deftest test-gae
  (let [;; GAE: exponentially-weighted advantages
        rewards [1.0 1.0 1.0]
        values  [5.0 4.0 3.0 0.0]  ;; V(s0)..V(s3), s3=terminal
        advantages (pol/gae rewards values 0.99 0.95)]
    (testing "GAE produces correct-length advantage vector"
      (is (= 3 (count advantages)))
      (is (every? #(Double/isFinite %) advantages)))))

;; --- Beliefs ---

(deftest test-kalman-filter
  (let [;; 1D constant velocity: x=[position, velocity], observe position
        A [[1.0 1.0] [0.0 1.0]]   ;; state transition
        C [[1.0 0.0]]              ;; observation (position only)
        Q [[0.1 0.0] [0.0 0.1]]   ;; process noise
        R [[1.0]]                  ;; measurement noise
        kf (bel/kalman-filter A C Q R)
        ;; Initial state
        init {:mean [0.0 0.0] :cov [[10.0 0.0] [0.0 10.0]]}
        ;; Observe position=5 three times
        result (reduce (fn [state obs]
                         (kf state obs))
                       init
                       [[5.0] [5.0] [5.0]])]
    (testing "Kalman filter converges toward observed position"
      (is (> (first (:mean result)) 3.0)))))

(deftest test-particle-filter
  (let [;; Simple 1D: state is position, noisy observation
        dynamics  (fn [x] (+ x (* 0.1 (- (rand) 0.5))))
        observe   (fn [x] (+ x (* 0.5 (- (rand) 0.5))))
        obs-lik   (fn [x obs] (Math/exp (- (* 2 (Math/abs (- x obs))))))
        pf        (bel/particle-filter dynamics obs-lik 200)
        ;; Run 5 steps observing position ~3.0
        result    (reduce (fn [particles _]
                            (pf particles 3.0))
                          (vec (repeatedly 200 #(* 10.0 (rand))))
                          (range 5))
        mean      (/ (reduce + result) (count result))]
    (testing "Particle filter concentrates near observation"
      (is (< (Math/abs (- mean 3.0)) 2.0)))))

;; --- Games ---

(deftest test-fictitious-play
  (let [;; Matching pennies: no pure NE, should oscillate
        game (games/make-normal-form-game
               [:p1 :p2]
               (fn [p] {:H 0 :T 0})
               {:p1 {[:H :H] 1.0 [:H :T] -1.0 [:T :H] -1.0 [:T :T] 1.0}
                :p2 {[:H :H] -1.0 [:H :T] 1.0 [:T :H] 1.0 [:T :T] -1.0}})
        result (games/fictitious-play game :max-iter 100)]
    (testing "Fictitious play produces mixed strategies"
      ;; Should converge toward 50/50
      (is (< (Math/abs (- (get-in result [:p1 :H]) 0.5)) 0.3)))))

(deftest test-nash-equilibrium-support-enum
  (let [;; Prisoner's dilemma — D is dominant
        game (games/make-normal-form-game
               [:p1 :p2]
               (fn [_] {:C 0 :D 0})
               {:p1 {[:C :C] 3.0 [:C :D] 0.0 [:D :C] 5.0 [:D :D] 1.0}
                :p2 {[:C :C] 3.0 [:C :D] 0.0 [:D :C] 5.0 [:D :D] 1.0}})
        result (games/nash-support-enum game)]
    (testing "Support enum finds Nash equilibrium"
      (is (some? result))
      (is (= 1.0 (get-in result [:p1 :D]))))))
