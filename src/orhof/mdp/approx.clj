(ns orhof.mdp.approx
  "Approximate value functions: nearest neighbor, kernel, linear, neural.
   Catalog: D25-D30. Pattern: MODEL BUILDER + ITERATE."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

;; =============================================================
;; NEAREST NEIGHBOR VF
;; =============================================================

(defn nearest-neighbor-vf
  "Nearest-neighbor value function approximation.
   Catalog: D25. Pattern: PARAMETERIZE + REDUCE.

   {State -> R} -> (State -> R)

   Returns a function that looks up the value of the closest
   state in the data.

   Example:
     (def vf (nearest-neighbor-vf {[0 0] 1.0 [1 1] 5.0}))
     (vf [0 0]) ;=> 1.0"
  [data & {:keys [distance-fn k] :or {k 1}}]
  (let [distance-fn (or distance-fn
                        (fn [a b]
                          (if (and (sequential? a) (sequential? b))
                            (Math/sqrt (reduce + (map (fn [ai bi] (let [d (- (double ai) (double bi))] (* d d)))
                                                      a b)))
                            (Math/abs (double (- (double (hash a)) (double (hash b))))))))
        entries (vec data)]
    (fn [state]
      (let [;; Find k nearest neighbors
            sorted (sort-by (fn [[s _]] (distance-fn state s)) entries)
            neighbors (take k sorted)]
        (/ (reduce + (map second neighbors)) (double (count neighbors)))))))

;; =============================================================
;; KERNEL SMOOTHING VF
;; =============================================================

(defn kernel-smoothing-vf
  "Kernel-smoothed value function approximation.
   Catalog: D26. Pattern: PARAMETERIZE + REDUCE.

   {State -> R} -> {:keys [bandwidth]} -> (State -> R)

   Returns a Nadaraya-Watson kernel regression estimator.

   Example:
     (def vf (kernel-smoothing-vf {[0] 0.0 [1] 1.0 [2] 4.0} :bandwidth 0.5))
     (vf [1.5]) ;=> ~2.5"
  [data & {:keys [bandwidth kernel-fn]
            :or   {bandwidth 1.0}}]
  (let [kernel-fn (or kernel-fn
                      (fn [d] (Math/exp (/ (- (* d d)) (* 2 bandwidth bandwidth)))))
        entries (vec data)]
    (fn [state]
      (let [weights (mapv (fn [[s _]]
                            (let [d (if (sequential? state)
                                      (m/vnorm (mapv - (mapv double state) (mapv double s)))
                                      (Math/abs (- (double state) (double s))))]
                              (kernel-fn d)))
                          entries)
            Z (reduce + weights)]
        (if (< Z 1e-30) 0.0
            (/ (reduce + (map (fn [w [_ v]] (* w v)) weights entries)) Z))))))

;; =============================================================
;; LINEAR INTERPOLATION VF
;; =============================================================

(defn linear-interpolation-vf
  "Linear interpolation value function on a 1D grid.
   Catalog: D27. Pattern: PARAMETERIZE.

   [[R R]] -> (R -> R)

   grid-data: sorted vector of [x value] pairs.

   Example:
     (def vf (linear-interpolation-vf [[0.0 0.0] [1.0 2.0] [2.0 6.0]]))
     (vf 0.5) ;=> 1.0"
  [grid-data]
  (let [sorted (vec (sort-by first grid-data))]
    (fn [x]
      (let [x (double x)]
        (cond
          (<= x (first (first sorted))) (second (first sorted))
          (>= x (first (last sorted)))  (second (last sorted))
          :else
          (let [;; Find bracketing pair
                right-idx (reduce (fn [_ i]
                                    (when (>= (first (nth sorted i)) x)
                                      (reduced i)))
                                  nil
                                  (range (count sorted)))
                left-idx  (dec right-idx)
                [x0 v0]   (nth sorted left-idx)
                [x1 v1]   (nth sorted right-idx)
                t         (/ (- x x0) (- x1 x0))]
            (+ (* (- 1 t) v0) (* t v1))))))))

;; =============================================================
;; LINEAR REGRESSION VF
;; =============================================================

(defn linear-regression-vf
  "Linear regression value function: V(s) = w^T phi(s).
   Catalog: D29. Pattern: PARAMETERIZE + REDUCE.

   (State -> [R]) -> [[State R]] -> (State -> R)

   feature-fn maps states to feature vectors.
   Fits weights via least squares.

   Example:
     (def vf (linear-regression-vf (fn [s] [(first s) (second s) 1.0])
                                    [[[0 0] 0.0] [[1 0] 2.0] [[0 1] 3.0]]))
     (vf [1 1]) ;=> ~5.0"
  [feature-fn data]
  (let [;; Build Phi matrix and y vector
        xs (mapv (fn [[s _]] (feature-fn s)) data)
        ys (mapv second data)
        ;; Normal equations: (Phi^T Phi) w = Phi^T y
        PhiT  (m/mat-transpose xs)
        PhiTPhi (m/mat-mult PhiT xs)
        PhiTy   (m/mat-vec PhiT ys)
        ;; Add small regularization for numerical stability
        n (count (first xs))
        reg (m/mat-scale 1e-6 (m/identity-matrix n))
        w (m/solve-linear (m/mat-add PhiTPhi reg) PhiTy)]
    (fn [state]
      (m/dot w (feature-fn state)))))

;; =============================================================
;; SIMPLE NEURAL NETWORK VF
;; =============================================================

(defn neural-network-vf
  "Simple feedforward neural network value function.
   Catalog: D30. Pattern: PARAMETERIZE + ITERATE.

   [Int] -> [[State R]] -> (State -> R)

   layer-sizes: [input hidden ... output] dimensions.
   Single hidden layer with tanh activation. Trained via GD.

   Example:
     (def vf (neural-network-vf [2 4 1] training-data :max-iter 500))"
  [layer-sizes data & {:keys [max-iter lr] :or {max-iter 200 lr 0.01}}]
  (let [;; Initialize random weights
        init-weights (mapv (fn [[n-in n-out]]
                             (mapv (fn [_]
                                     (mapv (fn [_] (* 0.1 (- (rand) 0.5)))
                                           (range (inc n-in))))  ;; +1 for bias
                                   (range n-out)))
                           (partition 2 1 layer-sizes))
        ;; Forward pass
        forward (fn [weights x]
                  (reduce (fn [activations layer-w]
                            (mapv (fn [neuron-w]
                                    (let [z (+ (reduce + (map * activations (butlast neuron-w)))
                                               (last neuron-w))]  ;; bias
                                      (Math/tanh z)))
                                  layer-w))
                          (vec (map double x))
                          weights))
        ;; Mean squared error
        mse (fn [weights]
              (/ (reduce + (map (fn [[s target]]
                                  (let [pred (first (forward weights (if (sequential? s) s [s])))
                                        err (- pred target)]
                                    (* err err)))
                                data))
                 (double (count data))))]
    ;; Train via numerical gradient descent (simplified)
    (let [final-weights
          (reduce (fn [weights _]
                    (let [;; Numerical gradient on each weight
                          grad (mapv (fn [li layer]
                                       (mapv (fn [ni neuron]
                                               (mapv (fn [wi _w]
                                                       (let [h 1e-4
                                                             w+ (assoc-in weights [li ni wi] (+ _w h))
                                                             w- (assoc-in weights [li ni wi] (- _w h))]
                                                         (/ (- (mse w+) (mse w-)) (* 2 h))))
                                                     (range (count neuron)) neuron))
                                             (range (count layer)) layer))
                                     (range (count weights)) weights)]
                      ;; Update weights
                      (mapv (fn [layer grad-layer]
                              (mapv (fn [neuron grad-neuron]
                                      (mapv (fn [w gw] (- w (* lr gw))) neuron grad-neuron))
                                    layer grad-layer))
                            weights grad)))
                  init-weights
                  (range max-iter))]
      (fn [state]
        (first (forward final-weights (if (sequential? state) state [state])))))))
