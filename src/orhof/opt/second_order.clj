(ns orhof.opt.second-order
  "Second-order optimization methods using Hessian or approximate Hessian.
   Pattern: ITERATE + DELEGATE (direction uses curvature information)."
  (:require [orhof.core :as c]
            [orhof.math :as m]
            [orhof.diff :as d]
            [orhof.opt.descent :as desc]))

;; =============================================================
;; NEWTON'S METHOD (full Hessian)
;; =============================================================

(defn newton-method
  "Newton's method for optimization using full Hessian.
   Catalog: O26. Pattern: ITERATE + DELEGATE.
   Computes Newton direction d = -H^{-1} g via linear solve,
   then applies backtracking line search for safety."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-8 max-iter 100}}]
  (let [grad-f (d/gradient f)
        hess-f (d/hessian f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0) :prev-value Double/MAX_VALUE}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [g (:grad state)
                          H (hess-f (:x state))
                          neg-g (m/v* -1.0 g)
                          d (m/solve-linear H neg-g)
                          alpha (desc/backtracking-line-search f grad-f (:x state) d)
                          x-new (c/v+ (:x state) (c/v* alpha d))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)}))})))

;; =============================================================
;; SECANT METHOD
;; =============================================================

(defn secant-method
  "Secant method — approximates Hessian from gradient differences.
   Catalog: O27. Pattern: ITERATE + DELEGATE.
   Uses the secant equation: H_k * s_k = y_k where
   s_k = x_k - x_{k-1}, y_k = g_k - g_{k-1}.
   Falls back to gradient descent on first iteration."
  [f x0 & {:keys [lr tol max-iter] :or {lr 0.01 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)
        n      (count x0)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :prev-x nil :prev-grad nil}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad prev-x prev-grad]} state
                          ;; Compute direction
                          d (if (and prev-x prev-grad)
                              ;; Secant: diagonal H approx, H_ii ~ y_i/s_i
                              ;; Direction: d_i = -g_i / H_ii = -g_i * s_i / y_i
                              (let [s (c/v- x prev-x)
                                    y (c/v- grad prev-grad)]
                                (mapv (fn [gi si yi]
                                        (let [hi (if (< (Math/abs si) 1e-30) (/ 1.0 lr)
                                                     (/ yi si))]
                                          ;; Ensure positive curvature; fall back to GD otherwise
                                          (if (> hi 1e-10)
                                            (- (/ gi hi))
                                            (* (- lr) gi))))
                                      grad s y))
                              ;; First step: gradient descent
                              (c/v* (- lr) grad))
                          alpha (desc/backtracking-line-search f grad-f x d)
                          x-new (c/v+ x (c/v* alpha d))]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       (grad-f x-new)
                       :prev-value (:value state)
                       :prev-x     x
                       :prev-grad  grad}))})))

;; =============================================================
;; DFP (Davidon-Fletcher-Powell)
;; =============================================================

(defn dfp
  "DFP quasi-Newton method.
   Catalog: O28. Pattern: ITERATE + DELEGATE.
   Maintains approximate inverse Hessian H via rank-1 updates.
   Direction: d = -H * g."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)
        n      (count x0)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :H (m/identity-matrix n)}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad H]} state
                          ;; Direction: d = -H * g
                          d (m/v* -1.0 (m/mat-vec H grad))
                          alpha (desc/backtracking-line-search f grad-f x d)
                          x-new (c/v+ x (c/v* alpha d))
                          g-new (grad-f x-new)
                          ;; DFP update: H_{k+1} = H_k + s*s^T/(s^T*y) - (H*y)*(H*y)^T/(y^T*H*y)
                          s  (c/v- x-new x)
                          y  (c/v- g-new grad)
                          sy (c/dot s y)]
                      (if (< (Math/abs sy) 1e-30)
                        ;; Skip update if curvature is too small
                        {:x x-new :value (f x-new) :grad g-new
                         :prev-value (:value state) :H H}
                        (let [Hy    (m/mat-vec H y)
                              yHy   (c/dot y Hy)
                              term1 (m/mat-scale (/ 1.0 sy) (m/outer-product s s))
                              term2 (m/mat-scale (/ -1.0 (max yHy 1e-30))
                                                 (m/outer-product Hy Hy))
                              H-new (m/mat-add (m/mat-add H term1) term2)]
                          {:x          x-new
                           :value      (f x-new)
                           :grad       g-new
                           :prev-value (:value state)
                           :H          H-new}))))})))

;; =============================================================
;; BFGS (Broyden-Fletcher-Goldfarb-Shanno)
;; =============================================================

