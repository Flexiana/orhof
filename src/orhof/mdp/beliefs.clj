(ns orhof.mdp.beliefs
  "Belief state methods: discrete filter, Kalman, particle filter, POMDP.
   Pattern: BELIEF UPDATER — (model) -> (belief, obs) -> belief'."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

;; =============================================================
;; DISCRETE BAYESIAN FILTER (existing)
;; =============================================================

(defn discrete-filter
  "Discrete Bayesian filter for state estimation.
   Catalog: D66. Pattern: BELIEF UPDATER.

   (State -> Action -> {State -> Prob}) -> (State -> Obs -> Prob)
   -> (Belief -> Action -> Obs -> Belief)

   Example:
     (def updater (discrete-filter transition-model observation-model))
     (updater {:a 0.5 :b 0.5} :move :obs-x)
     ;=> {:a 0.3 :b 0.7}"
  [transition-model observation-model]
  (c/make-updater
   {:predict (fn [belief action]
               (let [states (keys belief)]
                 (into {}
                       (map (fn [s']
                              [s' (reduce + (map (fn [s]
                                                   (* (get belief s 0.0)
                                                      (get (transition-model s action) s' 0.0)))
                                                 states))])
                            states))))
    :update (fn [predicted obs]
              (let [unnorm (into {} (map (fn [[s p]]
                                           [s (* p (observation-model s obs))])
                                         predicted))
                    Z (reduce + (vals unnorm))]
                (if (zero? Z) predicted
                    (into {} (map (fn [[s p]] [s (/ p Z)]) unnorm)))))}))

;; =============================================================
;; KALMAN FILTER
;; =============================================================

(defn kalman-filter
  "Kalman filter for linear-Gaussian state estimation.
   Catalog: D67. Pattern: BELIEF UPDATER + ITERATE.

   [[R]] -> [[R]] -> [[R]] -> [[R]]
   -> ({:mean [R] :cov [[R]]} -> [R] -> {:mean [R] :cov [[R]]})

   A: state transition matrix
   C: observation matrix
   Q: process noise covariance
   R: observation noise covariance

   Returns an update function: (state, observation) -> state'.

   Example:
     (def kf (kalman-filter A C Q R))
     (kf {:mean [0 0] :cov [[1 0][0 1]]} [5.0])
     ;=> {:mean [4.5 0.0] :cov [[0.5 0.0][0.0 1.0]]}"
  [A C Q R]
  (fn [{:keys [mean cov]} observation]
    (let [;; Predict: x' = A*x, P' = A*P*A^T + Q
          x-pred (m/mat-vec A mean)
          AT     (m/mat-transpose A)
          P-pred (m/mat-add (m/mat-mult A (m/mat-mult cov AT)) Q)
          ;; Innovation: y = z - C*x'
          CT     (m/mat-transpose C)
          z-pred (m/mat-vec C x-pred)
          y      (mapv - observation z-pred)
          ;; Innovation covariance: S = C*P'*C^T + R
          S      (m/mat-add (m/mat-mult C (m/mat-mult P-pred CT)) R)
          ;; Kalman gain: K = P'*C^T*S^{-1}
          ;; Solve S*K^T = C*P'^T for K^T, then transpose
          ;; Simplified: K_col = solve(S, (C*P')^T_col) for each col
          PCt    (m/mat-mult P-pred CT)
          ;; K = PCt * S^{-1} => for each column of PCt, solve S*k = pct_col
          K      (mapv (fn [row-pct]
                         (m/solve-linear S row-pct))
                       PCt)
          ;; Update: x = x' + K*y
          Ky     (m/mat-vec K y)
          x-upd  (mapv + x-pred Ky)
          ;; Update covariance: P = (I - K*C)*P'
          n      (count mean)
          KC     (m/mat-mult K C)
          I-KC   (m/mat-add (m/identity-matrix n) (m/mat-scale -1.0 KC))
          P-upd  (m/mat-mult I-KC P-pred)]
      {:mean x-upd :cov P-upd})))

;; =============================================================
;; EXTENDED KALMAN FILTER
;; =============================================================

(defn extended-kalman-filter
  "Extended Kalman Filter for nonlinear state estimation.
   Catalog: D68. Pattern: BELIEF UPDATER + DELEGATE.

   (State -> State) -> (State -> Obs) -> (State -> [[R]]) -> (State -> [[R]])
   -> [[R]] -> [[R]]
   -> ({:mean [R] :cov [[R]]} -> [R] -> {:mean [R] :cov [[R]]})

   dynamics-fn: state -> predicted state (nonlinear)
   observe-fn: state -> predicted observation (nonlinear)
   jacobian-f: state -> Jacobian of dynamics
   jacobian-h: state -> Jacobian of observation
   Q, R: noise covariances"
  [dynamics-fn observe-fn jacobian-f jacobian-h Q R]
  (fn [{:keys [mean cov]} observation]
    (let [;; Linearize at current estimate
          F  (jacobian-f mean)
          H  (jacobian-h mean)
          ;; Predict
          x-pred (dynamics-fn mean)
          FT     (m/mat-transpose F)
          P-pred (m/mat-add (m/mat-mult F (m/mat-mult cov FT)) Q)
          ;; Update (same as standard Kalman with linearized matrices)
          HT     (m/mat-transpose H)
          z-pred (observe-fn x-pred)
          y      (mapv - observation z-pred)
          S      (m/mat-add (m/mat-mult H (m/mat-mult P-pred HT)) R)
          PHt    (m/mat-mult P-pred HT)
          K      (mapv (fn [row] (m/solve-linear S row)) PHt)
          x-upd  (mapv + x-pred (m/mat-vec K y))
          n      (count mean)
          KC     (m/mat-mult K H)
          P-upd  (m/mat-mult (m/mat-add (m/identity-matrix n) (m/mat-scale -1.0 KC)) P-pred)]
      {:mean x-upd :cov P-upd})))

;; =============================================================
;; PARTICLE FILTER
;; =============================================================

(defn particle-filter
  "Sequential Monte Carlo (particle filter).
   Catalog: D70. Pattern: ITERATE + MAP-OVER + REDUCE + GENERATE.

   (State -> State) -> (State -> Obs -> R) -> Int
   -> ([State] -> Obs -> [State])

   dynamics-fn: state -> next state (with noise built in)
   obs-likelihood: (state, obs) -> likelihood
   n-particles: number of particles

   Returns an update function: (particles, observation) -> particles'.

   Example:
     (def pf (particle-filter dynamics obs-lik 100))
     (pf initial-particles 3.0)
     ;=> [updated particles...]"
  [dynamics-fn obs-likelihood n-particles]
  (fn [particles observation]
    (let [;; Propagate particles through dynamics
          propagated (mapv dynamics-fn particles)
          ;; Weight by observation likelihood
          weights (mapv (fn [x] (obs-likelihood x observation)) propagated)
          total-w (reduce + weights)
          norm-w  (if (zero? total-w)
                    (vec (repeat (count weights) (/ 1.0 (count weights))))
                    (mapv #(/ % total-w) weights))
          ;; Systematic resampling
          cumulative (vec (reductions + norm-w))
          step       (/ 1.0 n-particles)
          start      (* step (rand))
          positions  (mapv (fn [i] (+ start (* i step))) (range n-particles))]
      ;; Select particles by cumulative weights
      (mapv (fn [pos]
              (let [idx (reduce (fn [_ i]
                                  (when (>= (nth cumulative i) pos)
                                    (reduced i)))
                                nil
                                (range (count cumulative)))]
                (nth propagated (or idx (dec (count propagated))))))
            positions))))

;; =============================================================
;; ALPHA VECTORS (Exact POMDP)
;; =============================================================

(defn alpha-vector-backup
  "One step of alpha vector backup for exact POMDP solving.
   Catalog: D71. Pattern: ITERATE + REDUCE.

   MDP-like -> [[R]] -> [[R]]

   Given a set of alpha vectors representing the value function,
   computes the backup (new alpha vectors).
   Simplified for pedagogical purposes."
  [states actions transition-fn reward-fn obs-fn gamma alpha-vectors]
  ;; For each action, compute the backed-up alpha vector
  (mapv (fn [a]
          (mapv (fn [s]
                  (+ (reward-fn s a)
                     (* gamma
                        (reduce + (map (fn [s']
                                         (* (get (transition-fn s a) s' 0.0)
                                            (apply max (map (fn [alpha]
                                                              (nth alpha (.indexOf (vec states) s')))
                                                            alpha-vectors))))
                                       states)))))
                states))
        actions))
