(ns orhof.opt.stochastic
  "Stochastic optimization methods.
   Pattern: ITERATE with random perturbations."
  (:require [orhof.core :as c]
            [orhof.math :as m]
            [orhof.diff :as d]))

(defn simulated-annealing
  "Simulated annealing. Parameterized by neighbor-fn and temp-schedule.
   Catalog: O40. Pattern: ITERATE + PARAMETERIZE.

   neighbor-fn:   x -> x' (generate neighbor)
   temp-schedule: iteration -> temperature
   f:             objective function"
  [f x0 & {:keys [neighbor-fn temp-schedule max-iter]
            :or   {max-iter 10000}}]
  (let [neighbor-fn   (or neighbor-fn
                          (fn [x] (mapv #(+ % (* 0.1 (- (rand) 0.5))) x)))
        temp-schedule (or temp-schedule
                          (fn [t] (* 100.0 (Math/exp (* -0.001 t)))))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :best-x x0 :best-value (f x0)
                   :prev-value Double/MAX_VALUE :iter 0}
      :max-iter   max-iter
      :converged? (fn [s] (< (temp-schedule (:iter s)) 1e-10))
      :step-fn    (fn [state]
                    (let [x'      (neighbor-fn (:x state))
                          v'      (f x')
                          T       (temp-schedule (:iter state))
                          delta   (- v' (:value state))
                          accept? (or (<= delta 0)
                                      (< (rand) (Math/exp (/ (- delta) (max T 1e-30)))))
                          [x-next v-next] (if accept? [x' v'] [(:x state) (:value state)])
                          [bx bv] (if (< v-next (:best-value state))
                                    [x-next v-next]
                                    [(:best-x state) (:best-value state)])]
                      {:x          x-next
                       :value      v-next
                       :best-x     bx
                       :best-value bv
                       :prev-value (:value state)
                       :iter       (inc (:iter state))}))})))

;; =============================================================
;; NOISY DESCENT
;; =============================================================

(defn noisy-descent
  "Noisy gradient descent — adds Gaussian noise to the gradient.
   Catalog: O38. Pattern: ITERATE + WRAP.
   Wraps a standard gradient step with random perturbation,
   helping escape shallow local minima."
  [f x0 & {:keys [lr sigma tol max-iter]
            :or   {lr 0.01 sigma 0.1 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0) :prev-value Double/MAX_VALUE}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [g     (:grad state)
                          noise (mapv (fn [_] (* sigma (- (rand) 0.5) 2.0)) g)
                          noisy-g (c/v+ g noise)
                          x-new (c/v+ (:x state) (c/v* (- lr) noisy-g))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)}))})))

;; =============================================================
;; MADS (Mesh Adaptive Direct Search)
;; =============================================================

(defn mads
  "Mesh Adaptive Direct Search.
   Catalog: O39. Pattern: ITERATE + DELEGATE + BRANCH.
   Combines a search step (optional, e.g., random) with a poll
   step on an adaptive mesh. Mesh size decreases on failure."
  [f x0 & {:keys [mesh-size mesh-shrink mesh-grow tol max-iter]
            :or   {mesh-size 1.0 mesh-shrink 0.5 mesh-grow 2.0
                   tol 1e-8 max-iter 1000}}]
  (let [n (count x0)
        ;; Positive spanning set: +/- each coordinate
        make-dirs (fn [h]
                    (concat
                     (for [i (range n)] (assoc (c/v-zero n) i h))
                     (for [i (range n)] (assoc (c/v-zero n) i (- h)))))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE
                   :mesh mesh-size}
      :max-iter   max-iter
      :converged? (fn [state] (< (:mesh state) tol))
      :step-fn    (fn [state]
                    (let [{:keys [x value mesh]} state
                          dirs (make-dirs mesh)
                          ;; Poll: try each direction
                          improvement
                          (reduce (fn [_ dir]
                                    (let [x' (c/v+ x dir)
                                          v' (f x')]
                                      (when (< v' value)
                                        (reduced {:x x' :value v'}))))
                                  nil
                                  dirs)]
                      (if improvement
                        {:x (:x improvement) :value (:value improvement)
                         :prev-value value :mesh (* mesh mesh-grow)}
                        {:x x :value value :prev-value value
                         :mesh (* mesh mesh-shrink)})))})))

;; =============================================================
;; CMA-ES (Covariance Matrix Adaptation Evolution Strategy)
;; =============================================================

(defn cma-es
  "CMA-ES — Covariance Matrix Adaptation Evolution Strategy.
   Catalog: O42. Pattern: ITERATE + POPULATION EVOLVER.
   Maintains a full covariance matrix for sampling, adapts mean,
   step-size, and covariance from elite samples."
  [f dim & {:keys [n-samples n-elite sigma max-iter tol]
            :or   {n-samples nil n-elite nil sigma 1.0 max-iter 200 tol 1e-8}}]
  (let [n-samples (or n-samples (max 10 (* 4 dim)))
        n-elite   (or n-elite (quot n-samples 2))
        ;; Weights for recombination (log-linear)
        raw-w  (mapv (fn [i] (- (Math/log (+ n-elite 0.5)) (Math/log (inc i))))
                     (range n-elite))
        w-sum  (reduce + raw-w)
        weights (mapv #(/ % w-sum) raw-w)]
    (c/iterative-solve
     {:init-state {:mu    (c/v-zero dim)
                   :sigma sigma
                   :C     (m/identity-matrix dim)
                   :value Double/MAX_VALUE
                   :prev-value Double/MAX_VALUE
                   :iter  0}
      :max-iter   max-iter
      :converged? (fn [s] (< (:sigma s) tol))
      :step-fn    (fn [state]
                    (let [{:keys [mu sigma C]} state
                          ;; Sample from N(mu, sigma^2 * C) — simplified: diagonal C
                          diag-c (m/mat-diag C)
                          samples (repeatedly n-samples
                                              (fn [] (mapv (fn [mi ci]
                                                             (+ mi (* sigma (Math/sqrt (max ci 1e-30))
                                                                     (- (rand) 0.5) 2.0)))
                                                           mu diag-c)))
                          scored  (sort-by :value (map (fn [x] {:x x :value (f x)}) samples))
                          elite   (take n-elite scored)
                          elite-x (map :x elite)
                          ;; Weighted recombination for new mean
                          new-mu  (reduce (fn [acc [w x]] (c/v+ acc (c/v* w x)))
                                         (c/v-zero dim)
                                         (map vector weights elite-x))
                          ;; Update covariance (simplified: diagonal)
                          new-diag (mapv (fn [j]
                                          (let [mj (nth new-mu j)]
                                            (reduce + (map (fn [w x]
                                                             (let [d (- (nth x j) mj)]
                                                               (* w d d)))
                                                           weights elite-x))))
                                        (range dim))
                          new-C (m/diag-matrix (mapv #(max % 1e-30) new-diag))
                          ;; Adapt sigma (simplified: based on improvement)
                          new-sigma (* sigma (if (< (:value (first elite)) (:value state))
                                              1.0 0.9))]
                      {:mu         new-mu
                       :sigma      new-sigma
                       :C          new-C
                       :x          (:x (first elite))
                       :value      (:value (first elite))
                       :prev-value (:value state)
                       :iter       (inc (:iter state))}))})))
