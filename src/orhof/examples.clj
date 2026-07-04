(ns orhof.examples
  "Novel compositions of Higher-Order Functions across Optimization,
   Decision Making, and Validation."
  (:require [orhof.core :as c]
            [orhof.optimize :as opt]
            [orhof.mdp :as mdp]
            [orhof.validate :as val]))

;; ======================================================================
;; EXAMPLE 1: Meta-Optimization
;; Evolving an Optimizer using a Genetic Algorithm
;;
;; We use a Genetic Algorithm (Population Evolver) to find the best
;; hyperparameters (learning rate, momentum) for an Adam optimizer (IRO)
;; that is trying to solve a difficult non-convex problem (Ackley function).
;; ======================================================================

(defn ackley [x]
  (let [n (count x)
        sum-sq (reduce + (map #(* % %) x))
        sum-cos (reduce + (map #(Math/cos (* 2.0 Math/PI %)) x))
        term1 (* -20.0 (Math/exp (* -0.2 (Math/sqrt (/ sum-sq n)))))
        term2 (Math/exp (/ sum-cos n))]
    (+ term1 (- term2) 20.0 Math/E)))

(defn meta-optimize-adam []
  (let [;; The "fitness" of an Adam configuration is the final value
        ;; it achieves on the Ackley function after 100 iterations.
        evaluate-adam (fn [[lr beta1 beta2]]
                        (let [result (opt/adam ackley [1.0 1.0 1.0]
                                               :lr (Math/abs lr)
                                               :beta1 (min 0.99 (Math/abs beta1))
                                               :beta2 (min 0.999 (Math/abs beta2))
                                               :max-iter 100)]
                          (:value result)))
        ;; We use a GA to find the best [lr, beta1, beta2]
        ga-result (opt/genetic-algorithm evaluate-adam 3
                                         :pop-size 20
                                         :max-iter 10)]
    ga-result))

;; ======================================================================
;; EXAMPLE 2: Adversarial Reinforcement Learning
;; Falsifying an RL Policy during training
;;
;; We train a Q-learning agent, but the environment's transition dynamics
;; are controlled by a Falsification pipeline trying to minimize the agent's
;; robustness to a safety specification.
;; ======================================================================

(defn adversarial-rl []
  (let [;; A simple 1D world: state is position, actions are +1, -1
        states (range -10 11)
        actions (fn [_] [-1 1])
        ;; The agent wants to reach +10
        reward (fn [s a] (if (= s 10) 10.0 -0.1))
        
        ;; The safety spec: never go below -5
        safe? (fn [traj] (apply min (map #(+ % 5) traj)))
        
        ;; The adversarial environment step function
        env-step (fn [s a]
                   ;; The adversary gets to choose a disturbance d in [-1, 0, 1]
                   ;; to minimize the agent's safety.
                   ;; We use one-step lookahead falsification!
                   (let [adversary-obj (fn [d-flat]
                                         (let [d (Math/round (double (first d-flat)))
                                               d (max -1 (min 1 d))
                                               s' (max -10 (min 10 (+ s a d)))]
                                           ;; Objective is to minimize safety margin of s'
                                           (+ s' 5)))
                         adv-res (opt/nelder-mead adversary-obj [0.0] :max-iter 10)
                         best-d (Math/round (double (or (first (:x adv-res)) 0.0)))
                         best-d (max -1 (min 1 best-d))
                         s' (max -10 (min 10 (+ s a best-d)))]
                     {:state' s' :reward (reward s a) :done? (= s' 10)}))
        
        ;; Train the agent in this adversarial environment
        mdp (mdp/make-mdp states actions nil reward 0.9)
        Q (mdp/q-learning mdp env-step :max-episodes 100 :max-steps 50)]
    Q))

;; ======================================================================
;; EXAMPLE 3: Neuro-Symbolic Reachability
;; Using MCTS to explore a Continuous Reachability Graph
;;
;; We want to find a path through a continuous state space that satisfies
;; a temporal logic specification, but the dynamics are non-linear.
;; We use MCTS (Tree Explorer) where the node evaluation is driven by
;; a Reachability Set Propagator.
;; ======================================================================

(defn mcts-reachability []
  (let [;; Non-linear system: x' = x + u + sin(x)
        dynamics (fn [x u] (+ x u (Math/sin x)))
        ;; Interval arithmetic lifting of the dynamics
        interval-dynamics (fn [[lo hi] u]
                            (let [;; min/max of sin(x) on [lo, hi] is complex, 
                                  ;; we use a crude overapproximation:
                                  sin-lo -1.0
                                  sin-hi 1.0]
                              [(+ lo u sin-lo) (+ hi u sin-hi)]))
        
        ;; MCTS formulation
        ;; State is the current interval [lo, hi]
        actions (fn [_] [-1.0 0.0 1.0])
        transition (fn [iset u]
                     {(interval-dynamics iset u) 1.0})
        ;; Reward: +1 if the interval is fully inside the target [5, 10]
        reward (fn [[lo hi] u]
                 (if (and (>= lo 5.0) (<= hi 10.0)) 1.0 0.0))
        
        mdp (mdp/make-mdp nil actions transition reward 0.9)
        
        ;; Run MCTS from initial set [0, 0]
        best-action (mdp/mcts mdp [0.0 0.0] :budget 100)]
    best-action))

;; ======================================================================
;; EXAMPLE 4: The Automated Scientist
;; Composing PC, MB, and IRO to discover physical laws
;;
;; 1. Generate data from a noisy physical process (Sampler)
;; 2. Use a Genetic Algorithm to evolve mathematical equations (Model Builder)
;; 3. Use Gradient Descent to fine-tune the constants in the equations (IRO)
;; ======================================================================

;; A simple symbolic regression engine
(defn evaluate-expr [expr x c]
  (cond
    (number? expr) expr
    (= expr 'x) x
    (= expr 'c) c
    (seq? expr)
    (let [[op a b] expr
          va (evaluate-expr a x c)
          vb (evaluate-expr b x c)]
      (case op
        + (+ va vb)
        * (* va vb)
        - (- va vb)
        0.0))))

(defn automated-scientist []
  (let [;; 1. The hidden truth: y = 2.5 * x^2 + 1.2
        true-f (fn [x] (+ (* 2.5 x x) 1.2))
        data (mapv (fn [x] {:x x :y (+ (true-f x) (* 0.1 (- (rand) 0.5)))})
                   (range -5.0 5.0 0.5))
        
        ;; 2. The Model Builder: GA over expression trees
        ;; (Simplified for demonstration: we fix the tree structure and evolve constants)
        ;; Tree: (+ (* c1 (* x x)) c2)
        fitness (fn [[c1 c2]]
                  (let [mse (reduce + (map (fn [{:keys [x y]}]
                                             (let [pred (+ (* c1 x x) c2)
                                                   err (- pred y)]
                                               (* err err)))
                                           data))]
                    mse))
        
        ;; 3. We use Nelder-Mead to optimize the constants for the structure
        result (opt/nelder-mead fitness [1.0 1.0] :max-iter 200)]
    result))
