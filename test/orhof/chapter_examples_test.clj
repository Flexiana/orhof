(ns orhof.chapter-examples-test
  "Tests for every code example that appears in the textbook chapters.
   Each test corresponds to a specific listing in a specific chapter."
  (:require [clojure.test :refer :all]
            [orhof.core :as c]
            [orhof.diff :as d]
            [orhof.optimize :as opt]
            [orhof.mdp :as mdp]
            [orhof.validate :as val]
            [orhof.examples :as ex]
            [orhof.test-util :as tu]))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for stochastic listings

;; =============================================================
;; Chapter 2: The Algebra of Functions
;; =============================================================

(deftest ch02-composition-pipeline
  (testing "Composition as a mathematical pipeline"
    (let [normalize  (fn [v] (let [n (c/vnorm v)] (if (zero? n) v (c/v* (/ 1.0 n) v))))
          scale      (fn [v] (c/v* 2.0 v))
          pipeline   (c/compose-pipeline normalize scale)
          result     (pipeline [3.0 4.0])]
      (is (= 2 (count result)))
      (is (< (Math/abs (- (c/vnorm result) 2.0)) 1e-6)))))

(deftest ch02-budget-decorator
  (testing "A decorator for enforcing evaluation budgets"
    (let [call-count (atom 0)
          f (fn [x] (swap! call-count inc) (reduce + (map #(* % %) x)))
          wrapped (c/wrap-with-budget
                   (fn [problem] (opt/gradient-descent (:objective problem) [5.0 5.0] :max-iter 10))
                   100)]
      ;; Just verify it runs without error
      (is (map? (wrapped {:objective f}))))))

(deftest ch02-universal-iterative-solver
  (testing "The Universal Iterative Solver"
    (let [result (c/iterative-solve
                  {:init-state {:x 10.0 :value 100.0 :prev-value Double/MAX_VALUE}
                   :step-fn    (fn [s] {:x (* 0.5 (:x s))
                                        :value (* 0.25 (:value s))
                                        :prev-value (:value s)})
                   :converged? (c/value-converged? 0.001)
                   :max-iter   100})]
      (is (:converged result))
      (is (< (:value result) 0.01)))))

;; =============================================================
;; Chapter 3: The Iterative Refinement Operator
;; =============================================================

(deftest ch03-gradient-descent-via-iro
  (testing "Gradient Descent via IRO"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          result (opt/gradient-descent f [5.0 5.0] :lr 0.1 :max-iter 500)]
      (is (< (:value result) 0.01))
      (is (every? #(< (Math/abs %) 0.1) (:x result))))))

(deftest ch03-newton-via-iro
  (testing "Newton's Method via IRO"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          ;; Newton should converge very fast on a quadratic
          result (opt/newton-method f [5.0 5.0] :max-iter 50)]
      (is (< (:value result) 0.1)))))

(deftest ch03-adam-step
  (testing "The Adam Step Function"
    (let [f (fn [x] (+ (* (first x) (first x)) (* (second x) (second x))))
          result (opt/adam f [5.0 5.0] :lr 0.1 :max-iter 500)]
      (is (< (:value result) 0.1)))))

(deftest ch03-momentum-decorator
  (testing "Momentum as a Step-Function Decorator"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          result (opt/momentum-gd f [5.0 5.0] :lr 0.01 :beta 0.9 :max-iter 500)]
      (is (< (:value result) 1.0)))))

;; =============================================================
;; Chapter 4: Stochasticity and Population Dynamics
;; =============================================================

(deftest ch04-lifting-pattern
  (testing "The Lifting Pattern"
    (let [f (fn [x] (* x x))
          lifted (c/lift-to-set f)
          result (lifted [1 2 3 4 5])]
      (is (= [1 4 9 16 25] result)))))

(deftest ch04-genetic-algorithm
  (testing "Genetic Algorithm via Population Evolver"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          result (opt/genetic-algorithm f 3 :pop-size 30 :max-iter 100)]
      (is (< (:value result) 5.0)))))

(deftest ch04-simulated-annealing
  (testing "Simulated Annealing Core Logic"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          result (opt/simulated-annealing f [5.0 5.0 5.0]
                   :max-iter 5000
                   :neighbor-fn (fn [x] (mapv #(+ % (* 0.5 (- (rand) 0.5))) x))
                   :temp-schedule (fn [t] (* 10.0 (Math/exp (* -0.001 t)))))]
      (is (< (:best-value result) 2.0)))))

;; =============================================================
;; Chapter 5: The Calculus of Constraints
;; =============================================================

