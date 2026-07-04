(ns orhof.opt.constrained
  "Constrained optimization methods.
   Pattern: TRANSFORM-PROBLEM — convert constrained to unconstrained."
  (:require [orhof.core :as c]
            [orhof.math :as m]
            [orhof.diff :as d]
            [orhof.opt.descent :as desc]))

(defn penalize
  "Transform a constrained problem into an unconstrained one.
   Catalog: O48. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   constraints: seq of (fn [x] -> violation-amount), where 0 = satisfied.
   Returns a new objective function."
  [f constraints rho]
  (fn [x]
    (+ (f x)
       (* rho (reduce + (map (fn [c] (let [v (max 0.0 (c x))] (* v v)))
                              constraints))))))

(defn augmented-lagrangian
  "Augmented Lagrangian method for constrained optimization.
   Catalog: O49. Pattern: ITERATE (outer loop on multipliers).
   Outer loop updates multipliers; inner loop solves penalized problem.
   rho-max caps the penalty: the Augmented Lagrangian converges with a
   BOUNDED penalty, so rho must never grow without limit (an unbounded
   rho makes the fixed-lr inner descent explode)."
  [f constraints x0 & {:keys [rho rho-max max-outer max-inner]
                        :or   {rho 1.0 rho-max 50.0
                               max-outer 15 max-inner 200}}]
  (assert (pos? max-outer) "max-outer must be positive")
  (assert (pos? max-inner) "max-inner must be positive")
  (let [constraint-count (count constraints)
        init-state {:x x0
                    :value (f x0)
                    :lambdas (vec (repeat constraint-count 0.0))
                    :rho rho}
        make-augmented-objective
        (fn [lambdas rho]
          (fn [x]
            (+ (f x)
               (reduce +
                       (map-indexed
                         (fn [i c]
                           (let [violation (c x)
                                 linear-term (* (nth lambdas i) violation)
                                 quadratic-term (* (/ rho 2.0) violation violation)]
                             (+ linear-term quadratic-term)))
                         constraints)))))
        update-lambdas
        (fn [lambdas rho x*]
          (mapv (fn [i c]
                  (+ (nth lambdas i) (* rho (c x*))))
                (range constraint-count)
                constraints))
        outer-step
        (fn [{:keys [x lambdas rho]} _]
          (let [augmented-objective (make-augmented-objective lambdas rho)
                result (desc/gradient-descent augmented-objective x
                                              :max-iter max-inner
                                              :lr 0.01)
                x* (:x result)]
            {:x x*
             :value (f x*)
             :lambdas (update-lambdas lambdas rho x*)
             :rho (min rho-max (* 1.5 rho))}))]
    (reduce outer-step init-state (range max-outer))))

;; =============================================================
;; INTERIOR POINT / BARRIER METHOD
;; =============================================================