(defn bfgs
  "BFGS quasi-Newton method.
   Catalog: O29. Pattern: ITERATE + DELEGATE.
   Like DFP but uses a different rank-2 update formula that
   is generally more robust in practice."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)
        n      (count x0)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :H (m/identity-matrix n)}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad H]} state
                          d     (m/v* -1.0 (m/mat-vec H grad))
                          alpha (desc/backtracking-line-search f grad-f x d)
                          x-new (c/v+ x (c/v* alpha d))
                          g-new (grad-f x-new)
                          s  (c/v- x-new x)
                          y  (c/v- g-new grad)
                          sy (c/dot s y)]
                      (if (< (Math/abs sy) 1e-30)
                        {:x x-new :value (f x-new) :grad g-new
                         :prev-value (:value state) :H H}
                        ;; BFGS: H' = (I - s*y^T/sy) * H * (I - y*s^T/sy) + s*s^T/sy
                        (let [rho   (/ 1.0 sy)
                              I     (m/identity-matrix n)
                              syT   (m/mat-scale rho (m/outer-product s y))
                              ysT   (m/mat-scale rho (m/outer-product y s))
                              left  (m/mat-add I (m/mat-scale -1.0 syT))
                              right (m/mat-add I (m/mat-scale -1.0 ysT))
                              ssT   (m/mat-scale rho (m/outer-product s s))
                              H-new (m/mat-add (m/mat-mult left (m/mat-mult H right)) ssT)]
                          {:x          x-new
                           :value      (f x-new)
                           :grad       g-new
                           :prev-value (:value state)
                           :H          H-new}))))})))

;; =============================================================
;; L-BFGS (Limited-memory BFGS)
;; =============================================================

(defn lbfgs
  "L-BFGS — limited-memory BFGS.
   Catalog: O30. Pattern: ITERATE + DELEGATE + GENERATE.
   Stores only the last m (s, y) pairs instead of the full
   inverse Hessian matrix. Uses two-loop recursion."
  [f x0 & {:keys [m tol max-iter] :or {m 10 tol 1e-8 max-iter 1000}}]
  (let [grad-f (d/gradient f)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :grad (grad-f x0)
                   :prev-value Double/MAX_VALUE
                   :history []}  ;; list of {:s :y :rho}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x grad history]} state
                          ;; Two-loop recursion to compute H*g without forming H
                          n    (count x)
                          hist (vec history)
                          k    (count hist)
                          ;; Forward pass: compute alphas
                          [q alphas]
                          (reduce (fn [[q alphas] i]
                                    (let [{:keys [s y rho]} (nth hist (- k 1 i))
                                          ai (* rho (c/dot s q))
                                          q' (c/v- q (c/v* ai y))]
                                      [q' (conj alphas ai)]))
                                  [grad []]
                                  (range k))
                          ;; Initial H0 scaling
                          gamma (if (pos? k)
                                  (let [{:keys [s y]} (last hist)]
                                    (/ (c/dot s y) (max (c/dot y y) 1e-30)))
                                  1.0)
                          r (c/v* gamma q)
                          ;; Backward pass
                          r (reduce (fn [r i]
                                      (let [{:keys [s y rho]} (nth hist i)
                                            ai (nth alphas (- k 1 i))
                                            bi (* rho (c/dot y r))]
                                        (c/v+ r (c/v* (- ai bi) s))))
                                    r
                                    (range k))
                          ;; Direction = -H*g
                          d     (c/v* -1.0 r)
                          alpha (desc/backtracking-line-search f grad-f x d)
                          x-new (c/v+ x (c/v* alpha d))
                          g-new (grad-f x-new)
                          s     (c/v- x-new x)
                          y     (c/v- g-new grad)
                          sy    (c/dot s y)
                          ;; Update history (keep last m entries)
                          new-hist (if (> sy 1e-30)
                                     (let [entry {:s s :y y :rho (/ 1.0 sy)}]
                                       (if (>= k m)
                                         (conj (vec (rest hist)) entry)
                                         (conj hist entry)))
                                     hist)]
                      {:x          x-new
                       :value      (f x-new)
                       :grad       g-new
                       :prev-value (:value state)
                       :history    new-hist}))})))

;; =============================================================
;; LEVENBERG-MARQUARDT
;; =============================================================

(defn levenberg-marquardt
  "Levenberg-Marquardt algorithm for nonlinear least squares.
   Catalog: O31. Pattern: ITERATE + BRANCH.
   Interpolates between gradient descent (large lambda) and
   Gauss-Newton (small lambda) by adapting the damping parameter.

   residuals-fn: x -> [r1, r2, ...] vector of residuals
   Minimizes sum(r_i^2)."
  [residuals-fn x0 & {:keys [lambda tol max-iter]
                        :or   {lambda 1.0 tol 1e-8 max-iter 200}}]
  (let [f       (fn [x] (let [r (residuals-fn x)] (reduce + (map #(* % %) r))))
        jacob-f (d/jacobian residuals-fn)]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE
                   :lambda lambda}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x lambda]} state
                          r (residuals-fn x)
                          J (jacob-f x)
                          ;; J^T * J  (approximate Hessian)
                          JT (m/mat-transpose J)
                          JTJ (m/mat-mult JT J)
                          ;; J^T * r  (gradient)
                          JTr (m/mat-vec JT r)
                          n (count x)
                          ;; Damped system: (J^T*J + lambda*I) * d = -J^T*r
                          damped (m/mat-add JTJ (m/mat-scale lambda (m/identity-matrix n)))
                          neg-JTr (m/v* -1.0 JTr)
                          d (m/solve-linear damped neg-JTr)
                          x-new (c/v+ x d)
                          v-new (f x-new)]
                      ;; Adapt lambda: decrease if improvement, increase otherwise
                      (if (< v-new (:value state))
                        {:x x-new :value v-new :prev-value (:value state)
                         :lambda (* lambda 0.5)}
                        {:x x :value (:value state) :prev-value (:value state)
                         :lambda (* lambda 2.0)})))})))
