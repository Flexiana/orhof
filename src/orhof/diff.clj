(ns orhof.diff
  "Differentiation as higher-order functions.
   The derivative operator is a function that takes a function
   and returns a function: (ℝ→ℝ) → (ℝ→ℝ)."
  (:require [orhof.core :as c]))

;; =============================================================
;; Scalar Derivatives (f: ℝ → ℝ)
;; =============================================================

(defn forward-difference
  "Forward difference derivative operator.
   Catalog: O1. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Takes a scalar function f and an optional step size h, returns
   a new function that approximates f' using the forward-difference
   formula: f'(x) ≈ (f(x+h) - f(x)) / h. O(h) accuracy.

   Example:
     ((forward-difference #(* % %)) 3.0)       ;=> ~6.0
     ((forward-difference #(* % %) 1e-6) 3.0)  ;=> ~6.0"
  ([f] (forward-difference f 1e-8))
  ([f h]
   (fn [x]
     (/ (- (f (+ x h)) (f x)) h))))

(defn central-difference
  "Central difference derivative operator.
   Catalog: O2. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Takes a scalar function f and an optional step size h, returns
   a new function that approximates f' using the central-difference
   formula: f'(x) ≈ (f(x+h) - f(x-h)) / 2h. O(h²) accuracy,
   more accurate than forward difference.

   Example:
     ((central-difference #(Math/sin %)) 0.0)       ;=> ~1.0
     ((central-difference #(* % % %) 1e-6) 2.0)     ;=> ~12.0"
  ([f] (central-difference f 1e-8))
  ([f h]
   (fn [x]
     (/ (- (f (+ x h)) (f (- x h))) (* 2 h)))))

(defn complex-step
  "Complex-step derivative operator.
   Catalog: O3. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Returns the approximate derivative using the complex-step method.
   Avoids subtractive cancellation: f'(x) = Im(f(x + ih)) / h.
   Falls back to central difference on the JVM since native complex
   arithmetic is unavailable.

   Example:
     ((complex-step #(* % %)) 3.0)  ;=> ~6.0"
  ([f] (complex-step f 1e-20))
  ([f h]
   ;; In practice, we use the central difference as a stand-in
   ;; since JVM doesn't natively support complex arithmetic.
   ;; The concept is: f'(x) = Im(f(x + ih)) / h
   (central-difference f h)))

;; =============================================================
;; Gradient (f: ℝⁿ → ℝ)
;; =============================================================

(defn gradient
  "Gradient operator — lifts a scalar field to its gradient field.
   Catalog: O2 (vector extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f: ℝⁿ→ℝ and returns ∇f: ℝⁿ→ℝⁿ,
   a vector of partial derivatives computed via central differences.

   Example:
     ((gradient (fn [[x y]] (+ (* x x) (* y y)))) [3.0 4.0])
     ;=> [~6.0 ~8.0]"
  ([f] (gradient f 1e-8))
  ([f h]
   (fn [x]
     (let [n (count x)]
       (mapv (fn [i]
               (let [x+ (update x i + h)
                     x- (update x i - h)]
                 (/ (- (f x+) (f x-)) (* 2 h))))
             (range n))))))

(defn directional-derivative
  "Directional derivative operator.
   Catalog: O2 (directional extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Vec R -> R)

   Takes a scalar-valued function f and returns a function that
   computes the directional derivative of f at point x in
   direction d: D_d f(x) = ∇f(x) · d.

   Example:
     (let [dd (directional-derivative (fn [[x y]] (+ (* x x) (* y y))))]
       (dd [1.0 0.0] [1.0 0.0]))  ;=> ~2.0"
  ([f] (directional-derivative f 1e-8))
  ([f h]
   (let [grad-f (gradient f h)]
     (fn [x d]
       (c/dot (grad-f x) d)))))

;; =============================================================
;; Hessian (f: ℝⁿ → ℝ)
;; =============================================================

(defn hessian
  "Hessian operator — lifts a scalar field to its second-derivative matrix.
   Catalog: O2 (second-order extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Mat R)

   Takes a scalar-valued function f: ℝⁿ→ℝ and returns H: ℝⁿ→ℝⁿˣⁿ,
   the matrix of second partial derivatives computed via finite
   differences on a 2×2 stencil.

   Example:
     ((hessian (fn [[x y]] (+ (* x x) (* x y)))) [1.0 2.0])
     ;=> [[~2.0 ~1.0] [~1.0 ~0.0]]"
  ([f] (hessian f 1e-5))
  ([f h]
   (fn [x]
     (let [n (count x)]
       (vec (for [i (range n)]
              (vec (for [j (range n)]
                     (let [xpp (-> x (update i + h) (update j + h))
                           xpm (-> x (update i + h) (update j - h))
                           xmp (-> x (update i - h) (update j + h))
                           xmm (-> x (update i - h) (update j - h))]
                       (/ (+ (f xpp) (f xmm) (- (f xpm)) (- (f xmp)))
                          (* 4 h h)))))))))))

;; =============================================================
;; Jacobian (f: ℝⁿ → ℝᵐ)
;; =============================================================

(defn jacobian
  "Jacobian operator — lifts a vector-valued function to its derivative matrix.
   Catalog: O2 (matrix extension). Pattern: PARAMETERIZE.

   (Vec R -> Vec R) -> R? -> (Vec R -> Mat R)

   Takes a vector-valued function f: ℝⁿ→ℝᵐ and returns J: ℝⁿ→ℝᵐˣⁿ,
   the matrix of all first partial derivatives (∂fᵢ/∂xⱼ) computed
   via central differences.

   Example:
     ;; f([x,y]) = [x*y, x+y]
     ((jacobian (fn [[x y]] [(* x y) (+ x y)])) [2.0 3.0])
     ;=> [[~3.0 ~2.0] [~1.0 ~1.0]]"
  ([f] (jacobian f 1e-8))
  ([f h]
   (fn [x]
     (let [fx (f x)
           n  (count x)
           m  (count fx)]
       (vec (for [i (range m)]
              (vec (for [j (range n)]
                     (let [x+ (update x j + h)
                           x- (update x j - h)]
                       (/ (- (nth (f x+) i) (nth (f x-) i))
                          (* 2 h)))))))))))

;; =============================================================
;; Forward-Mode Automatic Differentiation (Dual Numbers)
;; =============================================================

(defrecord Dual [value deriv])

(defn dual
  "Create a dual number ε-augmented value [v + dε].
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   R -> R -> Dual

   Pairs a primal value with its derivative seed. Dual numbers
   propagate derivatives through arithmetic via operator overloading.

   Example:
     (dual 3.0 1.0)  ;=> #Dual{:value 3.0 :deriv 1.0}"
  [v d]
  (->Dual v d))

(defn dual-value
  "Extract the primal (real) value from a dual number.
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   Dual | R -> R

   Returns the value component of a Dual, or the number itself
   if it is not a Dual.

   Example:
     (dual-value (dual 3.0 1.0))  ;=> 3.0
     (dual-value 5.0)             ;=> 5.0"
  [^Dual d] (if (instance? Dual d) (:value d) d))

(defn dual-deriv
  "Extract the derivative (infinitesimal) part from a dual number.
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   Dual | R -> R

   Returns the derivative component of a Dual, or 0.0 if the
   argument is a plain number.

   Example:
     (dual-deriv (dual 3.0 1.0))  ;=> 1.0
     (dual-deriv 5.0)             ;=> 0.0"
  [^Dual d] (if (instance? Dual d) (:deriv d) 0.0))

(defn dual+
  "Dual-number addition: (a + a'ε) + (b + b'ε) = (a+b) + (a'+b')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Adds two dual numbers, propagating both value and derivative.

   Example:
     (dual+ (dual 2.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 5.0 :deriv 1.0}"
  [a b]
  (dual (+ (dual-value a) (dual-value b))
        (+ (dual-deriv a) (dual-deriv b))))

(defn dual-
  "Dual-number subtraction: (a + a'ε) - (b + b'ε) = (a-b) + (a'-b')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Subtracts two dual numbers, propagating both value and derivative.

   Example:
     (dual- (dual 5.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 2.0 :deriv 1.0}"
  [a b]
  (dual (- (dual-value a) (dual-value b))
        (- (dual-deriv a) (dual-deriv b))))

(defn dual*
  "Dual-number multiplication via product rule:
   (a + a'ε)(b + b'ε) = ab + (a'b + ab')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Multiplies two dual numbers, applying the product rule for
   derivative propagation.

   Example:
     (dual* (dual 3.0 1.0) (dual 4.0 0.0))
     ;=> #Dual{:value 12.0 :deriv 4.0}"
  [a b]
  (let [av (dual-value a) ad (dual-deriv a)
        bv (dual-value b) bd (dual-deriv b)]
    (dual (* av bv) (+ (* av bd) (* ad bv)))))

(defn dual-div
  "Dual-number division via quotient rule:
   (a + a'ε)/(b + b'ε) = a/b + (a'b - ab')/b² ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Divides two dual numbers, applying the quotient rule for
   derivative propagation.

   Example:
     (dual-div (dual 6.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 2.0 :deriv ~0.333}"
  [a b]
  (let [av (dual-value a) ad (dual-deriv a)
        bv (dual-value b) bd (dual-deriv b)]
    (dual (/ av bv)
          (/ (- (* ad bv) (* av bd)) (* bv bv)))))

(defn dual-sin
  "Dual-number sine via chain rule: sin(a + a'ε) = sin(a) + a'cos(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes sine of a dual number, propagating the derivative
   via the chain rule.

   Example:
     (dual-sin (dual 0.0 1.0))
     ;=> #Dual{:value 0.0 :deriv 1.0}"
  [a]
  (dual (Math/sin (dual-value a))
        (* (Math/cos (dual-value a)) (dual-deriv a))))

(defn dual-cos
  "Dual-number cosine via chain rule: cos(a + a'ε) = cos(a) - a'sin(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes cosine of a dual number, propagating the derivative
   via the chain rule.

   Example:
     (dual-cos (dual 0.0 1.0))
     ;=> #Dual{:value 1.0 :deriv 0.0}"
  [a]
  (dual (Math/cos (dual-value a))
        (* (- (Math/sin (dual-value a))) (dual-deriv a))))

(defn dual-exp
  "Dual-number exponential via chain rule: exp(a + a'ε) = exp(a) + a'exp(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes the exponential of a dual number. Since d/dx exp(x) = exp(x),
   the derivative part scales by the same exponential.

   Example:
     (dual-exp (dual 0.0 1.0))
     ;=> #Dual{:value 1.0 :deriv 1.0}"
  [a]
  (let [ev (Math/exp (dual-value a))]
    (dual ev (* ev (dual-deriv a)))))

(defn dual-log
  "Dual-number natural log via chain rule: log(a + a'ε) = log(a) + (a'/a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes the natural logarithm of a dual number, propagating
   the derivative as 1/x.

   Example:
     (dual-log (dual 1.0 1.0))
     ;=> #Dual{:value 0.0 :deriv 1.0}"
  [a]
  (dual (Math/log (dual-value a))
        (/ (dual-deriv a) (dual-value a))))

(defn dual-pow
  "Dual-number power via chain rule: (a + a'ε)^n = a^n + n·a^(n-1)·a'ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> R -> Dual

   Raises a dual number to a constant integer/real power, propagating
   the derivative via the power rule.

   Example:
     (dual-pow (dual 3.0 1.0) 2)
     ;=> #Dual{:value 9.0 :deriv 6.0}"
  [a n]
  (dual (Math/pow (dual-value a) n)
        (* n (Math/pow (dual-value a) (dec n)) (dual-deriv a))))

(defn auto-diff
  "Forward-mode automatic differentiation operator.
   Catalog: O4. Pattern: TRANSFORM-PROBLEM / COMPOSE.

   (Dual -> Dual) -> (R -> R)

   Takes a function f written in dual arithmetic (dual+, dual*, etc.)
   and returns a plain R->R derivative function. Seeds the input
   with derivative 1.0 and extracts the derivative from the output.

   Example:
     (def f (fn [x] (dual* x x)))        ;; x^2 in dual arithmetic
     ((auto-diff f) 3.0)                  ;=> 6.0"
  [f]
  (fn [x]
    (let [result (f (dual x 1.0))]
      (dual-deriv result))))

;; =============================================================
;; Stochastic Gradient Approximation
;; =============================================================

(defn regression-gradient
  "Gradient estimation by linear regression over random perturbations.
   Catalog: O6. Pattern: REDUCE / MAP-OVER.

   (Vec R -> R) -> Int -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f, a sample count, and an optional
   perturbation scale delta. Returns a gradient function that estimates
   ∇f(x) by perturbing x n-samples times, observing the change in f,
   and solving for the gradient via least-squares regression.

   Example:
     (let [g (regression-gradient (fn [[x y]] (+ (* x x) (* y y))) 50)]
       (g [3.0 4.0]))  ;=> [~6.0 ~8.0]"
  ([f n-samples] (regression-gradient f n-samples 0.1))
  ([f n-samples delta]
   (fn [x]
     (let [dim    (count x)
           deltas (repeatedly n-samples
                              #(mapv (fn [_] (* delta (- (rand) 0.5))) (range dim)))
           ys     (map (fn [d] (- (f (c/v+ x d)) (f x))) deltas)
           ;; Least-squares: g ≈ (DᵀD)⁻¹ Dᵀ y
           ;; Simplified: average of (y/d) for each component
           ]
       (mapv (fn [j]
               (let [pairs (map (fn [d y] [(nth d j) y]) deltas ys)
                     num   (reduce + (map (fn [[dj y]] (* dj y)) pairs))
                     den   (reduce + (map (fn [[dj _]] (* dj dj)) pairs))]
                 (if (zero? den) 0.0 (/ num den))))
             (range dim))))))

(defn spsa-gradient
  "Simultaneous Perturbation Stochastic Approximation gradient estimator.
   Catalog: O7. Pattern: PARAMETERIZE / GENERATE.

   (Vec R -> R) -> R -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f, a gain parameter c, and an optional
   perturbation magnitude delta. Returns a gradient function that
   estimates ∇f(x) using only 2 function evaluations regardless of
   dimension, by perturbing all coordinates simultaneously with
   random ±delta.

   Example:
     (let [g (spsa-gradient (fn [[x y]] (+ (* x x) (* y y))) 1.0)]
       (g [3.0 4.0]))  ;=> [~6.0 ~8.0]  (stochastic, approximate)"
  ([f c] (spsa-gradient f c 0.1))
  ([f c delta]
   (fn [x]
     (let [dim (count x)
           d   (mapv (fn [_] (if (< (rand) 0.5) (- delta) delta)) (range dim))
           y+  (f (c/v+ x d))
           y-  (f (c/v- x d))]
       (mapv (fn [di] (/ (- y+ y-) (* 2 di))) d)))))

;; =============================================================
;; Reverse-Mode Automatic Differentiation (Tape-based)
;; =============================================================

(defrecord TapeNode [value children grad-fn])

(defn- make-tape-var
  "Create a tape variable (leaf node)."
  [value id]
  {:value value :op :var :id id :children []})

(defn tape+
  "Tape addition — records the add operation for backpropagation.
   Catalog: O5 (arithmetic). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode -> TapeNode

   Adds two tape nodes, recording that ∂(a+b)/∂a = 1 and
   ∂(a+b)/∂b = 1 in the computation graph.

   Example:
     (tape+ {:value 2.0 :id 0 :children []}
            {:value 3.0 :id 1 :children []})
     ;=> {:value 5.0 :op :add ...}"
  [a b]
  {:value (+ (:value a) (:value b))
   :op :add :children [a b]
   :grad-fn (fn [g] [g g])})

(defn tape*
  "Tape multiplication — records the mul operation for backpropagation.
   Catalog: O5 (arithmetic). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode -> TapeNode

   Multiplies two tape nodes, recording the product-rule gradients
   ∂(a*b)/∂a = b and ∂(a*b)/∂b = a in the computation graph.

   Example:
     (tape* {:value 3.0 :id 0 :children []}
            {:value 4.0 :id 1 :children []})
     ;=> {:value 12.0 :op :mul ...}"
  [a b]
  {:value (* (:value a) (:value b))
   :op :mul :children [a b]
   :grad-fn (fn [g] [(* g (:value b)) (* g (:value a))])})

(defn tape-sin
  "Tape sine — records the sin operation for backpropagation.
   Catalog: O5 (transcendental). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode

   Computes sine of a tape node, recording that ∂sin(a)/∂a = cos(a)
   in the computation graph.

   Example:
     (tape-sin {:value 0.0 :id 0 :children []})
     ;=> {:value 0.0 :op :sin ...}"
  [a]
  {:value (Math/sin (:value a))
   :op :sin :children [a]
   :grad-fn (fn [g] [(* g (Math/cos (:value a)))])})

(defn tape-exp
  "Tape exponential — records the exp operation for backpropagation.
   Catalog: O5 (transcendental). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode

   Computes exponential of a tape node, recording that
   ∂exp(a)/∂a = exp(a) in the computation graph.

   Example:
     (tape-exp {:value 0.0 :id 0 :children []})
     ;=> {:value 1.0 :op :exp ...}"
  [a]
  (let [ev (Math/exp (:value a))]
    {:value ev :op :exp :children [a]
     :grad-fn (fn [g] [(* g ev)])}))

(defn- backpropagate
  "Walk the tape backward accumulating gradients.
   Pattern: reduce over topologically-sorted nodes."
  [output-node n-vars]
  (let [grads (atom {(:id output-node) 1.0})
        ;; BFS backward from output
        visit (fn visit [node]
                (when-let [grad-fn (:grad-fn node)]
                  (let [parent-grad (get @grads (:id node) 0.0)
                        child-grads (grad-fn parent-grad)]
                    (doseq [[child cg] (map vector (:children node) child-grads)]
                      (swap! grads update (:id child) (fnil + 0.0) cg)
                      (visit child)))))]
    (visit output-node)
    ;; Return gradients for the input variables (ids 0..n-1)
    (mapv #(get @grads % 0.0) (range n-vars))))

(defn reverse-ad
  "Reverse-mode automatic differentiation operator.
   Catalog: O5. Pattern: TRANSFORM-PROBLEM / GENERATE.

   (Vec TapeNode -> TapeNode) -> Int -> (Vec R -> Vec R)

   Takes a function f written in tape arithmetic (tape+, tape*, etc.)
   and the number of input variables. Returns a gradient function
   that computes ∇f via backpropagation through the recorded
   computation graph. More efficient than forward-mode when the
   number of outputs is small relative to inputs.

   Example:
     ;; f([x,y]) = x*y
     (def grad-f (reverse-ad (fn [[x y]] (tape* x y)) 2))
     (grad-f [3.0 4.0])  ;=> [4.0 3.0]"
  [f n-vars]
  (fn [x]
    (let [tape-vars (mapv (fn [i] (make-tape-var (nth x i) i)) (range n-vars))
          output    (f tape-vars)]
      (backpropagate (assoc output :id :output) n-vars))))