(defn interior-point
  "Interior point method using logarithmic barrier.
   Catalog: O50. Pattern: TRANSFORM-PROBLEM + ITERATE.
   constraints: seq of (fn [x] -> value), where g(x) <= 0 is feasible.
   Adds barrier: -mu * sum(log(-g_i(x))) to objective."
  [f constraints x0 & {:keys [mu mu-shrink tol max-outer max-inner]
                         :or   {mu 10.0 mu-shrink 0.5 tol 1e-6
                                max-outer 50 max-inner 200}}]
  (let [feasible? (fn [x]
                    (and (every? #(Double/isFinite %) x)
                         (every? neg? (map (fn [c] (c x)) constraints))))]
    (reduce (fn [{:keys [x mu]} _outer]
              (let [barrier-f (fn [x]
                                (let [gs (map (fn [c] (c x)) constraints)]
                                  (if (every? neg? gs)
                                    (+ (f x)
                                       (* (- mu) (reduce + (map #(Math/log (- %)) gs))))
                                    Double/MAX_VALUE)))
                    result (desc/gradient-descent barrier-f x :max-iter max-inner :lr 0.001)
                    x-new  (:x result)
                    ;; A fixed-lr step can overshoot the barrier; near the
                    ;; boundary the log term returns MAX_VALUE and the numeric
                    ;; gradient overflows. Keep the step only if it stays
                    ;; strictly feasible and finite, else keep the last iterate.
                    x-next (if (feasible? x-new) x-new x)]
                {:x x-next :value (f x-next) :mu (* mu mu-shrink)}))
            {:x x0 :value (f x0) :mu mu}
            (range max-outer))))

;; =============================================================
;; ADMM (Alternating Direction Method of Multipliers)
;; =============================================================

(defn admm
  "ADMM — Alternating Direction Method of Multipliers.
   Catalog: O51. Pattern: ITERATE + DELEGATE (3-step split).
   Splits problem into x-update, z-update, and dual-update.

   x-update-fn: (fn [z u rho] -> x-new)
   z-update-fn: (fn [x u] -> z-new)
   x0: initial x value"
  [x-update-fn z-update-fn x0 & {:keys [rho max-iter tol]
                                   :or   {rho 1.0 max-iter 200 tol 1e-6}}]
  (c/iterative-solve
   {:init-state {:x x0 :z x0 :u 0.0 :iter 0
                 :value Double/MAX_VALUE :prev-value Double/MAX_VALUE}
    :max-iter   max-iter
    :converged? (fn [state]
                  ;; Converge when primal residual |x - z| is small (skip first iter)
                  (and (pos? (or (:iter state) 0))
                       (< (Math/abs (- (double (:x state)) (double (:z state)))) tol)))
    :step-fn    (fn [state]
                  (let [{:keys [z u]} state
                        x-new (x-update-fn z u rho)
                        z-new (z-update-fn x-new u)
                        u-new (+ u (- (double x-new) (double z-new)))
                        residual (Math/abs (- (double x-new) (double z-new)))]
                    {:x x-new :z z-new :u u-new :iter (inc (or (:iter state) 0))
                     :value residual :prev-value (:value state)}))}))

;; =============================================================
;; DUAL ASCENT
;; =============================================================

(defn dual-ascent
  "Dual ascent for equality-constrained optimization.
   Catalog: O52. Pattern: ITERATE + TRANSFORM-PROBLEM.
   min f(x) s.t. h_i(x) = 0.
   Outer loop: gradient ascent on dual variables lambda.
   Inner loop: minimize Lagrangian L(x, lambda) over x."
  [f eq-constraints x0 & {:keys [lr max-iter max-inner]
                            :or   {lr 0.1 max-iter 200 max-inner 100}}]
  (reduce (fn [{:keys [x lambdas]} _outer]
            (let [;; Lagrangian: L(x, lambda) = f(x) + sum_i lambda_i * h_i(x)
                  lagrangian (fn [x]
                               (+ (f x)
                                  (reduce + (map (fn [lam h] (* lam (h x)))
                                                 lambdas eq-constraints))))
                  ;; Minimize Lagrangian over x
                  result (desc/gradient-descent lagrangian x :max-iter max-inner :lr 0.01)
                  x-new  (:x result)
                  ;; Dual ascent: lambda_i += lr * h_i(x)
                  new-lambdas (mapv (fn [lam h] (+ lam (* lr (h x-new))))
                                    lambdas eq-constraints)]
              {:x x-new :value (f x-new) :lambdas new-lambdas}))
          {:x x0 :value (f x0) :lambdas (vec (repeat (count eq-constraints) 0.0))}
          (range max-iter)))

;; =============================================================
;; KKT CONDITIONS CHECK
;; =============================================================

(defn kkt-check
  "Check KKT (Karush-Kuhn-Tucker) conditions at a point.
   Catalog: O53. Pattern: SENSITIVITY ANALYZER.
   Returns a map of violations:
   :stationarity  — norm of grad_L
   :primal        — max constraint violation
   :dual          — any lambda < 0
   :complementary — max |lambda_i * g_i(x)|"
  [f constraints x lambdas]
  (let [grad-f ((d/gradient f) x)
        grad-gs (map (fn [c] ((d/gradient c) x)) constraints)
        ;; Stationarity: grad f + sum(lambda_i * grad g_i) = 0
        grad-L (reduce (fn [acc [lam grad-g]]
                         (c/v+ acc (c/v* lam grad-g)))
                       grad-f
                       (map vector lambdas grad-gs))
        ;; Primal feasibility: g_i(x) <= 0
        violations (mapv (fn [c] (c x)) constraints)
        ;; Dual feasibility: lambda_i >= 0
        ;; Complementary slackness: lambda_i * g_i(x) = 0
        complementary (mapv (fn [lam c] (* lam (c x))) lambdas constraints)]
    {:stationarity       (c/vnorm grad-L)
     :primal-feasibility (apply max (map #(max 0.0 %) violations))
     :dual-feasibility   (apply min lambdas)
     :complementary      (apply max (map #(Math/abs %) complementary))}))
