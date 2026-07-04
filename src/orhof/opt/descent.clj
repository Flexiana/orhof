(ns orhof.opt.descent
  "Gradient-based descent methods.
   All methods instantiate the generic descent framework with
   different direction-fn and step-fn strategies.
   Pattern: ITERATE + DELEGATE (Parameterized Strategy)."
  (:require [orhof.core :as c]
            [orhof.diff :as d]))

;; =============================================================
;; GENERIC DESCENT FRAMEWORK
;; =============================================================

(defn descent
  "Generic descent framework. All gradient-based optimizers are
   instances of this pattern with different direction-fn and step-fn.
   Catalog: O13. Pattern: ITERATE + DELEGATE.

   direction-fn: state -> direction-vector
   step-fn:      state direction -> step-size
   f:            objective function Rn -> R
   x0:           initial point
   grad-fn:      gradient function (default: numerical)"
  [{:keys [direction-fn step-fn f x0 grad-fn tol max-iter]
    :or   {tol 1e-8 max-iter 1000}}]
  (let [grad-fn (or grad-fn (d/gradient f))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-fn x0) :prev-value Double/MAX_VALUE}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [dir   (direction-fn state)
                          alpha (step-fn state dir)
                          x-new (c/v+ (:x state) (c/v* alpha dir))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-fn x-new)
                       :prev-value (:value state)
                       :direction  dir
                       :step-size  alpha}))})))

;; =============================================================
;; LINE SEARCH
;; =============================================================

(defn backtracking-line-search
  "Backtracking line search (Armijo condition).
   Catalog: O14. Pattern: ITERATE.
   Returns step size."
  [f grad-f x dir & {:keys [alpha beta c1]
                      :or   {alpha 1.0 beta 0.5 c1 1e-4}}]
  (let [fx    (f x)
        slope (c/dot (grad-f x) dir)]
    (->> (iterate #(* beta %) alpha)
         (drop-while (fn [a]
                       (and (> a 1e-16)
                            (> (f (c/v+ x (c/v* a dir)))
                               (+ fx (* c1 a slope))))))
         first)))

;; =============================================================
;; FIRST-ORDER METHODS
;; =============================================================

(defn gradient-descent
  "Gradient descent with fixed learning rate.
   Catalog: O17. Pattern: ITERATE."
  [f x0 & {:keys [lr tol max-iter]
            :or   {lr 0.01 tol 1e-8 max-iter 1000}}]
  (descent {:f            f
            :x0           x0
            :tol          tol
            :max-iter     max-iter
            :direction-fn (fn [s] (c/v* -1 (:grad s)))
            :step-fn      (fn [_ _] lr)}))

(defn steepest-descent
  "Steepest descent with backtracking line search.
   Catalog: O17 variant. Pattern: ITERATE + DELEGATE."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (descent {:f            f
              :x0           x0
              :tol          tol
              :max-iter     max-iter
              :direction-fn (fn [s] (c/v* -1 (:grad s)))
              :step-fn      (fn [s dir]
                              (backtracking-line-search f grad-f (:x s) dir))})))

(defn conjugate-gradient
  "Conjugate gradient method (Fletcher-Reeves).
   Catalog: O18. Pattern: ITERATE + DELEGATE."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :dir (c/v* -1 (grad-f x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [dir   (:dir state)
                          alpha (backtracking-line-search f grad-f (:x state) dir)
                          x-new (c/v+ (:x state) (c/v* alpha dir))
                          g-new (grad-f x-new)
                          g-old (:grad state)
                          beta  (/ (c/dot g-new g-new)
                                   (max 1e-30 (c/dot g-old g-old)))
                          d-new (c/v+ (c/v* -1 g-new) (c/v* beta dir))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       g-new
                       :prev-value (:value state)
                       :dir        d-new}))})))

;; =============================================================
;; MOMENTUM VARIANTS
;; =============================================================

(defn momentum-gd
  "Gradient descent with Polyak momentum.
   Catalog: O19. Pattern: ITERATE (velocity is part of state)."
  [f x0 & {:keys [lr beta tol max-iter]
            :or   {lr 0.01 beta 0.9 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :velocity (c/v-zero (count x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad velocity]} state
                          v-new (c/v+ (c/v* beta velocity) (c/v* (- lr) grad))
                          x-new (c/v+ x v-new)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :velocity   v-new}))})))