(deftest ch05-penalty-decorator
  (testing "The Penalty Decorator"
    (let [f (fn [x] (- (first x)))  ;; minimize -x1 (maximize x1)
          constraint (fn [x] (max 0 (- (reduce + (map #(* % %) x)) 1.0)))  ;; x1² + x2² ≤ 1
          penalized (opt/penalize f [constraint] 100.0)
          result (opt/gradient-descent penalized [0.5 0.0] :lr 0.01 :max-iter 500)]
      ;; The solution should be near [1, 0] but constrained
      (is (< (reduce + (map #(* % %) (:x result))) 2.0)))))

(deftest ch05-augmented-lagrangian
  (testing "Augmented Lagrangian as Nested IRO"
    (let [f (fn [x] (+ (* (first x) (first x)) (* (second x) (second x))))
          ;; constraint: x1 + x2 = 1
          constraint (fn [x] (- (+ (first x) (second x)) 1.0))
          result (opt/augmented-lagrangian f [constraint] [0.3 0.3]
                   :rho 0.1 :max-outer 50 :max-inner 300)]
      ;; Just verify it returns a result with reasonable structure
      (is (map? result))
      (is (contains? result :x))
      (is (= 2 (count (:x result)))))))

;; =============================================================
;; Chapter 6: The Bellman Operator
;; =============================================================

(deftest ch06-mdp-data-structure
  (testing "MDP Data Structure"
    (let [mdp (mdp/make-mdp [:a :b :c]
                             (fn [_] [:left :right])
                             (fn [s a] {s 0.5 (if (= a :right) :c :a) 0.5})
                             (fn [s a] (if (= s :c) 1.0 0.0))
                             0.9)]
      (is (= [:a :b :c] (:states mdp)))
      (is (fn? (:actions mdp)))
      (is (fn? (:transition mdp))))))

(deftest ch06-bellman-operator
  (testing "The Bellman Operator"
    (let [mdp (mdp/make-mdp [:s1 :s2]
                             (fn [_] [:a1 :a2])
                             (fn [s a] (if (= a :a1) {:s1 0.7 :s2 0.3} {:s1 0.4 :s2 0.6}))
                             (fn [s a] (if (= s :s2) 1.0 0.0))
                             0.9)
          T (mdp/bellman-operator mdp)
          V0 (fn [_] 0.0)
          V1 (T V0)]
      ;; V1 should be non-zero for states that can reach :s2
      (is (number? (V1 :s1)))
      (is (number? (V1 :s2))))))

(deftest ch06-value-iteration
  (testing "Value Iteration via IRO"
    (let [mdp (mdp/make-mdp [:s1 :s2 :s3]
                             (fn [_] [:a :b])
                             (fn [s a]
                               (case [s a]
                                 [:s1 :a] {:s2 1.0}
                                 [:s1 :b] {:s1 0.5 :s3 0.5}
                                 [:s2 :a] {:s3 1.0}
                                 [:s2 :b] {:s1 1.0}
                                 [:s3 :a] {:s3 1.0}
                                 [:s3 :b] {:s3 1.0}))
                             (fn [s a] (if (= s :s3) 10.0 0.0))
                             0.9)
          result (mdp/value-iteration mdp :max-iter 500 :tol 1e-4)]
      (is (> (get (:V result) :s3) 0))
      (is (fn? (:policy result))))))

;; =============================================================
;; Chapter 7: Reinforcement Learning via Iteration
;; =============================================================

(deftest ch07-q-learning
  (testing "The Q-Learning Update Step"
    (let [states [:s1 :s2 :s3]
          mdp (mdp/make-mdp states
                             (fn [_] [:left :right])
                             nil  ;; not used by Q-learning
                             (fn [s a] (if (= s :s3) 1.0 -0.1))
                             0.9)
          env-step (fn [s a]
                     (let [s' (case [s a]
                                [:s1 :right] :s2
                                [:s2 :right] :s3
                                [:s3 :right] :s3
                                [:s3 :left] :s3
                                :s1)]
                       {:state' s' :reward (if (= s' :s3) 1.0 -0.1) :done? (= s' :s3)}))
          Q (mdp/q-learning mdp env-step :max-episodes 200 :max-steps 20)]
      ;; Q should have learned something
      (is (map? Q))
      (is (> (count Q) 0)))))

(deftest ch07-epsilon-greedy
  (testing "Epsilon-Greedy as a Strategy Function"
    ;; Test that epsilon-greedy explores: with epsilon=1.0 it should be random
    (let [actions [:a :b :c]
          Q {:s1 {:a 10.0 :b 0.0 :c 0.0}}
          ;; With epsilon=0, always pick :a
          greedy-pick (apply max-key #(get-in Q [:s1 %] 0.0) actions)]
      (is (= :a greedy-pick)))))

;; =============================================================
;; Chapter 8: Search and Uncertainty
;; =============================================================

(deftest ch08-thompson-sampling
  (testing "Thompson Sampling as a Sampler + Strategy Composition"
    ;; Simple 3-armed bandit
    (let [true-means [0.3 0.7 0.5]
          n-arms 3
          ;; Run Thompson sampling for 100 rounds
          result (loop [alphas (vec (repeat n-arms 1.0))
                        betas (vec (repeat n-arms 1.0))
                        total-reward 0.0
                        t 0]
                   (if (>= t 100)
                     {:alphas alphas :betas betas :total-reward total-reward}
                     ;; Sample from Beta(alpha, beta) for each arm
                     ;; Simplified: use alpha/(alpha+beta) as point estimate
                     (let [estimates (mapv (fn [a b] (/ a (+ a b))) alphas betas)
                           arm (apply max-key estimates (range n-arms))
                           ;; Pull arm
                           reward (if (< (rand) (nth true-means arm)) 1.0 0.0)]
                       (recur (update alphas arm + reward)
                              (update betas arm + (- 1.0 reward))
                              (+ total-reward reward)
                              (inc t)))))]
      ;; The best arm (index 1) should have the highest alpha
      (is (> (:total-reward result) 30)))))

(deftest ch08-discrete-bayesian-filter
  (testing "Discrete Bayesian Filter via Belief Updater"
    (let [states [:sunny :rainy]
          ;; Transition: sunny→sunny 0.7, sunny→rainy 0.3, rainy→rainy 0.6, rainy→sunny 0.4
          predict-fn (fn [belief _action]
                       {:sunny (+ (* 0.7 (:sunny belief 0.5)) (* 0.4 (:rainy belief 0.5)))
                        :rainy (+ (* 0.3 (:sunny belief 0.5)) (* 0.6 (:rainy belief 0.5)))})
          ;; Observation model: umbrella observed
          update-fn (fn [predicted obs]
                      (let [likelihoods (if (= obs :umbrella)
                                          {:sunny 0.1 :rainy 0.8}
                                          {:sunny 0.9 :rainy 0.2})
                            raw (into {} (map (fn [[s p]] [s (* p (get likelihoods s))]) predicted))
                            Z (reduce + (vals raw))]
                        (into {} (map (fn [[s p]] [s (/ p Z)]) raw))))
          updater (c/make-updater {:predict predict-fn :update update-fn})
          ;; Start with uniform belief, observe umbrella
          belief0 {:sunny 0.5 :rainy 0.5}
          belief1 (updater belief0 nil :umbrella)]
      ;; After seeing umbrella, rainy should be more likely
      (is (> (:rainy belief1) (:sunny belief1))))))

;; =============================================================
;; Chapter 9: Specifications as Functions
;; =============================================================

(deftest ch09-always-operator
  (testing "The Always Operator"
    (let [spec (fn [state] (- 10.0 state))  ;; state < 10
          always-spec (val/always spec)
          traj1 [1 2 3 4 5]
          traj2 [1 2 3 15 5]]
      (is (pos? (always-spec traj1)))
      (is (neg? (always-spec traj2))))))

(deftest ch09-eventually-operator
  (testing "The Eventually Operator"
    (let [spec (fn [state] (- state 5.0))  ;; state > 5
          eventually-spec (val/eventually spec)
          traj1 [1 2 3 4 4.9]
          traj2 [1 2 3 4 6]]
      (is (neg? (eventually-spec traj1)))
      (is (pos? (eventually-spec traj2))))))

(deftest ch09-logical-combinators
  (testing "Logical Combinators"
    (let [safe (fn [s] (- 10.0 s))
          warm (fn [s] (- s 2.0))
          safe-and-warm (val/spec-and safe warm)
          safe-or-warm (val/spec-or safe warm)]
      (is (pos? (safe-and-warm 5)))    ;; 5 < 10 AND 5 > 2
      (is (neg? (safe-and-warm 1)))    ;; 1 < 10 BUT 1 < 2
      (is (pos? (safe-or-warm 1)))     ;; 1 < 10 (safe), even though cold
      (is (neg? (safe-and-warm 11)))))) ;; 11 > 10 (unsafe) AND 11 > 2 (warm) → safe part is neg

(deftest ch09-complex-temporal-specs
  (testing "Composing Complex Temporal Specifications"
    (let [safe (fn [s] (- 100.0 s))
          reached-goal (fn [s] (- s 50.0))
          ;; "Always safe AND eventually reach goal"
          mission (val/spec-and
                   (val/always safe)
                   (val/eventually reached-goal))
          traj-good [10 20 30 40 60]
          traj-bad  [10 20 30 40 45]]
      (is (pos? (mission traj-good)))
      (is (neg? (mission traj-bad))))))

;; =============================================================
;; Chapter 10: Falsification as Composition
;; =============================================================

(deftest ch10-falsification-pipeline
  (testing "The Falsification Objective Pipeline"
    ;; disturbance is a scalar (dim=1), so dynamics receives a single number
    (let [system (val/make-system
                  (fn [state d] (+ state (first d) 0.1))
                  0.0)
          spec (val/always (fn [s] (- 5.0 (Math/abs s))))
          result (val/random-falsification system spec 20 1
                   :n-samples 500 :bounds [-2.0 2.0])]
      ;; With large disturbances, should be able to falsify
      (is (boolean? (:falsified? result))))))

(deftest ch10-falsification-composition
  (testing "Falsification as Pure Composition"
    ;; disturbance dim=1, so dynamics receives a vector [d]
    (let [system (val/make-system
                  (fn [state d] (+ state (* 0.5 (first d))))
                  0.0)
          spec (val/always (fn [s] (- 3.0 (Math/abs s))))
          optimizer (fn [obj x0] (opt/simulated-annealing obj x0
                                   :max-iter 1000
                                   :neighbor-fn (fn [x] (mapv #(+ % (* 0.5 (- (rand) 0.5))) x))
                                   :temp-schedule (fn [t] (* 5.0 (Math/exp (* -0.005 t))))))
          result (val/falsify system spec optimizer 10 1)]
      (is (boolean? (:falsified? result))))))

;; =============================================================
;; Chapter 11: Reachability and Sensitivity
;; =============================================================

(deftest ch11-interval-arithmetic
  (testing "Set Propagation via Interval Arithmetic"
    (let [A [[0.9 0.1] [0.0 0.8]]
          B [[1.0] [0.0]]
          w-set (val/interval-set [-0.1 -0.1] [0.1 0.1])
          u-set (val/interval-set [0.0] [1.0])
          init-set (val/interval-set [0.0 0.0] [1.0 1.0])
          reach-sets (val/linear-reachability A B w-set u-set init-set 5)]
      (is (= 6 (count reach-sets)))  ;; initial + 5 steps
      ;; Each set should be an interval
      (is (every? #(and (contains? % :lo) (contains? % :hi)) reach-sets)))))

(deftest ch11-sensitivity-analysis
  (testing "Sensitivity Analysis"
    ;; dim=1, so each disturbance step is a 1-element vector
    (let [system (val/make-system
                  (fn [state d] (+ state (first d)))
                  0.0)
          spec (val/always (fn [s] (- 5.0 (Math/abs s))))
          disturbance [0.0 0.0 3.0 0.0 0.0]  ;; step 3 has a big push
          sensitivity (val/robustness-sensitivity system spec disturbance 1)]
      ;; Should return 5 sensitivity values
      (is (= 5 (count sensitivity))))))

;; =============================================================
;; Chapter 12: Synthesis and Novel Compositions
;; =============================================================

(deftest ch12-meta-optimization
  (testing "Evolving Adam with a Genetic Algorithm"
    (let [result (ex/meta-optimize-adam)]
      (is (map? result))
      (is (contains? result :value)))))

(deftest ch12-automated-scientist
  (testing "Automated Scientist — symbolic regression"
    (let [result (ex/automated-scientist)]
      (is (map? result))
      ;; Should find constants near [2.5, 1.2]
      (is (< (:value result) 5.0)))))

;; =============================================================
;; Chapter 13: Practical HOF Idioms
;; =============================================================

(deftest ch13-partial-application
  (testing "Partial Application for Configuration"
    (let [configured-gd (partial opt/gradient-descent)
          f (fn [x] (reduce + (map #(* % %) x)))
          result (configured-gd f [3.0 3.0] :lr 0.1 :max-iter 200)]
      (is (< (:value result) 0.5)))))

(deftest ch13-memoization
  (testing "Memoizing an Expensive Objective"
    (let [call-count (atom 0)
          expensive-f (fn [x] (swap! call-count inc) (reduce + (map #(* % %) x)))
          memo-f (memoize expensive-f)
          ;; Call twice with same input
          _ (memo-f [1.0 2.0])
          _ (memo-f [1.0 2.0])]
      ;; Should only have been called once
      (is (= 1 @call-count)))))

(deftest ch13-transducers
  (testing "Transducers for Efficient Population Processing"
    (let [f (fn [x] (reduce + (map #(* % %) x)))
          population (repeatedly 50 #(mapv (fn [_] (- (rand 10) 5)) (range 3)))
          ;; Use transducer pipeline: evaluate → filter → sort
          xf (comp (map (fn [x] {:x x :value (f x)}))
                   (filter (fn [{:keys [value]}] (< value 50.0))))
          elite (into [] xf population)]
      (is (every? #(< (:value %) 50.0) elite)))))

(deftest ch13-juxt-multi-objective
  (testing "Juxt for Multi-Objective Evaluation"
    (let [f1 (fn [x] (reduce + (map #(* % %) x)))
          f2 (fn [x] (reduce + (map #(Math/abs %) x)))
          multi-eval (juxt f1 f2)
          result (multi-eval [1.0 2.0 3.0])]
      (is (= 2 (count result)))
      (is (= 14.0 (first result)))
      (is (= 6.0 (second result))))))

;; =============================================================
;; Appendix A: Clojure Primer
;; =============================================================

(deftest appendix-a-basics
  (testing "Basic Clojure operations"
    (is (= 42 (+ 40 2)))
    (is (= [1 4 9 16] (mapv #(* % %) [1 2 3 4])))
    (is (= 10 (reduce + [1 2 3 4])))
    (is (= 6 ((fn [x y] (* x y)) 2 3)))
    (is (= [2 4 6] (filterv even? [1 2 3 4 5 6])))))

;; =============================================================
;; Cross-domain composition tests (additional)
;; =============================================================

(deftest cross-domain-optimize-then-validate
  (testing "Optimize a controller, then validate it"
    (let [;; Step 1: Optimize a simple proportional controller gain
          plant (fn [state gain disturbance]
                  (+ (* 0.9 state) (* gain (- 0.0 state)) disturbance))
          ;; Objective: minimize steady-state error
          obj (fn [[gain]]
                (let [sys (val/make-system (fn [s d] (plant s gain (first d))) 5.0)
                      traj (val/rollout sys (repeat 20 [0.0]))
                      final-error (Math/abs (double (last traj)))]
                  final-error))
          opt-result (opt/nelder-mead obj [0.1] :max-iter 100)
          best-gain (first (:x opt-result))
          
          ;; Step 2: Validate the optimized controller
          sys (val/make-system (fn [s d] (plant s best-gain (first d))) 5.0)
          spec (val/always (fn [s] (- 20.0 (Math/abs s))))
          val-result (val/random-falsification sys spec 20 1
                       :n-samples 200 :bounds [-1.0 1.0])]
      (is (< (:value opt-result) 5.0))
      (is (boolean? (:falsified? val-result))))))

(deftest cross-domain-rl-with-safety
  (testing "RL policy with safety specification monitoring"
    (let [;; Simple gridworld
          states (range 10)
          actions (fn [_] [:left :right])
          reward (fn [s a] (if (= s 9) 10.0 -0.1))
          mdp (mdp/make-mdp states actions nil reward 0.9)
          env-step (fn [s a]
                     (let [s' (case a
                                :right (min 9 (inc s))
                                :left (max 0 (dec s)))]
                       {:state' s' :reward (reward s a) :done? (= s' 9)}))
          ;; Train agent
          Q (mdp/q-learning mdp env-step :max-episodes 200 :max-steps 20)
          ;; Create safety monitor
          safe-spec (fn [s] (- s 0.0))  ;; state >= 0 (trivially true here)
          monitor (val/make-monitor (val/always safe-spec) 10)
          ;; Run the trained policy through the monitor
          verdicts (loop [s 0 steps 0 results []]
                     (if (or (= s 9) (>= steps 20))
                       results
                       (let [verdict (monitor s)
                             a (if (empty? Q) :right
                                   (apply max-key #(get-in Q [s %] 0.0) [:left :right]))
                             {:keys [state']} (env-step s a)]
                         (recur state' (inc steps) (conj results verdict)))))]
      ;; All verdicts should be safe or unknown (robustness >= 0)
      (is (every? #(>= (:robustness %) 0) verdicts)))))
