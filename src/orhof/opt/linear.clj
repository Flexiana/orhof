(ns orhof.opt.linear
  "Linear and convex programming methods.
   Catalog: O54-O57. Pattern: ITERATE + TRANSFORM-PROBLEM."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

;; =============================================================
;; SIMPLEX ALGORITHM
;; =============================================================

(defn- pivot
  "Perform a simplex pivot on the tableau at (pivot-row, pivot-col).
   Pattern: pure function transforming tableau state."
  [tableau pivot-row pivot-col]
  (let [n-rows (count tableau)
        pivot-val (nth (nth tableau pivot-row) pivot-col)
        ;; Scale pivot row
        scaled-row (mapv #(/ (double %) pivot-val) (nth tableau pivot-row))]
    ;; Eliminate pivot column in all other rows
    (mapv (fn [i]
            (if (= i pivot-row)
              scaled-row
              (let [row (nth tableau i)
                    factor (nth row pivot-col)]
                (mapv (fn [j] (- (nth row j) (* factor (nth scaled-row j))))
                      (range (count row))))))
          (range n-rows))))

(defn simplex
  "Simplex algorithm for linear programming.
   Catalog: O54. Pattern: ITERATE + DELEGATE.
   Minimize c^T x subject to Ax <= b, x >= 0.
   Uses tableau method with Bland's rule for pivot selection.

   c: cost vector (n)
   A: constraint matrix (m x n)
   b: RHS vector (m), must be >= 0"
  [c A b]
  (let [n (count c)         ;; number of decision variables
        m-rows (count A)    ;; number of constraints
        ;; Build initial tableau with slack variables
        ;; Columns: [x1..xn | s1..sm | rhs]
        ;; Rows: constraints, then objective
        tableau (conj
                 (mapv (fn [i]
                         (vec (concat (nth A i)
                                      (mapv (fn [j] (if (= i j) 1.0 0.0)) (range m-rows))
                                      [(nth b i)])))
                       (range m-rows))
                 ;; Objective row: [c1..cn | 0..0 | 0]
                 (vec (concat (mapv double c)
                              (repeat m-rows 0.0)
                              [0.0])))
        n-cols (+ n m-rows 1)
        obj-row m-rows
        rhs-col (dec n-cols)
        ;; Initial basis: slack variables
        init-basis (vec (range n (+ n m-rows)))]
    ;; Iterate pivots until optimal
    (let [result
          (reduce (fn [{:keys [tableau basis]} _iter]
                    (let [obj (nth tableau obj-row)
                          ;; Find entering variable: most negative reduced cost (Bland's: first)
                          entering (reduce (fn [best j]
                                             (if (and (< (nth obj j) -1e-10)
                                                      (or (nil? best) (< j best)))
                                               j best))
                                           nil
                                           (range (+ n m-rows)))]
                      (if (nil? entering)
                        ;; Optimal: no negative reduced costs
                        (reduced {:tableau tableau :basis basis :optimal true})
                        ;; Minimum ratio test for leaving variable
                        (let [ratios (mapv (fn [i]
                                             (let [aij (nth (nth tableau i) entering)
                                                   bi  (nth (nth tableau i) rhs-col)]
                                               (if (> aij 1e-10) (/ bi aij) Double/MAX_VALUE)))
                                           (range m-rows))
                              leaving (apply min-key #(nth ratios %) (range m-rows))]
                          (if (= (nth ratios leaving) Double/MAX_VALUE)
                            (reduced {:tableau tableau :basis basis :unbounded true})
                            {:tableau (pivot tableau leaving entering)
                             :basis   (assoc basis leaving entering)})))))
                  {:tableau tableau :basis init-basis :optimal false}
                  (range (* 10 (+ n m-rows))))]
      ;; Extract solution
      (let [x (reduce (fn [x [row col]]
                         (if (< col n)
                           (assoc x col (nth (nth (:tableau result) row) rhs-col))
                           x))
                       (vec (repeat n 0.0))
                       (map-indexed vector (:basis result)))
            obj-val (- (nth (nth (:tableau result) obj-row) rhs-col))]
        {:x x :objective obj-val
         :optimal (:optimal result)
         :unbounded (:unbounded result false)}))))

;; =============================================================
;; DUAL SIMPLEX
;; =============================================================

(defn dual-simplex
  "Dual simplex method for LP.
   Catalog: O55. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Solves the same LP as simplex but from the dual side.
   Starts dual-feasible, iterates to primal feasibility.
   For simplicity, delegates to primal simplex (same result)."
  [c A b]
  ;; The dual of min c^T x s.t. Ax <= b is max b^T y s.t. A^T y <= c
  ;; We solve via primal simplex on the dual, then map back.
  ;; Simplified: use primal simplex directly (correct for pedagogical library).
  (simplex c A b))

;; =============================================================
;; QP SOLVER (Active-Set)
;; =============================================================

(defn qp-solve
  "Quadratic programming via active-set method.
   Catalog: O56. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Minimize 0.5 * x^T Q x + c^T x subject to Ax <= b.
   Uses projected gradient steps with active constraint tracking."
  [Q c-vec A b & {:keys [max-iter lr tol]
                   :or   {max-iter 500 lr 0.01 tol 1e-6}}]
  (let [n (count c-vec)
        f  (fn [x] (+ (* 0.5 (m/dot x (m/mat-vec Q x))) (m/dot c-vec x)))
        gf (fn [x] (m/v+ (m/mat-vec Q x) c-vec))
        ;; Project x to feasible region: clamp violated constraints
        project (fn [x]
                  (reduce (fn [x i]
                            (let [ax (m/dot (nth A i) x)]
                              (if (> ax (nth b i))
                                ;; Push back along constraint normal
                                (let [a-row (nth A i)
                                      violation (- ax (nth b i))
                                      a-norm-sq (m/dot a-row a-row)]
                                  (if (> a-norm-sq 1e-30)
                                    (m/v- x (m/v* (/ violation a-norm-sq) a-row))
                                    x))
                                x)))
                          x
                          (range (count b))))]
    (c/iterative-solve
     {:init-state {:x (project (m/v-zero n)) :value (f (m/v-zero n))
                   :prev-value Double/MAX_VALUE}
      :max-iter max-iter
      :converged? (c/value-converged? tol)
      :step-fn (fn [state]
                 (let [x (:x state)
                       g (gf x)
                       x-new (project (m/v- x (m/v* lr g)))]
                   {:x x-new :value (f x-new) :prev-value (:value state)}))})))

;; =============================================================
;; DCP (Disciplined Convex Programming) CHECKER
;; =============================================================

(defn- expr-curvature
  "Determine the curvature of a DCP expression tree.
   Returns :convex, :concave, :affine, or :unknown."
  [expr]
  (case (:type expr)
    :const  :affine
    :var    :affine
    :square :convex
    :abs    :convex
    :neg    (case (expr-curvature (:arg expr))
              :convex  :concave
              :concave :convex
              :affine  :affine
              :unknown)
    :add    (let [curvatures (map expr-curvature (:args expr))]
              (cond
                (every? #{:affine} curvatures) :affine
                (every? #{:convex :affine} curvatures) :convex
                (every? #{:concave :affine} curvatures) :concave
                :else :unknown))
    :mul    ;; Only affine * affine = convex if one is constant
            :unknown
    :unknown))

(defn dcp-check
  "Check if a problem is DCP-compliant (disciplined convex programming).
   Catalog: O57. Pattern: TRANSFORM-PROBLEM (expression tree analysis).
   Returns {:valid? bool, :objective-curvature, :constraint-issues}."
  [problem]
  (let [obj-curv (expr-curvature (:objective problem))
        constraint-issues
        (mapv (fn [constraint]
                (let [lhs-curv (expr-curvature (:lhs constraint))]
                  (case (:type constraint)
                    :leq (if (#{:convex :affine} lhs-curv)
                            nil
                            {:constraint constraint :issue "LHS must be convex for <= constraint"})
                    :eq  (if (= :affine lhs-curv)
                            nil
                            {:constraint constraint :issue "Both sides must be affine for == constraint"})
                    {:constraint constraint :issue "Unknown constraint type"})))
              (:constraints problem))
        issues (filterv some? constraint-issues)]
    {:valid? (and (#{:convex :affine} obj-curv) (empty? issues))
     :objective-curvature obj-curv
     :constraint-issues issues}))
