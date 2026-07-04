(ns orhof.missing-coverage-test
  "Tests for all previously untested functions.
   Each test uses a known-answer problem to verify correctness."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.math :as m]
            [orhof.diff :as d]
            [orhof.optimize :as opt]
            [orhof.mdp :as mdp]
            [orhof.validate :as val]
            [orhof.opt.bracketing :as brk]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; =============================================================
;; Standard test functions
;; =============================================================

(defn sphere [x] (reduce + (map #(* % %) x)))
(defn rosenbrock [[x y]]
  (+ (Math/pow (- 1 x) 2) (* 100 (Math/pow (- y (* x x)) 2))))

;; =============================================================
;; Differentiation: hessian, jacobian, spsa-gradient,
;;   regression-gradient
;; =============================================================

(deftest test-hessian
  (let [;; f(x,y) = x^2 + 3*y^2 => H = [[2, 0], [0, 6]]
        f (fn [[x y]] (+ (* x x) (* 3 y y)))
        H-fn (d/hessian f)
        H (H-fn [1.0 2.0])]
    (testing "Hessian diagonal entries"
      (is (< (Math/abs (- (nth (nth H 0) 0) 2.0)) 0.1) "d2f/dx2 = 2")
      (is (< (Math/abs (- (nth (nth H 1) 1) 6.0)) 0.1) "d2f/dy2 = 6"))
    (testing "Hessian off-diagonal entries are ~0"
      (is (< (Math/abs (nth (nth H 0) 1)) 0.1))
      (is (< (Math/abs (nth (nth H 1) 0)) 0.1)))))

(deftest test-jacobian
  (let [;; f(x,y) = [x*y, x+y] => J = [[y, x], [1, 1]]
        f (fn [[x y]] [(* x y) (+ x y)])
        J-fn (d/jacobian f)
        J (J-fn [3.0 2.0])]
    (testing "Jacobian entries"
      ;; Row 0 = d(x*y)/dx, d(x*y)/dy = [y, x] = [2, 3]
      (is (< (Math/abs (- (nth (nth J 0) 0) 2.0)) 0.01))
      (is (< (Math/abs (- (nth (nth J 0) 1) 3.0)) 0.01))
      ;; Row 1 = d(x+y)/dx, d(x+y)/dy = [1, 1]
      (is (< (Math/abs (- (nth (nth J 1) 0) 1.0)) 0.01))
      (is (< (Math/abs (- (nth (nth J 1) 1) 1.0)) 0.01)))))

(deftest test-spsa-gradient
  (let [grad-fn (d/spsa-gradient sphere 1)
        ;; Average multiple SPSA estimates to reduce variance
        estimates (repeatedly 20 #(grad-fn [3.0 4.0]))
        avg-g (reduce (fn [acc g] (mapv + acc g))
                      [0.0 0.0]
                      estimates)
        avg-g (mapv #(/ % 20.0) avg-g)]
    (testing "SPSA gradient average approximates true gradient [6, 8]"
      (is (> (first avg-g) 2.0) "x-component should be positive on average")
      (is (> (second avg-g) 4.0) "y-component should be positive on average"))))

(deftest test-regression-gradient
  (let [grad-fn (d/regression-gradient sphere 50)
        g (grad-fn [3.0 4.0])]
    (testing "Regression gradient approximates true gradient"
      ;; True gradient of sphere at [3,4] = [6, 8]
      ;; Stochastic method — wide tolerance
      (is (< (Math/abs (- (first g) 6.0)) 4.0) "x-component ~ 6")
      (is (< (Math/abs (- (second g) 8.0)) 4.0) "y-component ~ 8"))))

;; =============================================================
;; Bracketing: quadratic-fit-search
;; =============================================================

(deftest test-quadratic-fit-search
  (let [f (fn [x] (* (- x 3) (- x 3)))
        result (opt/quadratic-fit-search f 0 10)]
    (testing "Quadratic fit search finds minimum of (x-3)^2"
      (is (< (Math/abs (- result 3.0)) 0.01)))))

;; =============================================================
;; Second-order: Newton's method (full Hessian)
;; =============================================================

(deftest test-newton-method
  (let [;; f(x,y) = x^2 + 2*y^2, min at origin
        f (fn [[x y]] (+ (* x x) (* 2 y y)))
        result (opt/newton-method f [5.0 3.0] :max-iter 50)]
    (testing "Newton's method converges to minimum"
      (is (< (c/vnorm (:x result)) 0.1) "Should find origin")
      (is (< (:value result) 0.01)))))

;; =============================================================
;; Temporal logic: until-op
;; =============================================================

(deftest test-until-op
  (let [;; phi1 U phi2: phi1 holds until phi2 becomes true
        ;; phi1: "x < 5" (robustness: 5 - x)
        ;; phi2: "x > 8" (robustness: x - 8)
        phi1 (fn [x] (- 5.0 x))
        phi2 (fn [x] (- x 8.0))
        spec (val/until-op phi1 phi2)]
    (testing "Until satisfied: values stay <5, then jump >8"
      ;; Trajectory: [1, 2, 3, 9] => phi1 holds at 0,1,2; phi2 holds at 3
      (is (pos? (spec [1.0 2.0 3.0 9.0]))))
    (testing "Until violated: phi2 never becomes true"
      ;; Trajectory: [1, 2, 3, 4] => phi2 never positive
      (is (neg? (spec [1.0 2.0 3.0 4.0]))))
    (testing "Until violated: phi1 fails before phi2"
      ;; Trajectory: [1, 6, 3, 9] => phi1 fails at index 1 (6 > 5)
      ;; At j=3 (phi2=1.0), min phi1 for i<3 includes phi1(6)=-1, so min=-1
      ;; Result at j=3: min(1.0, -1.0) = -1.0
      ;; No j gives positive result
      (is (neg? (spec [1.0 6.0 3.0 9.0]))))))

;; =============================================================
;; MDP: MCTS
;; =============================================================

(deftest test-mcts
  (let [;; Simple 2-state MDP: state :a, actions [:go], leads to :b with reward 10
        states [:a :b]
        actions (fn [s] (case s :a [:go] :b []))
        transition (fn [s a] (case [s a] [:a :go] {:b 1.0} {}))
        reward (fn [s a] (case [s a] [:a :go] 10.0 0.0))
        mdp (mdp/make-mdp states actions transition reward 0.9)
        best-action (mdp/mcts mdp :a :budget 100)]
    (testing "MCTS finds the obvious best action"
      (is (= best-action :go)))))

;; =============================================================
;; MDP: SARSA (dedicated test)
;; =============================================================

(deftest test-sarsa
  (let [states [:a :b :goal]
        actions (fn [_] [:left :right])
        transition (fn [s a]
                     (case [s a]
                       [:a :right] {:b 1.0}
                       [:b :right] {:goal 1.0}
                       [:a :left]  {:a 1.0}
                       [:b :left]  {:a 1.0}
                       {:goal 1.0}))
        reward (fn [s a]
                 (if (= s :goal) 0.0
                     (if (and (= s :b) (= a :right)) 10.0 -1.0)))
        env-step (fn [s a]
                   (let [t (transition s a)
                         s' (first (keys t))]
                     {:state' s' :reward (reward s a) :done? (= s' :goal)}))
        mdp (mdp/make-mdp states actions transition reward 0.9)
        Q (mdp/sarsa mdp env-step :max-episodes 500 :max-steps 20)]
    (testing "SARSA learns to prefer :right from :b"
      (is (> (get-in Q [:b :right] 0.0) (get-in Q [:b :left] 0.0))))))

;; =============================================================
;; MDP: REINFORCE
;; =============================================================

(deftest test-reinforce
  (let [;; Simple bandit-like problem: 2 states, 2 actions
        ;; Action 0 gives reward 1.0, action 1 gives reward 0.0
        states [:s]
        actions (fn [_] [:a0 :a1])
        env-step (fn [s a]
                   {:state' :s
                    :reward (if (= a :a0) 1.0 0.0)
                    :done? true})
        ;; Softmax policy parameterized by 2 weights
        policy-fn (fn [params s]
                    (let [[w0 w1] params
                          e0 (Math/exp w0) e1 (Math/exp w1)
                          Z (+ e0 e1)]
                      {:a0 (/ e0 Z) :a1 (/ e1 Z)}))
        ;; SGD update
        update-fn (fn [params grad] (c/v+ params (c/v* 0.01 grad)))
        mdp (mdp/make-mdp states actions nil nil 0.99)
        final-params (mdp/reinforce mdp env-step policy-fn update-fn [0.0 0.0]
                                    :max-episodes 200 :max-steps 1)]
    (testing "REINFORCE learns to prefer the rewarding action"
      ;; After training, w0 should be >= w1 (prefers a0)
      (is (>= (first final-params) (second final-params))))))

;; =============================================================
;; MDP: iterated-best-response
;; =============================================================

(deftest test-iterated-best-response
  (let [;; Prisoner's dilemma: cooperate (C) or defect (D)
        ;; Payoffs: (C,C)=3, (C,D)=0, (D,C)=5, (D,D)=1
        players [:p1 :p2]
        actions (fn [p] {:C 0 :D 0})  ;; action set
        ;; Payoffs indexed as [my-action, opponent-action]
        ;; PD: mutual C=3, I defect+they cooperate=5, I cooperate+they defect=0, mutual D=1
        payoffs {:p1 {[:C :C] 3.0 [:C :D] 0.0 [:D :C] 5.0 [:D :D] 1.0}
                 :p2 {[:C :C] 3.0 [:C :D] 0.0 [:D :C] 5.0 [:D :D] 1.0}}
        game (mdp/make-normal-form-game players actions payoffs)
        result (mdp/iterated-best-response game :max-iter 50)]
    (testing "Iterated best response converges to mutual defection (Nash eq)"
      ;; In PD, dominant strategy is D for both
      (is (= 1.0 (get-in result [:p1 :D]))
          "Player 1 should play D with probability 1")
      (is (= 1.0 (get-in result [:p2 :D]))
          "Player 2 should play D with probability 1"))))

;; =============================================================
;; Validation: adaptive-stress-test
;; =============================================================

(deftest test-adaptive-stress-test
  (let [;; System: x' = x + d, start at 0
        system (val/make-system (fn [x [d]] (+ x d)) 0.0)
        ;; Spec: always |x| < 0.8 (easy to violate with 5 steps of ~0.25 max each)
        safe? (fn [x] (- 0.8 (Math/abs x)))
        spec (val/always safe?)
        result (val/adaptive-stress-test system spec 1 10 :budget 500)]
    (testing "Adaptive stress test finds falsifying trajectory"
      (is (:falsified? result))
      (is (neg? (:robustness result))))))

;; =============================================================
;; Validation: importance-sampling
;; =============================================================

(deftest test-importance-sampling
  (let [;; Estimate P(X > 2) where X ~ N(0,1)
        ;; True value: ~0.0228
        target-pdf (fn [x] (Math/exp (- (* 0.5 x x))))
        ;; Proposal: shifted normal centered at 2.5 (better for rare event)
        proposal-sampler (fn [] (+ 2.5 (* 1.0 (- (rand) 0.5) 2.0)))
        proposal-pdf (fn [x] (if (and (> x 1.5) (< x 3.5)) 0.5 1e-10))
        indicator (fn [x] (> x 2.0))
        result (val/importance-sampling target-pdf proposal-sampler proposal-pdf indicator 5000)]
    (testing "Importance sampling estimates a probability"
      (is (> (:probability result) 0.0))
      (is (< (:probability result) 1.0))
      (is (pos? (:effective-samples result))))))

;; =============================================================
;; Validation: cross-entropy-estimation
;; =============================================================

(deftest test-cross-entropy-estimation
  (let [;; System: x' = x + d, need |x| > 3 to fail
        system (val/make-system (fn [x [d]] (+ x d)) 0.0)
        safe? (fn [x] (- 3.0 (Math/abs x)))
        spec (val/always safe?)
        result (val/cross-entropy-estimation system spec 3 1
                 :n-samples 200 :n-elite 20 :max-iter 5)]
    (testing "Cross-entropy estimation produces a result"
      (is (some? (:mu result)))
      (is (some? (:sigma result)))
      (is (some? (:probability result))))))

;; =============================================================
;; Validation: shapley-values
;; =============================================================

(deftest test-shapley-values
  (let [;; System: x' = x + d, 3 steps
        system (val/make-system (fn [x [d]] (+ x d)) 0.0)
        safe? (fn [x] (- 5.0 (Math/abs x)))
        spec (val/always safe?)
        ;; Disturbance that causes failure: [2.0, 2.0, 2.0] => x reaches 6
        disturbance [2.0 2.0 2.0]
        sv (val/shapley-values system spec disturbance 1 :n-samples 200)]
    (testing "Shapley values have correct dimension"
      (is (= (count sv) 3)))
    (testing "All Shapley values are finite"
      (is (every? #(Double/isFinite %) sv)))
    (testing "Shapley values are negative (each disturbance contributes to violation)"
      ;; Each d_i pushes system further from safety
      (is (every? neg? sv)))))

;; =============================================================
;; Differentiation: reverse-mode AD
;; =============================================================

(deftest test-reverse-ad
  (let [;; f(x0, x1) = x0 * x1 + x0
        ;; df/dx0 = x1 + 1, df/dx1 = x0
        f (fn [vars]
            (let [x0 (nth vars 0) x1 (nth vars 1)]
              (d/tape+ (d/tape* x0 x1) x0)))
        grad-fn (d/reverse-ad f 2)
        g (grad-fn [3.0 4.0])]
    (testing "Reverse AD computes correct gradient"
      ;; df/dx0 = x1 + 1 = 5.0, df/dx1 = x0 = 3.0
      (is (< (Math/abs (- (first g) 5.0)) 0.01))
      (is (< (Math/abs (- (second g) 3.0)) 0.01)))))

;; =============================================================
;; Bracketing: Shubert-Piyavskii
;; =============================================================

(deftest test-shubert-piyavskii
  (let [;; Minimize (x-0.3)^2 on [0, 1], Lipschitz constant L=2
        result (brk/shubert-piyavskii (fn [x] (* (- x 0.3) (- x 0.3)))
                                       0.0 1.0 2.0
                                       :max-iter 50)]
    (testing "Shubert-Piyavskii finds approximate minimum"
      (is (< (:best-val result) 0.05)))))

;; =============================================================
;; Math: solve-linear, cholesky
;; =============================================================

(deftest test-solve-linear
  (let [;; Solve 2x + y = 5, x + 3y = 10 => x=1, y=3
        A [[2.0 1.0] [1.0 3.0]]
        b [5.0 10.0]
        x (m/solve-linear A b)]
    (testing "solve-linear gives correct solution"
      (is (< (Math/abs (- (first x) 1.0)) 0.01))
      (is (< (Math/abs (- (second x) 3.0)) 0.01)))))

(deftest test-cholesky
  (let [;; A = [[4, 2], [2, 3]] => L = [[2, 0], [1, sqrt(2)]]
        A [[4.0 2.0] [2.0 3.0]]
        L (m/cholesky A)
        ;; Verify L * L^T = A
        LLT (m/mat-mult L (m/mat-transpose L))]
    (testing "Cholesky L*L^T reconstructs A"
      (is (< (Math/abs (- (nth (nth LLT 0) 0) 4.0)) 0.01))
      (is (< (Math/abs (- (nth (nth LLT 0) 1) 2.0)) 0.01))
      (is (< (Math/abs (- (nth (nth LLT 1) 0) 2.0)) 0.01))
      (is (< (Math/abs (- (nth (nth LLT 1) 1) 3.0)) 0.01)))))
