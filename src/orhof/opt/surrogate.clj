(ns orhof.opt.surrogate
  "Surrogate-based and Bayesian optimization methods.
   Catalog: O58-O65. Pattern: MODEL BUILDER + PIPELINE COMPOSER."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

;; =============================================================
;; SAMPLING PLANS
;; =============================================================

(defn full-factorial
  "Full factorial sampling — grid over all dimension levels.
   Catalog: O58. Pattern: MAP-OVER.
   bounds: [[lo1 hi1] [lo2 hi2] ...], n-levels per dimension."
  [bounds n-levels]
  (let [levels (mapv (fn [[lo hi]]
                       (mapv (fn [i] (+ lo (* (/ (double i) (dec n-levels)) (- hi lo))))
                             (range n-levels)))
                     bounds)]
    ;; Cartesian product via reduce
    (reduce (fn [acc dim-levels]
              (for [prev acc, level dim-levels]
                (conj prev level)))
            [[]]
            levels)))

(defn latin-hypercube
  "Latin Hypercube Sampling — stratified random sampling.
   Catalog: O59. Pattern: GENERATE + MAP-OVER.
   Each dimension divided into n strata, one sample per stratum."
  [dim n]
  (let [;; For each dimension, create a random permutation of strata
        strata (mapv (fn [_]
                       (let [perm (shuffle (range n))]
                         (mapv (fn [k] (/ (+ k (rand)) n)) perm)))
                     (range dim))]
    ;; Combine: sample i gets stratum strata[d][i] for each dimension d
    (mapv (fn [i]
            (mapv (fn [d] (nth (nth strata d) i)) (range dim)))
          (range n))))

(defn sobol-sequence
  "Sobol quasi-random sequence (simplified).
   Catalog: O60. Pattern: GENERATE.
   Uses Van der Corput sequence in base 2 as approximation."
  [dim n]
  (let [;; Van der Corput in base 2
        vdc (fn [i]
              (let [bits (Long/toBinaryString i)
                    reversed (apply str (reverse bits))]
                (/ (Double/parseDouble (str "0." reversed "0")) 1.0)))
        ;; Use different bases for different dimensions (simplified)
        primes [2 3 5 7 11 13 17 19 23 29]]
    (mapv (fn [i]
            (mapv (fn [d]
                    (let [base (nth primes (mod d (count primes)))
                          ;; Van der Corput in given base
                          val (reduce (fn [acc [digit place]]
                                        (+ acc (/ (double digit) (Math/pow base place))))
                                      0.0
                                      (map-indexed (fn [j c]
                                                     [(Character/digit c 10) (inc j)])
                                                   (Long/toString (inc i) base)))]
                      val))
                  (range dim)))
          (range n))))

;; =============================================================
;; RBF SURROGATE
;; =============================================================

(defn rbf-surrogate
  "Radial Basis Function surrogate model.
   Catalog: O61. Pattern: MODEL BUILDER.
   Fits RBF interpolant: f(x) = sum_i w_i * phi(||x - x_i||).
   Returns a prediction function."
  [points values & {:keys [kernel-fn epsilon]
                     :or   {epsilon 1.0}}]
  (let [kernel-fn (or kernel-fn (fn [r] (Math/exp (- (* epsilon r r)))))
        n (count points)
        ;; Build kernel matrix K_ij = phi(||x_i - x_j||)
        K (vec (for [i (range n)]
                 (vec (for [j (range n)]
                        (kernel-fn (m/vnorm (m/v- (nth points i) (nth points j))))))))
        ;; Solve K * w = y for weights
        w (m/solve-linear K (vec values))]
    ;; Return prediction function
    (fn [x]
      (reduce + (map (fn [i]
                       (* (nth w i) (kernel-fn (m/vnorm (m/v- x (nth points i))))))
                     (range n))))))

;; =============================================================
;; GAUSSIAN PROCESS
;; =============================================================

(defn gaussian-process
  "Gaussian Process regression.
   Catalog: O62. Pattern: MODEL BUILDER.
   Fits GP with squared-exponential kernel.
   Returns a function: x -> {:mean, :variance}."
  [points values & {:keys [length-scale signal-var noise-var]
                     :or   {length-scale 1.0 signal-var 1.0 noise-var 0.01}}]
  (let [n (count points)
        ;; Squared-exponential kernel
        kernel (fn [xi xj]
                 (let [r2 (reduce + (map (fn [a b] (let [d (- a b)] (* d d))) xi xj))]
                   (* signal-var (Math/exp (/ (- r2) (* 2 length-scale length-scale))))))
        ;; Build K + noise*I
        K (vec (for [i (range n)]
                 (vec (for [j (range n)]
                        (+ (kernel (nth points i) (nth points j))
                           (if (= i j) noise-var 0.0))))))
        ;; K^{-1} * y  (via linear solve)
        y-vec (vec values)
        alpha (m/solve-linear K y-vec)]
    ;; Return prediction function
    (fn [x]
      (let [k-star (mapv (fn [i] (kernel x (nth points i))) (range n))
            ;; Posterior mean: k* . alpha
            mean (m/dot k-star alpha)
            ;; Posterior variance: k(x,x) - k*^T K^{-1} k*
            k-xx (kernel x x)
            v    (m/solve-linear K k-star)
            variance (max 0.0 (- k-xx (m/dot k-star v)))]
        {:mean mean :variance variance}))))

;; =============================================================
;; ACQUISITION FUNCTIONS
;; =============================================================

(defn expected-improvement
  "Expected Improvement acquisition function.
   Catalog: O63. Pattern: COMPOSE + PARAMETERIZE.
   Returns a function: x -> EI value (higher = more promising)."
  [gp-predict best-so-far]
  (fn [x]
    (let [{:keys [mean variance]} (gp-predict x)
          sigma (Math/sqrt (max variance 1e-30))
          z     (/ (- best-so-far mean) sigma)
          ;; EI = sigma * (z * Phi(z) + phi(z))
          ;; Approximate standard normal CDF and PDF
          phi-z (/ (Math/exp (/ (- (* z z)) 2.0)) (Math/sqrt (* 2 Math/PI)))
          ;; Phi(z) approximation via logistic function
          Phi-z (/ 1.0 (+ 1.0 (Math/exp (* -1.7 z))))]
      (max 0.0 (* sigma (+ (* z Phi-z) phi-z))))))

(defn upper-confidence-bound
  "Upper Confidence Bound acquisition function.
   Catalog: O64. Pattern: COMPOSE + PARAMETERIZE.
   UCB(x) = -mean(x) + kappa * sqrt(variance(x)).
   (Negated mean because we minimize.)"
  [gp-predict & {:keys [kappa] :or {kappa 2.0}}]
  (fn [x]
    (let [{:keys [mean variance]} (gp-predict x)]
      (+ (- mean) (* kappa (Math/sqrt (max variance 0.0)))))))

;; =============================================================
;; BAYESIAN OPTIMIZATION LOOP
;; =============================================================

(defn bayesian-optimization
  "Bayesian Optimization loop.
   Catalog: O65. Pattern: PIPELINE COMPOSER (iterate: fit GP -> maximize EI -> evaluate).
   f: objective function to minimize.
   bounds: [lower upper] vectors.
   Returns {:best-x, :best-value, :history}."
  [f bounds & {:keys [n-init n-iter]
                :or   {n-init 5 n-iter 20}}]
  (let [[lo hi] bounds
        dim    (count lo)
        ;; Initial samples via Latin Hypercube
        init-xs (mapv (fn [sample]
                        (mapv (fn [d s] (+ (nth lo d) (* s (- (nth hi d) (nth lo d)))))
                              (range dim) sample))
                      (latin-hypercube dim n-init))
        init-ys (mapv f init-xs)]
    ;; Iterate: fit GP -> find best acquisition point -> evaluate
    (reduce (fn [{:keys [xs ys best-x best-value]} _iter]
              (let [gp      (gaussian-process xs ys)
                    ei      (expected-improvement gp best-value)
                    ;; Search for best EI via random sampling (simplified)
                    candidates (repeatedly 100
                                 (fn [] (mapv (fn [d]
                                                (+ (nth lo d) (* (rand) (- (nth hi d) (nth lo d)))))
                                              (range dim))))
                    best-cand (apply max-key ei candidates)
                    y-new     (f best-cand)
                    xs-new    (conj xs best-cand)
                    ys-new    (conj ys y-new)
                    [bx bv]   (if (< y-new best-value) [best-cand y-new] [best-x best-value])]
                {:xs xs-new :ys ys-new :best-x bx :best-value bv}))
            (let [best-idx (apply min-key #(nth init-ys %) (range n-init))]
              {:xs init-xs :ys init-ys
               :best-x (nth init-xs best-idx) :best-value (nth init-ys best-idx)})
            (range n-iter))))
