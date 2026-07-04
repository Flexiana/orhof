(ns orhof.val.falsify
  "Falsification methods: optimization-based, random, stress testing.
   Pattern: PIPELINE COMPOSER — Falsification = Optimizer . Robustness . Rollout."
  (:require [orhof.core :as c]
            [orhof.val.spec :as spec]))

(defn falsify
  "Falsification via optimization.
   Catalog: V12. Pattern: PIPELINE COMPOSER.
   Composes: optimizer(lambda d. -rho(spec, rollout(system, d)))

   system:    dynamical system
   spec-fn:   specification (trajectory -> robustness)
   optimizer: (fn [objective x0] -> result)
   horizon:   number of time steps
   dim:       disturbance dimension per step

   Returns {:falsified? bool, :disturbance, :robustness, :trajectory}"
  [system spec-fn optimizer horizon dim]
  (let [objective (fn [disturbance-flat]
                    (let [ds (map vec (partition dim disturbance-flat))
                          trajectory (spec/rollout system ds)]
                      (spec-fn trajectory)))
        x0     (vec (repeat (* horizon dim) 0.0))
        result (optimizer objective x0)
        best-d (or (:best-x result) (:x result))
        ds     (map vec (partition dim best-d))
        trajectory (spec/rollout system ds)
        robustness (spec-fn trajectory)]
    {:falsified?  (neg? robustness)
     :disturbance best-d
     :robustness  robustness
     :trajectory  trajectory
     :optimizer-result result}))

