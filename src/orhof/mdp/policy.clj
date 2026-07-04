(ns orhof.mdp.policy
  "Policy learning methods: Q-learning, SARSA, REINFORCE.
   Pattern: ITERATE — update value/policy from experience."
  (:require [orhof.core :as c]))

(defn q-learning
  "Tabular Q-learning. Updates Q(s,a) from experience.
   Catalog: D61. Pattern: ITERATE (reduce over episodes, fold over steps).
   env-step: (fn [state action] -> {:state' s', :reward r, :done? bool})
   Returns the Q-table."
  [mdp env-step & {:keys [alpha epsilon max-episodes max-steps]
                    :or   {alpha 0.1 epsilon 0.1
                           max-episodes 1000 max-steps 100}}]
  (let [{:keys [states actions gamma]} mdp]
    ;; Outer: reduce over episodes
    (reduce (fn [Q _episode]
              (let [s0 (rand-nth (vec states))]
                ;; Inner: fold over steps within an episode
                (:Q (c/fold-over-stream
                     (fn [{:keys [Q s]} _step]
                       (let [avail (vec (actions s))
                             a     (if (< (rand) epsilon)
                                     (rand-nth avail)
                                     (apply max-key
                                            (fn [a] (get-in Q [s a] 0.0))
                                            avail))
                             {:keys [state' reward done?]} (env-step s a)
                             max-q' (if done? 0.0
                                        (apply max (map #(get-in Q [state' %] 0.0)
                                                        (actions state'))))
                             old-q  (get-in Q [s a] 0.0)
                             new-q  (+ old-q (* alpha (- (+ reward (* gamma max-q')) old-q)))
                             Q'     (assoc-in Q [s a] new-q)]
                         (if done?
                           (reduced {:Q Q' :s state'})
                           {:Q Q' :s state'})))
                     {:Q Q :s s0}
                     (range max-steps)))))
            {}
            (range max-episodes))))

(defn sarsa
  "SARSA: on-policy TD control.
   Catalog: D62. Pattern: ITERATE.
   env-step: (fn [state action] -> {:state' s', :reward r, :done? bool})"
  [mdp env-step & {:keys [alpha epsilon max-episodes max-steps]
                    :or   {alpha 0.1 epsilon 0.1
                           max-episodes 1000 max-steps 100}}]
  (let [{:keys [states actions gamma]} mdp
        eps-greedy (fn [Q s]
                     (let [avail (vec (actions s))]
                       (if (< (rand) epsilon)
                         (rand-nth avail)
                         (apply max-key #(get-in Q [s %] 0.0) avail))))]
    (reduce (fn [Q _episode]
              (let [s0 (rand-nth (vec states))
                    a0 (eps-greedy Q s0)]
                (loop [s s0, a a0, Q-inner Q, step 0]
                  (if (>= step max-steps)
                    Q-inner
                    (let [{:keys [state' reward done?]} (env-step s a)
                          a'    (eps-greedy Q-inner state')
                          old-q (get-in Q-inner [s a] 0.0)
                          next-q (if done? 0.0 (get-in Q-inner [state' a'] 0.0))
                          new-q (+ old-q (* alpha (- (+ reward (* gamma next-q)) old-q)))
                          Q-next (assoc-in Q-inner [s a] new-q)]
                      (if done?
                        Q-next
                        (recur state' a' Q-next (inc step))))))))
            {}
            (range max-episodes))))

(defn reinforce
  "REINFORCE policy gradient algorithm.
   Catalog: D46. Pattern: ITERATE + SENSITIVITY ANALYZER.
   policy-fn:   (fn [params state] -> action-probs)
   env-step:    (fn [state action] -> {:state' s', :reward r, :done? bool})
   update-fn:   (fn [params gradient] -> params') — e.g., SGD step"
  [mdp env-step policy-fn update-fn init-params
   & {:keys [max-episodes max-steps gamma]
      :or   {max-episodes 500 max-steps 100 gamma 0.99}}]
  (reduce (fn [params _episode]
            (let [s0 (rand-nth (vec (:states mdp)))
                  ;; Generate episode trajectory
                  trajectory
                  (reduce (fn [traj _step]
                            (let [s (or (:next-state (peek traj)) s0)
                                  probs (policy-fn params s)
                                  avail (vec ((:actions mdp) s))
                                  a     (rand-nth avail)
                                  {:keys [state' reward done?]} (env-step s a)
                                  entry {:state s :action a :reward reward :next-state state'}]
                              (if done?
                                (reduced (conj traj entry))
                                (conj traj entry))))
                          []
                          (range max-steps))
                  ;; Compute discounted returns (reduce from right)
                  rewards (mapv :reward trajectory)
                  returns (vec (reverse
                                (reduce (fn [acc r]
                                          (conj acc (+ r (* gamma (or (peek acc) 0.0)))))
                                        []
                                        (reverse rewards))))
                  ;; Compute gradient using finite differences on log-policy
                  gradient (reduce c/v+
                                   (c/v-zero (count init-params))
                                   (map (fn [t G]
                                          ;; Finite-difference approximation of score function
                                          (let [h 1e-4
                                                score (mapv (fn [i]
                                                              (let [p+ (update init-params i + h)
                                                                    p- (update init-params i - h)
                                                                    lp+ (Math/log (max 1e-300 (get (policy-fn p+ (:state t)) (:action t) 0.1)))
                                                                    lp- (Math/log (max 1e-300 (get (policy-fn p- (:state t)) (:action t) 0.1)))]
                                                                (/ (- lp+ lp-) (* 2 h))))
                                                            (range (count init-params)))]
                                            (c/v* G score)))
                                        trajectory returns))]
              (update-fn params gradient)))
          init-params
          (range max-episodes)))

;; =============================================================
;; EVOLUTION STRATEGIES
;; =============================================================

(defn evolution-strategies
  "Evolution Strategies — gradient-free policy optimization.
   Catalog: D43. Pattern: ITERATE + MAP-OVER + REDUCE.

   (Theta -> R) -> Theta -> Opts -> {:x Theta, :value R}

   Evaluates isotropic perturbations, updates mean toward
   perturbations with highest fitness.

   Example:
     (evolution-strategies (fn [t] (- (reduce + (map #(* % %) t)))) [5.0 5.0])
     ;=> {:x [0.1 0.1] :value -0.02}"
  [fitness-fn theta0 & {:keys [n-samples sigma lr max-iter tol]
                          :or   {n-samples 50 sigma 0.1 lr 0.01
                                 max-iter 200 tol 1e-8}}]
  (c/iterative-solve
   {:init-state {:x theta0 :value (fitness-fn theta0) :prev-value Double/MAX_VALUE}
    :max-iter max-iter
    :converged? (c/value-converged? tol)
    :step-fn (fn [state]
               (let [theta (:x state)
                     n     (count theta)
                     ;; Generate perturbations
                     epsilons (repeatedly n-samples
                                (fn [] (mapv (fn [_] (* sigma (- (rand) 0.5) 2.0)) (range n))))
                     ;; Evaluate fitness of each perturbation
                     fitnesses (mapv (fn [eps] (fitness-fn (c/v+ theta eps))) epsilons)
                     ;; Normalize fitnesses for stability
                     f-mean (/ (reduce + fitnesses) (double n-samples))
                     f-std  (let [v (/ (reduce + (map #(let [d (- % f-mean)] (* d d)) fitnesses))
                                       (double n-samples))]
                               (Math/sqrt (max v 1e-30)))
                     norm-f (mapv (fn [f] (/ (- f f-mean) f-std)) fitnesses)
                     ;; Weighted gradient estimate
                     grad (c/v* (/ 1.0 (* n-samples sigma))
                                (reduce (fn [acc [fit eps]]
                                          (c/v+ acc (c/v* fit eps)))
                                        (c/v-zero n)
                                        (map vector norm-f epsilons)))
                     theta-new (c/v+ theta (c/v* lr grad))]
                 {:x theta-new :value (fitness-fn theta-new)
                  :prev-value (:value state)}))}))

;; =============================================================
;; REWARD-TO-GO
;; =============================================================

(defn reward-to-go
  "Compute discounted reward-to-go (future returns).
   Catalog: D47. Pattern: REDUCE (right fold).

   [R] -> R -> [R]

   G_t = r_t + gamma * G_{t+1}

   Example:
     (reward-to-go [1.0 2.0 3.0] 0.99)
     ;=> [5.9403 4.97 3.0]"
  [rewards gamma]
  (vec (reverse
        (reduce (fn [acc r]
                  (conj acc (+ r (* gamma (or (peek acc) 0.0)))))
                []
                (reverse rewards)))))

;; =============================================================
;; GENERALIZED ADVANTAGE ESTIMATION (GAE)
;; =============================================================

(defn gae
  "Generalized Advantage Estimation.
   Catalog: D54. Pattern: REDUCE (right fold).

   [R] -> [R] -> R -> R -> [R]

   rewards: [r_0, ..., r_{T-1}]
   values: [V(s_0), ..., V(s_T)] (T+1 values, last is terminal)
   gamma: discount factor
   lambda: GAE decay parameter

   Returns advantage estimates A_t = sum_{l=0}^{T-t} (gamma*lambda)^l * delta_{t+l}
   where delta_t = r_t + gamma*V(s_{t+1}) - V(s_t)

   Example:
     (gae [1.0 1.0 1.0] [5.0 4.0 3.0 0.0] 0.99 0.95)
     ;=> [advantages...]"
  [rewards values gamma lambda]
  (let [T (count rewards)
        ;; TD residuals: delta_t = r_t + gamma*V(s_{t+1}) - V(s_t)
        deltas (mapv (fn [t]
                       (+ (nth rewards t)
                          (* gamma (nth values (inc t)))
                          (- (nth values t))))
                     (range T))]
    ;; GAE: right fold with decay gamma*lambda
    (vec (reverse
          (reduce (fn [acc t]
                    (let [prev-gae (or (peek acc) 0.0)
                          gae-t (+ (nth deltas (- (dec T) t))
                                   (* gamma lambda prev-gae))]
                      (conj acc gae-t)))
                  []
                  (range T))))))