(defn with-momentum
  "Wrap a gradient-descent step with Polyak momentum.
   Returns a step-fn that maintains velocity in the state map.
   Catalog: O19. Pattern: WRAP (decorator)."
  [base-lr beta]
  (fn [state]
    (let [g     (:grad state)
          v     (or (:velocity state) (c/v-zero (count g)))
          v-new (c/v+ (c/v* beta v) (c/v* (- base-lr) g))
          x-new (c/v+ (:x state) v-new)]
      (assoc state :x x-new :velocity v-new))))

(defn adam
  "Adam optimizer.
   Catalog: O24. Pattern: ITERATE (first + second moments in state)."
  [f x0 & {:keys [lr beta1 beta2 eps tol max-iter]
            :or   {lr 0.001 beta1 0.9 beta2 0.999
                   eps 1e-8 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :m (c/v-zero (count x0))
                   :v (c/v-zero (count x0))
                   :t 1}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad m v t]} state
                          m-new  (c/v+ (c/v* beta1 m) (c/v* (- 1 beta1) grad))
                          v-new  (c/v+ (c/v* beta2 v)
                                       (mapv #(* (- 1 beta2) % %) grad))
                          m-hat  (c/v* (/ 1.0 (- 1 (Math/pow beta1 t))) m-new)
                          v-hat  (c/v* (/ 1.0 (- 1 (Math/pow beta2 t))) v-new)
                          x-new  (mapv (fn [xi mi vi]
                                         (- xi (/ (* lr mi) (+ (Math/sqrt vi) eps))))
                                       x m-hat v-hat)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :m          m-new
                       :v          v-new
                       :t          (inc t)}))})))

(defn adamw
  "AdamW optimizer — Adam with decoupled weight decay.
   Catalog: O24 variant. Pattern: ITERATE."
  [f x0 & {:keys [lr beta1 beta2 eps weight-decay tol max-iter]
            :or   {lr 0.001 beta1 0.9 beta2 0.999
                   eps 1e-8 weight-decay 0.01 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :m (c/v-zero (count x0))
                   :v (c/v-zero (count x0))
                   :t 1}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad m v t]} state
                          m-new     (c/v+ (c/v* beta1 m) (c/v* (- 1 beta1) grad))
                          v-new     (c/v+ (c/v* beta2 v)
                                         (mapv #(* (- 1 beta2) % %) grad))
                          m-hat     (c/v* (/ 1.0 (- 1 (Math/pow beta1 t))) m-new)
                          v-hat     (c/v* (/ 1.0 (- 1 (Math/pow beta2 t))) v-new)
                          x-decayed (c/v+ x (c/v* (- weight-decay) x))
                          x-new     (mapv (fn [xi mi vi]
                                            (- xi (/ (* lr mi) (+ (Math/sqrt vi) eps))))
                                          x-decayed m-hat v-hat)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :m          m-new
                       :v          v-new
                       :t          (inc t)}))})))

(defn lion
  "Lion optimizer (EvoLved Sign Momentum).
   Catalog: O24 variant. Pattern: ITERATE."
  [f x0 & {:keys [lr beta1 beta2 weight-decay tol max-iter]
            :or   {lr 1e-4 beta1 0.9 beta2 0.99
                   weight-decay 0.0 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :m (c/v-zero (count x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad m]} state
                          c-vec (c/v+ (c/v* beta1 m) (c/v* (- 1 beta1) grad))
                          x-new (mapv (fn [xi ci]
                                        (- (* xi (- 1.0 (* lr weight-decay)))
                                           (* lr (Math/signum ci))))
                                      x c-vec)
                          m-new (c/v+ (c/v* beta2 m) (c/v* (- 1 beta2) grad))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :m          m-new}))})))

;; =============================================================
;; NESTEROV MOMENTUM
;; =============================================================

(defn nesterov-momentum
  "Nesterov accelerated gradient.
   Catalog: O20. Pattern: ITERATE + WRAP.
   Evaluates gradient at the look-ahead position x + beta*v,
   not at x. This gives better convergence on convex problems."
  [f x0 & {:keys [lr beta tol max-iter]
            :or   {lr 0.01 beta 0.9 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :velocity (c/v-zero (count x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad velocity]} state
                          ;; Look-ahead: evaluate gradient at x + beta*v
                          x-lookahead (c/v+ x (c/v* beta velocity))
                          g-lookahead (grad-f x-lookahead)
                          ;; Update velocity and position
                          v-new (c/v+ (c/v* beta velocity) (c/v* (- lr) g-lookahead))
                          x-new (c/v+ x v-new)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :velocity   v-new}))})))

;; =============================================================
;; ADAGRAD
;; =============================================================