(defn random-falsification
  "Random falsification: sample random disturbances and check spec.
   Catalog: V9. Pattern: GENERATE + MAP-OVER."
  [system spec-fn horizon dim & {:keys [n-samples bounds]
                                  :or   {n-samples 1000 bounds [-1.0 1.0]}}]
  (let [[lo hi] bounds
        range-size (- hi lo)]
    (reduce (fn [best _i]
              (let [best-rob (:robustness best)]
                (if (and (some? best-rob) (neg? best-rob))
                  (reduced best)
                  (let [d-flat (vec (repeatedly (* horizon dim)
                                                #(+ lo (* (rand) range-size))))
                        ds   (map vec (partition dim d-flat))
                        traj (spec/rollout system ds)
                        rob  (spec-fn traj)]
                    (if (< rob (or best-rob Double/MAX_VALUE))
                      {:falsified? (neg? rob) :disturbance d-flat
                       :robustness rob :trajectory traj :samples (inc (:samples best 0))}
                      (update best :samples (fnil inc 0)))))))
            {:falsified? false :disturbance nil :robustness Double/MAX_VALUE
             :trajectory nil :samples 0}
            (range n-samples))))

(defn adaptive-stress-test
  "Adaptive stress testing using random tree exploration.
   Catalog: V17-V20. Pattern: TREE/GRAPH EXPLORER.
   system: dynamical system, spec-fn: specification,
   dim: disturbance dim, horizon: time steps, budget: rollouts."
  [system spec-fn dim horizon & {:keys [budget] :or {budget 500}}]
  (reduce (fn [{:keys [robustness] :as best} _]
            (let [ds   (vec (repeatedly horizon
                                        (fn [] (vec (repeatedly dim #(* 0.5 (- (rand) 0.5)))))))
                  traj (spec/rollout system ds)
                  rob  (spec-fn traj)]
              (if (< rob robustness)
                {:falsified? (neg? rob) :robustness rob :trajectory traj
                 :disturbances ds :iterations (inc (:iterations best 0))}
                (update best :iterations (fnil inc 0)))))
          {:falsified? false :robustness Double/MAX_VALUE :trajectory nil :iterations 0}
          (range budget)))

;; =============================================================
;; DISTURBANCE-BASED FALSIFICATION
;; =============================================================

(defn disturbance-falsification
  "Disturbance-based falsification with custom sampler.
   Catalog: V10. Pattern: GENERATE + PARAMETERIZE.

   System -> Spec -> Int -> Int -> {:keys [sampler n-samples]}
   -> {:falsified? Bool, :robustness R, ...}

   sampler: () -> disturbance-vector (one step)

   Example:
     (disturbance-falsification sys spec 5 2
       :sampler (fn [] [0.1 0.2]) :n-samples 500)"
  [system spec-fn horizon dim & {:keys [sampler n-samples]
                                   :or   {n-samples 1000}}]
  (let [sampler (or sampler (fn [] (vec (repeatedly dim #(- (rand) 0.5)))))]
    (reduce (fn [best _]
              (if (and (:falsified? best) (neg? (:robustness best)))
                (reduced best)
                (let [ds   (vec (repeatedly horizon sampler))
                      traj (spec/rollout system ds)
                      rob  (spec-fn traj)]
                  (if (< rob (:robustness best))
                    {:falsified? (neg? rob) :robustness rob :trajectory traj
                     :disturbances ds :samples (inc (:samples best 0))}
                    (update best :samples (fnil inc 0))))))
            {:falsified? false :robustness Double/MAX_VALUE :trajectory nil :samples 0}
            (range n-samples))))

;; =============================================================
;; FUZZING
;; =============================================================

(defn fuzzing
  "Mutation-based fuzzing for falsification.
   Catalog: V11. Pattern: ITERATE + DELEGATE.

   System -> Spec -> [R] -> Int -> {:keys [mutate-fn max-iter]}
   -> {:falsified? Bool, :robustness R, ...}

   Iteratively mutates the disturbance vector, keeping improvements.

   Example:
     (fuzzing sys spec [0 0 0] 1
       :mutate-fn (fn [d] (mapv #(+ % (* 0.1 (rand))) d))
       :max-iter 500)"
  [system spec-fn seed-disturbance dim & {:keys [mutate-fn max-iter]
                                            :or   {max-iter 500}}]
  (let [mutate-fn (or mutate-fn
                      (fn [d] (mapv #(+ % (* 0.3 (- (rand) 0.5))) d)))]
    (reduce (fn [best _]
              (let [d-flat (mutate-fn (or (:disturbance best) seed-disturbance))
                    ds     (mapv vec (partition dim d-flat))
                    traj   (spec/rollout system ds)
                    rob    (spec-fn traj)]
                (if (< rob (:robustness best))
                  {:falsified? (neg? rob) :robustness rob :trajectory traj
                   :disturbance d-flat :iterations (inc (:iterations best 0))}
                  (update best :iterations (fnil inc 0)))))
            {:falsified? false :robustness Double/MAX_VALUE :trajectory nil
             :disturbance seed-disturbance :iterations 0}
            (range max-iter))))

;; =============================================================
;; ROBUSTNESS-BASED OBJECTIVE
;; =============================================================

(defn robustness-objective
  "Create a robustness-based objective for any optimizer.
   Catalog: V13. Pattern: COMPOSE.

   System -> Spec -> Int -> Int -> (Disturbance-flat -> R)

   Composes rollout + spec evaluation into a single objective function
   that any optimizer can minimize.

   Example:
     (def obj (robustness-objective sys spec 5 1))
     (obj [0.1 0.2 0.3 0.4 0.5]) ;=> 2.3 (robustness)"
  [system spec-fn horizon dim]
  (fn [disturbance-flat]
    (let [ds   (mapv vec (partition dim disturbance-flat))
          traj (spec/rollout system ds)]
      (spec-fn traj))))

;; =============================================================
;; SHOOTING METHODS
;; =============================================================

(defn shooting-method
  "Shooting method — evaluate random action/disturbance sequences.
   Catalog: V16. Pattern: GENERATE + MAP-OVER.

   System -> Spec -> Int -> Int -> {:keys [n-samples]}
   -> {:falsified? Bool, :robustness R, :best-sequence [...]}

   Example:
     (shooting-method sys spec 5 1 :n-samples 200)"
  [system spec-fn horizon dim & {:keys [n-samples bounds]
                                   :or   {n-samples 200 bounds [-1.0 1.0]}}]
  (let [[lo hi] bounds]
    (reduce (fn [best _]
              (let [seq (vec (repeatedly horizon
                              (fn [] (vec (repeatedly dim #(+ lo (* (rand) (- hi lo))))))))
                    traj (spec/rollout system seq)
                    rob  (spec-fn traj)]
                (if (< rob (:robustness best))
                  {:falsified? (neg? rob) :robustness rob :trajectory traj
                   :best-sequence seq}
                  best)))
            {:falsified? false :robustness Double/MAX_VALUE}
            (range n-samples))))

;; =============================================================
;; RL-BASED FALSIFICATION
;; =============================================================

(defn rl-falsification
  "RL-based falsification — learn a policy to choose disturbances.
   Catalog: V20. Pattern: ITERATE + DELEGATE.

   System -> Spec -> Int -> Int -> Opts
   -> {:falsified? Bool, :robustness R, ...}

   Uses epsilon-greedy Q-learning where the 'state' is discretized
   system state and 'actions' are disturbance bins.

   Example:
     (rl-falsification sys spec 5 1 :max-episodes 200)"
  [system spec-fn horizon dim & {:keys [max-episodes max-steps epsilon alpha]
                                   :or   {max-episodes 200 max-steps 10
                                          epsilon 0.3 alpha 0.1}}]
  (let [;; Discretize disturbance space into bins
        bins  [[-0.5] [0.0] [0.5]]
        gamma 0.99
        result
    (reduce (fn [{:keys [best-rob best-traj Q]} _episode]
              (let [;; Run one episode
                    episode-result
                    (reduce (fn [{:keys [state Q traj]} _step]
                              (let [;; Epsilon-greedy action selection
                                    s-key (int (* 10 (min 5.0 (max -5.0 state))))
                                    action (if (< (rand) epsilon)
                                             (rand-nth bins)
                                             (let [q-vals (map (fn [a] [(get-in Q [s-key a] 0.0) a]) bins)
                                                   best   (apply min-key first q-vals)]
                                               (second best)))
                                    ;; Step system
                                    new-state ((:dynamics system) state action)
                                    rob       (spec-fn (conj traj new-state))
                                    reward    (- rob)  ;; negative robustness = reward for adversary
                                    ;; Q-learning update
                                    s'-key    (int (* 10 (min 5.0 (max -5.0 new-state))))
                                    old-q     (get-in Q [s-key action] 0.0)
                                    max-q'    (apply max (map #(get-in Q [s'-key %] 0.0) bins))
                                    new-q     (+ old-q (* alpha (- (+ reward (* gamma max-q')) old-q)))
                                    Q'        (assoc-in Q [s-key action] new-q)]
                                {:state new-state :Q Q' :traj (conj traj new-state)}))
                            {:state (:initial-state system) :Q Q
                             :traj [(:initial-state system)]}
                            (range max-steps))
                    final-rob (spec-fn (:traj episode-result))]
                {:best-rob  (min best-rob final-rob)
                 :best-traj (if (< final-rob best-rob) (:traj episode-result) best-traj)
                 :Q         (:Q episode-result)}))
            {:best-rob Double/MAX_VALUE :best-traj nil :Q {}}
            (range max-episodes))]
      {:falsified? (neg? (:best-rob result))
       :robustness (:best-rob result)
       :trajectory (:best-traj result)}))

