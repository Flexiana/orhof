(ns orhof.val.sampling
  "Sampling methods: rejection, MCMC, importance sampling.
   Pattern: SAMPLER CONSTRUCTOR — build a sampling function from a target."
  (:require [orhof.core :as c]
            [orhof.val.spec :as spec]))

(defn rejection-sampler
  "Rejection sampling. Returns a sampler function.
   Catalog: V21. Pattern: SAMPLER CONSTRUCTOR.
   target-pdf: x -> p(x) (unnormalized)
   proposal-sampler: () -> x
   proposal-pdf: x -> q(x)
   M: bound such that p(x) <= M * q(x)"
  [target-pdf proposal-sampler proposal-pdf M]
  (fn []
    (c/sample-until
     proposal-sampler
     (fn [x]
       (<= (rand) (/ (target-pdf x) (* M (proposal-pdf x))))))))

(defn metropolis-hastings
  "Metropolis-Hastings MCMC sampler.
   Catalog: V22. Pattern: ITERATE.
   target-pdf: x -> p(x) (unnormalized)
   proposal: (fn [x] -> x') — symmetric proposal
   Returns a sampler function that produces n samples."
  [target-pdf proposal x0 & {:keys [burn-in thin]
                               :or   {burn-in 100 thin 1}}]
  (fn [n]
    (->> (iterate (fn [x]
                    (let [x' (proposal x)
                          alpha (min 1.0 (/ (target-pdf x') (max 1e-300 (target-pdf x))))]
                      (if (<= (rand) alpha) x' x)))
                  x0)
         (drop burn-in)
         (take-nth thin)
         (take n)
         vec)))

(defn importance-sampling
  "Importance sampling for probability estimation.
   Catalog: V27. Pattern: MAP-OVER + REDUCE.
   target-pdf: x -> p(x)
   proposal-sampler: () -> x
   proposal-pdf: x -> q(x)
   indicator: x -> boolean (event of interest)
   n: number of samples"
  [target-pdf proposal-sampler proposal-pdf indicator n]
  (let [samples    (repeatedly n proposal-sampler)
        weights    (map (fn [x] (/ (target-pdf x) (max 1e-300 (proposal-pdf x)))) samples)
        indicators (map (fn [x] (if (indicator x) 1.0 0.0)) samples)
        weighted   (map * weights indicators)
        Z          (reduce + weights)]
    {:probability (/ (reduce + weighted) Z)
     :effective-samples (/ (* Z Z) (reduce + (map #(* % %) weights)))
     :n-samples n}))

(defn cross-entropy-estimation
  "Cross-entropy method for rare event probability estimation.
   Catalog: V29. Pattern: ITERATE + REDUCE.
   Iteratively improves sampling distribution to focus on failure region."
  [system spec-fn horizon dim & {:keys [n-samples n-elite max-iter rho]
                                  :or   {n-samples 500 n-elite 50 max-iter 10 rho 0.1}}]
  (let [total-dim (* horizon dim)]
    (reduce (fn [{:keys [mu sigma]} _k]
              (let [samples (repeatedly n-samples
                                       (fn [] (mapv (fn [m s] (+ m (* s (- (rand) 0.5) 2.0)))
                                                    mu sigma)))
                    scored  (map (fn [d-flat]
                                  (let [ds  (map vec (partition dim d-flat))
                                        traj (spec/rollout system ds)
                                        rob  (spec-fn traj)]
                                    {:d d-flat :robustness rob}))
                                samples)
                    sorted  (sort-by :robustness scored)
                    elite   (take n-elite sorted)
                    elite-d (map :d elite)
                    new-mu  (mapv (fn [j]
                                   (/ (reduce + (map #(nth % j) elite-d))
                                      (double n-elite)))
                                 (range total-dim))
                    new-sigma (mapv (fn [j]
                                     (let [m (nth new-mu j)]
                                       (max 0.01
                                            (Math/sqrt
                                             (/ (reduce + (map #(let [d (- (nth % j) m)] (* d d))
                                                               elite-d))
                                                (double n-elite))))))
                                   (range total-dim))]
                {:mu    (mapv (fn [old new] (+ (* (- 1 rho) old) (* rho new))) mu new-mu)
                 :sigma (mapv (fn [old new] (+ (* (- 1 rho) old) (* rho new))) sigma new-sigma)
                 :threshold (:robustness (last elite))
                 :probability (/ (double n-elite) n-samples)}))
            {:mu    (vec (repeat total-dim 0.0))
             :sigma (vec (repeat total-dim 1.0))
             :threshold Double/MAX_VALUE
             :probability 0.0}
            (range max-iter))))

;; =============================================================
;; GIBBS SAMPLING (general)
;; =============================================================

(defn gibbs-sampling
  "Gibbs sampling with custom conditional distributions.
   Catalog: V23/D8. Pattern: ITERATE + DELEGATE.

   {Keyword -> (State -> Val)} -> State -> Int -> {:keys [burn-in]} -> [State]

   conditionals: map of variable -> (fn [current-state] -> new-value)
   Each step resamples every variable from its conditional.

   Example:
     (gibbs-sampling {:x (fn [s] (sample-x-given-y (:y s)))
                      :y (fn [s] (sample-y-given-x (:x s)))}
                     {:x 0.0 :y 0.0} 1000)"
  [conditionals init-state n & {:keys [burn-in] :or {burn-in 100}}]
  (let [step (fn [state]
               (reduce (fn [s [var cond-fn]]
                         (assoc s var (cond-fn s)))
                       state
                       conditionals))]
    (->> (iterate step init-state)
         (drop burn-in)
         (take n)
         vec)))

;; =============================================================
;; HAMILTONIAN MONTE CARLO
;; =============================================================

(defn hmc
  "Hamiltonian Monte Carlo sampler.
   Catalog: V24. Pattern: ITERATE + COMPOSE.

   (X -> R) -> (X -> X) -> X -> Opts -> [X]

   log-prob: x -> log p(x)
   grad-log-prob: x -> gradient of log p(x)
   Uses leapfrog integrator for Hamiltonian dynamics.

   Example:
     (hmc (fn [x] (- (* 0.5 (first x) (first x))))
          (fn [x] [(- (first x))])
          [0.0] :n-samples 500)"
  [log-prob grad-log-prob x0 & {:keys [n-samples step-size n-leapfrog burn-in]
                                  :or   {n-samples 500 step-size 0.1 n-leapfrog 10 burn-in 50}}]
  (let [dim (count x0)
        ;; Leapfrog integrator: compose n half-steps
        leapfrog (fn [x p]
                   (reduce (fn [[x p] _]
                             (let [p-half (mapv + p (mapv #(* 0.5 step-size %) (grad-log-prob x)))
                                   x-new  (mapv + x (mapv #(* step-size %) p-half))
                                   p-new  (mapv + p-half (mapv #(* 0.5 step-size %) (grad-log-prob x-new)))]
                               [x-new p-new]))
                           [x p]
                           (range n-leapfrog)))
        ;; One HMC step
        hmc-step (fn [x]
                   (let [p (mapv (fn [_] (- (rand) 0.5)) (range dim))  ;; sample momentum
                         H0 (- (log-prob x) (* 0.5 (reduce + (map * p p))))
                         [x' p'] (leapfrog x p)
                         H1 (- (log-prob x') (* 0.5 (reduce + (map * p' p'))))
                         accept? (<= (Math/log (rand)) (- H1 H0))]
                     (if accept? x' x)))]
    (->> (iterate hmc-step x0)
         (drop burn-in)
         (take n-samples)
         vec)))

;; =============================================================
;; DIRECT MONTE CARLO ESTIMATION
;; =============================================================

(defn direct-mc-estimation
  "Direct Monte Carlo probability estimation.
   Catalog: V26. Pattern: MAP-OVER + REDUCE.

   (() -> X) -> (X -> Bool) -> Int -> {:probability R}

   Example:
     (direct-mc-estimation sample-fn event? 10000)
     ;=> {:probability 0.05}"
  [sampler event? n]
  (let [hits (reduce (fn [count _]
                       (if (event? (sampler)) (inc count) count))
                     0
                     (range n))]
    {:probability (/ (double hits) n) :n-samples n}))

;; =============================================================
;; SEQUENTIAL MONTE CARLO
;; =============================================================

(defn sequential-mc
  "Sequential Monte Carlo (particle filter for general targets).
   Catalog: V30. Pattern: ITERATE + MAP-OVER + REDUCE.

   (X -> X) -> (X -> Obs -> R) -> [X] -> [Obs] -> [X]

   Propagates particles through dynamics, reweights by observations.

   Example:
     (sequential-mc dynamics-fn likelihood-fn initial-particles observations)"
  [dynamics-fn obs-likelihood particles observations]
  (reduce (fn [particles obs]
            (let [;; Propagate
                  propagated (mapv dynamics-fn particles)
                  ;; Weight
                  weights (mapv #(obs-likelihood % obs) propagated)
                  total (reduce + weights)
                  norm-w (if (zero? total)
                           (vec (repeat (count weights) (/ 1.0 (count weights))))
                           (mapv #(/ % total) weights))
                  ;; Systematic resample
                  n (count particles)
                  cumul (vec (reductions + norm-w))
                  step (/ 1.0 n)
                  start (* step (rand))]
              (mapv (fn [i]
                      (let [pos (+ start (* i step))
                            idx (or (reduce (fn [_ j] (when (>= (nth cumul j) pos) (reduced j)))
                                            nil (range n))
                                    (dec n))]
                        (nth propagated idx)))
                    (range n))))
          particles
          observations))