(defn adagrad
  "AdaGrad optimizer — per-parameter adaptive learning rates.
   Catalog: O21. Pattern: ITERATE + WRAP.
   Accumulates squared gradients; parameters with large gradients
   get smaller effective learning rates."
  [f x0 & {:keys [lr eps tol max-iter]
            :or   {lr 0.5 eps 1e-8 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :sum-sq-grad (c/v-zero (count x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad sum-sq-grad]} state
                          ;; Accumulate squared gradients
                          ssg-new (mapv (fn [si gi] (+ si (* gi gi)))
                                       sum-sq-grad grad)
                          ;; Adaptive step: x_new = x - lr * g / sqrt(ssg + eps)
                          x-new (mapv (fn [xi gi si]
                                        (- xi (/ (* lr gi) (+ (Math/sqrt si) eps))))
                                      x grad ssg-new)]
                      {:x            x-new
                       :value        (f x-new)
                       :grad         (grad-f x-new)
                       :prev-value   (:value state)
                       :sum-sq-grad  ssg-new}))})))

;; =============================================================
;; RMSPROP
;; =============================================================

(defn rmsprop
  "RMSProp optimizer — exponential moving average of squared gradients.
   Catalog: O22. Pattern: ITERATE + WRAP.
   Like AdaGrad but with decay, preventing learning rate from
   shrinking to zero."
  [f x0 & {:keys [lr beta eps tol max-iter]
            :or   {lr 0.01 beta 0.9 eps 1e-8 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :v (c/v-zero (count x0))}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad v]} state
                          ;; Exponential moving avg of squared gradients
                          v-new (mapv (fn [vi gi] (+ (* beta vi) (* (- 1 beta) gi gi)))
                                      v grad)
                          ;; Adaptive step
                          x-new (mapv (fn [xi gi vi]
                                        (- xi (/ (* lr gi) (+ (Math/sqrt vi) eps))))
                                      x grad v-new)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :v          v-new}))})))

;; =============================================================
;; ADADELTA
;; =============================================================

(defn adadelta
  "Adadelta optimizer — no learning rate required.
   Catalog: O23. Pattern: ITERATE + WRAP.
   Uses ratio of accumulated parameter deltas to accumulated
   gradients, automatically adapting the step size."
  [f x0 & {:keys [rho eps tol max-iter]
            :or   {rho 0.95 eps 1e-6 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :eg2 (c/v-zero (count x0))   ;; E[g^2]
                   :edx2 (c/v-zero (count x0))} ;; E[dx^2]
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad eg2 edx2]} state
                          ;; Accumulate gradient: E[g^2] = rho*E[g^2] + (1-rho)*g^2
                          eg2-new (mapv (fn [egi gi] (+ (* rho egi) (* (- 1 rho) gi gi)))
                                       eg2 grad)
                          ;; Compute step: dx = -sqrt(E[dx^2]+eps) / sqrt(E[g^2]+eps) * g
                          dx (mapv (fn [gi egi edxi]
                                     (* (- (/ (Math/sqrt (+ edxi eps))
                                              (Math/sqrt (+ egi eps))))
                                        gi))
                                   grad eg2-new edx2)
                          ;; Accumulate deltas: E[dx^2] = rho*E[dx^2] + (1-rho)*dx^2
                          edx2-new (mapv (fn [edxi dxi] (+ (* rho edxi) (* (- 1 rho) dxi dxi)))
                                        edx2 dx)
                          x-new (c/v+ x dx)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :eg2        eg2-new
                       :edx2       edx2-new}))})))

;; =============================================================
;; HYPERGRADIENT DESCENT
;; =============================================================

(defn hypergradient-descent
  "Hypergradient descent — adapts the learning rate online.
   Catalog: O25. Pattern: ITERATE + WRAP.
   Computes the gradient of the loss w.r.t. the learning rate,
   then adjusts lr each step using a meta learning rate."
  [f x0 & {:keys [lr meta-lr tol max-iter]
            :or   {lr 0.01 meta-lr 1e-4 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :lr lr
                   :prev-grad nil}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad prev-grad]} state
                          current-lr (:lr state)
                          ;; Hypergradient: d(loss)/d(lr) approx = -g_k . g_{k-1}
                          lr-new (if prev-grad
                                   (+ current-lr
                                      (* meta-lr (c/dot grad prev-grad)))
                                   current-lr)
                          ;; Clamp lr to be positive
                          lr-new (max 1e-8 (min 1.0 lr-new))
                          ;; Standard gradient step with adapted lr
                          x-new (c/v+ x (c/v* (- lr-new) grad))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :lr         lr-new
                       :prev-grad  grad}))})))
