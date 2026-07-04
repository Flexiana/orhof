(ns orhof.mdp.planning
  "Exact sequential decision methods: MDP representation,
   Bellman operators, value iteration, policy iteration.
   Pattern: ITERATE on function spaces (Bellman operator as Function Transformer)."
  (:require [orhof.core :as c]))

(defn make-mdp
  "Create an MDP from its components.
   states:     collection of states
   actions:    (fn [state] -> collection of actions)
   transition: (fn [state action] -> {state' -> probability})
   reward:     (fn [state action] -> reward)
   gamma:      discount factor"
  [states actions transition reward gamma]
  {:states states :actions actions :transition transition
   :reward reward :gamma gamma})

(defn bellman-operator
  "The Bellman optimality operator.
   Catalog: D22 core. Pattern: FUNCTION TRANSFORMER.
   Takes an MDP, returns (V -> V') — a HOF on function spaces.
   T(V)(s) = max_a [R(s,a) + gamma * sum_{s'} T(s,a,s') V(s')]"
  [mdp]
  (let [{:keys [actions transition reward gamma]} mdp]
    (fn [V]
      (fn [s]
        (apply max
               (map (fn [a]
                      (+ (reward s a)
                         (* gamma
                            (reduce + (map (fn [[s' p]] (* p (V s')))
                                           (transition s a))))))
                    (actions s)))))))

(defn bellman-policy-operator
  "The Bellman operator for a fixed policy pi.
   Catalog: D20 core. Pattern: FUNCTION TRANSFORMER.
   T_pi(V)(s) = R(s, pi(s)) + gamma * sum_{s'} T(s, pi(s), s') V(s')"
  [mdp policy]
  (let [{:keys [transition reward gamma]} mdp]
    (fn [V]
      (fn [s]
        (let [a (policy s)]
          (+ (reward s a)
             (* gamma
                (reduce + (map (fn [[s' p]] (* p (V s')))
                               (transition s a))))))))))

(defn lookahead
  "One-step lookahead: compute Q(s,a) given value function V.
   Returns the value of taking action a in state s then following V."
  [mdp V s a]
  (let [{:keys [transition reward gamma]} mdp]
    (+ (reward s a)
       (* gamma
          (reduce + (map (fn [[s' p]] (* p (V s')))
                         (transition s a)))))))

(defn greedy-policy
  "Extract the greedy policy from a value function.
   Returns (fn [s] -> best-action)."
  [mdp V]
  (let [{:keys [actions]} mdp]
    (fn [s]
      (apply max-key
             (fn [a] (lookahead mdp V s a))
             (actions s)))))

(defn value-iteration
  "Value iteration: repeatedly apply the Bellman operator until convergence.
   Catalog: D22. Pattern: ITERATE on function spaces.
   Returns {:V value-fn, :policy policy-fn, :iterations k}."
  [mdp & {:keys [tol max-iter] :or {tol 1e-6 max-iter 1000}}]
  (assert (pos? max-iter) "max-iter must be positive")
  (let [{:keys [states]} mdp
        states-vec (vec states)
        bellman (bellman-operator mdp)
        init-V (zipmap states-vec (repeat 0.0))
        update-values
        (fn [V-map]
          (let [V-fn (fn [s] (get V-map s 0.0))
                V-next-fn (bellman V-fn)]
            (zipmap states-vec (map V-next-fn states-vec))))
        max-delta
        (fn [V-old V-new]
          (apply max
                 (map (fn [s]
                        (Math/abs (- (get V-new s 0.0)
                                     (get V-old s 0.0))))
                      states-vec)))
        result
        (reduce
         (fn [{:keys [V iter]} _]
           (let [V' (update-values V)
                 next-iter (inc iter)]
             (if (< (max-delta V V') tol)
               (reduced {:V V' :iter next-iter :converged true})
               {:V V' :iter next-iter :converged false})))
         {:V init-V :iter 0 :converged false}
         (range max-iter))
        value-fn (fn [s] (get (:V result) s 0.0))]
    {:V (:V result)
     :policy (greedy-policy mdp value-fn)
     :iterations (:iter result)
     :converged (:converged result)}))

(defn policy-evaluation
  "Evaluate a policy by iterating the Bellman policy operator.
   Catalog: D20. Pattern: ITERATE.
   Returns a value function (map: state -> value)."
  [mdp policy & {:keys [tol max-iter] :or {tol 1e-6 max-iter 500}}]
  (assert (pos? max-iter) "max-iter must be positive")
  (let [{:keys [states]} mdp
        states-vec (vec states)
        t-pi (bellman-policy-operator mdp policy)
        init-V (zipmap states-vec (repeat 0.0))
        update-values
        (fn [V-map]
          (let [V-fn (fn [s] (get V-map s 0.0))
                V-next-fn (t-pi V-fn)]
            (zipmap states-vec (map V-next-fn states-vec))))
        max-delta
        (fn [V-old V-new]
          (apply max
                 (map (fn [s]
                        (Math/abs (- (get V-new s 0.0)
                                     (get V-old s 0.0))))
                      states-vec)))
        result
        (reduce
          (fn [{:keys [V]} _]
            (let [V' (update-values V)]
              (if (< (max-delta V V') tol)
                (reduced {:V V'})
                {:V V'})))
          {:V init-V}
          (range max-iter))]
    (:V result)))

(defn policy-iteration
  "Policy iteration: alternate between evaluation and improvement.
   Catalog: D21. Pattern: ITERATE (comp improve evaluate).
   Returns {:V value-fn, :policy policy-fn, :iterations k}."
  [mdp & {:keys [max-iter] :or {max-iter 100}}]
  (assert (pos? max-iter) "max-iter must be positive")
  (let [{:keys [states actions]} mdp
        states-vec (vec states)
        init-policy (fn [s] (first (actions s)))
        policy-changed?
        (fn [old-policy new-policy]
          (boolean
           (some (fn [s] (not= (old-policy s) (new-policy s)))
                 states-vec)))
        step-policy
        (fn [{:keys [policy iter]} _]
          (let [V (policy-evaluation mdp policy)
                V-fn (fn [s] (get V s 0.0))
                improved-policy (greedy-policy mdp V-fn)
                changed? (policy-changed? policy improved-policy)
                next-iter (inc iter)
                state {:V V
                       :policy improved-policy
                       :iterations next-iter
                       :converged (not changed?)}]
            (if changed?
              (assoc state :iter next-iter)
              (reduced state))))]
    (reduce step-policy
            {:V nil :policy init-policy :iter 0 :iterations 0 :converged false}
            (range max-iter))))

;; =============================================================
;; ASYNCHRONOUS VALUE ITERATION
;; =============================================================

(defn async-value-iteration
  "Asynchronous value iteration — updates one state at a time.
   Catalog: D23. Pattern: ITERATE + DELEGATE.

   MDP -> Opts -> {:V {State -> R}, :policy (State -> Action)}

   Updates states in round-robin order rather than all at once.
   Can converge faster when some states are more important.

   Example:
     (async-value-iteration mdp :max-iter 200)
     ;=> {:V {...} :policy (fn [s] ...) :converged true}"
  [mdp & {:keys [tol max-iter] :or {tol 1e-6 max-iter 1000}}]
  (assert (pos? max-iter) "max-iter must be positive")
  (let [{:keys [states]} mdp
        states-vec (vec states)
        bellman (bellman-operator mdp)
        init-V (zipmap states-vec (repeat 0.0))
        update-state
        (fn [V-map s]
          (let [V-fn (fn [s'] (get V-map s' 0.0))
                next-value ((bellman V-fn) s)]
            (assoc V-map s next-value)))
        update-values
        (fn [V-map]
          (reduce update-state V-map states-vec))
        max-delta
        (fn [V-old V-new]
          (apply max
                 (map (fn [s]
                        (Math/abs (- (get V-new s 0.0)
                                     (get V-old s 0.0))))
                      states-vec)))
        result
        (reduce
         (fn [{:keys [V iter]} _]
           (let [V' (update-values V)
                 next-iter (inc iter)]
             (if (< (max-delta V V') tol)
               (reduced {:V V' :iter next-iter :converged true})
               {:V V' :iter next-iter :converged false})))
         {:V init-V :iter 0 :converged false}
         (range max-iter))
        value-fn (fn [s] (get (:V result) s 0.0))]
    {:V (:V result)
     :policy (greedy-policy mdp value-fn)
     :iterations (:iter result)
     :converged (:converged result)}))

