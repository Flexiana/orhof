# HOF Algorithms Library — API Reference

Auto-generated documentation for all public functions.
Each entry shows the function signature, type information, and usage examples.

**Library stats:**
- 34 namespaces
- 294 public functions

---

## Table of Contents

- [`orhof.core`](#orhofcore) (35 functions)
- [`orhof.math`](#orhofmath) (20 functions)
- [`orhof.diff`](#orhofdiff) (31 functions)
- [`orhof.opt.bracketing`](#orhofoptbracketing) (5 functions)
- [`orhof.opt.descent`](#orhofoptdescent) (15 functions)
- [`orhof.opt.second-order`](#orhofoptsecond-order) (6 functions)
- [`orhof.opt.direct`](#orhofoptdirect) (6 functions)
- [`orhof.opt.stochastic`](#orhofoptstochastic) (4 functions)
- [`orhof.opt.population`](#orhofoptpopulation) (6 functions)
- [`orhof.opt.constrained`](#orhofoptconstrained) (6 functions)
- [`orhof.opt.linear`](#orhofoptlinear) (4 functions)
- [`orhof.opt.surrogate`](#orhofoptsurrogate) (8 functions)
- [`orhof.opt.multiobjective`](#orhofoptmultiobjective) (7 functions)
- [`orhof.mdp.planning`](#orhofmdpplanning) (9 functions)
- [`orhof.mdp.online`](#orhofmdponline) (7 functions)
- [`orhof.mdp.policy`](#orhofmdppolicy) (6 functions)
- [`orhof.mdp.beliefs`](#orhofmdpbeliefs) (5 functions)
- [`orhof.mdp.games`](#orhofmdpgames) (8 functions)
- [`orhof.mdp.inference`](#orhofmdpinference) (8 functions)
- [`orhof.mdp.learning`](#orhofmdplearning) (6 functions)
- [`orhof.mdp.approx`](#orhofmdpapprox) (5 functions)
- [`orhof.val.spec`](#orhofvalspec) (13 functions)
- [`orhof.val.falsify`](#orhofvalfalsify) (8 functions)
- [`orhof.val.sampling`](#orhofvalsampling) (8 functions)
- [`orhof.val.reach`](#orhofvalreach) (10 functions)
- [`orhof.val.explain`](#orhofvalexplain) (8 functions)
- [`orhof.search`](#orhofsearch) (11 functions)
- [`orhof.search.graph`](#orhofsearchgraph) (2 functions)
- [`orhof.search.games`](#orhofsearchgames) (5 functions)
- [`orhof.search.mdp`](#orhofsearchmdp) (5 functions)
- [`orhof.search.val`](#orhofsearchval) (2 functions)
- [`orhof.search.opt`](#orhofsearchopt) (5 functions)
- [`orhof.search.learn`](#orhofsearchlearn) (8 functions)
- [`orhof.search.diff`](#orhofsearchdiff) (2 functions)

---

## `orhof.core`

The twelve fundamental higher-order function patterns.

   Five are Clojure built-ins, used directly:
     iterate  — iterative refinement (search)
     reduce   — fold over streams (learning)
     comp     — function composition (pipelines)
     partial  — function configuration (currying)
     filter/repeatedly — generate-and-test (sampling)

   Seven are novel, built from scratch:
     population-step — evaluate/select/recombine/mutate pipeline
     explore-tree    — recursive branching with pruning
     make-updater    — belief state estimation
     propagate-sets  — set propagation through dynamics
     analyze-sensitivity — perturbation-based analysis
     transform-problem  — encode/solve/decode pipeline
     fit-model       — data-driven function construction

### `analyze-sensitivity`

```clojure
(analyze-sensitivity f method)
```

Analyze sensitivity of f using a perturbation method.

   (a -> b) -> ((a -> b) -> Info) -> Info

   Example:
     (analyze-sensitivity my-fn finite-difference-method)

---

### `best-of`

```clojure
(best-of fitness-fn population)
```

Return the best individual (minimization).

   (a -> R) -> [a] -> a

   Example:
     (best-of #(Math/abs %) [-3 1 -5 2]) ;=> 1

---

### `compose-pipeline`

```clojure
(compose-pipeline & stages)
```

Compose stages left-to-right (data flows first -> last).

   (a -> b) -> (b -> c) -> ... -> (a -> z)

   Example:
     ((compose-pipeline inc #(* 2 %)) 3) ;=> 8

---

### `compose-transformers`

```clojure
(compose-transformers & transformers)
```

Compose transformers left-to-right.

   ((f -> f) -> ... -> (f -> f)) -> (f -> f)

   (compose-transformers T1 T2 T3) = (fn [f] (T3 (T2 (T1 f))))

---

### `dot`

---

### `evolve`

```clojure
(evolve pop-transform population k-max)
```

Evolve a population k-max generations.

   ([a] -> Int -> [a]) -> [a] -> Int -> [a]

   Pattern: reduce over generation indices.

   Example:
     (evolve (fn [pop k] (map inc pop)) [0 0 0] 5) ;=> [5 5 5]

---

### `explore-tree`

```clojure
(explore-tree expand select evaluate root budget)
```

Explore a tree from root with budget.

   (Node -> [Node]) -> ([Node] -> Node) -> (Node -> R) -> Node -> Int
   -> {:best-node Node, :best-val R}

   expand:   node -> children
   select:   frontier -> next node to expand
   evaluate: node -> value (lower is better)

   Pattern: reduce over budget steps.

   Example:
     (explore-tree expand-fn first eval-fn root 100)

---

### `fit-model`

```clojure
(fit-model model-class data)
```

Fit a model to data, returning a prediction function.

   {:fit (Data -> (X -> Y))} -> Data -> (X -> Y)

   Example:
     (fit-model {:fit (fn [data] (fn [x] (mean data)))} [1 2 3])
     ;=> (fn [x] 2.0)

---

### `fold-collecting`

```clojure
(fold-collecting update-fn init-state observations)
```

Like fold-over-stream but returns all intermediate states.

   (State -> Obs -> State) -> State -> [Obs] -> (Seq State)

   Example:
     (fold-collecting + 0 [1 2 3]) ;=> (0 1 3 6)

---

### `fold-over-stream`

```clojure
(fold-over-stream update-fn init-state observations)
```

Fold an update function over a stream of observations.

   (State -> Obs -> State) -> State -> [Obs] -> State

   Example:
     (fold-over-stream + 0 [1 2 3 4]) ;=> 10

---

### `iterate-trajectory`

```clojure
(iterate-trajectory {:keys [step-fn init-state max-iter], :or {max-iter 1000}})
```

Full trajectory capture via iterate.

   {:step-fn (State -> State), :init-state State, :max-iter Int}
   -> (Seq State)

   Returns a lazy sequence of all states.

   Example:
     (iterate-trajectory {:init-state 1 :step-fn inc :max-iter 5})
     ;=> (1 2 3 4 5 6)

---

### `iterate-until`

```clojure
(iterate-until step-fn pred init)
```

Iterate step-fn until pred holds.

   (a -> a) -> (a -> Bool) -> a -> a

   Example:
     (iterate-until #(* 2 %) #(> % 100) 1) ;=> 128

---

### `iterative-solve`

```clojure
(iterative-solve {:keys [step-fn converged? init-state max-iter], :or {max-iter 1000}})
```

The universal iterative solver. Pattern 1: IRO.

   {:step-fn (State -> State), :converged? (State -> Bool),
    :init-state State, :max-iter Int}
   -> State

   Applies step-fn repeatedly until converged? returns true or
   max-iter is reached. Returns final state with :iterations
   and :converged metadata.

   Example:
     (iterative-solve
       {:init-state {:x 10.0 :value 100.0 :prev-value ##Inf}
        :step-fn    (fn [s] {:x (* 0.5 (:x s)) :value (* 0.25 (:value s))
                             :prev-value (:value s)})
        :converged? (value-converged? 0.01)})
     ;=> {:x 0.039 :value 0.0015 :converged true :iterations 8}

---

### `lift-to-interval`

```clojure
(lift-to-interval f)
```

Lift a scalar function to interval arithmetic.

   (R -> R) -> ([R R] -> [R R])

   Example:
     ((lift-to-interval #(* % %)) [2 3]) ;=> [4 9]

---

### `lift-to-set`

```clojure
(lift-to-set f)
```

Lift a point function to operate on a set (collection).

   (a -> b) -> ([a] -> [b])

   Example:
     ((lift-to-set inc) [1 2 3]) ;=> (2 3 4)

---

### `make-algorithm`

```clojure
(make-algorithm algorithm-fn config)
```

Create a configured solver from algorithm + strategy config.

   (Config -> Result) -> Config -> (Problem -> Result)

   Example:
     (def solver (make-algorithm optimize {:lr 0.01 :max-iter 500}))
     (solver {:objective sphere :x0 [5 5]})

---

### `make-updater`

```clojure
(make-updater model)
```

Create a belief update function from a model.

   {:predict (Belief -> Action -> Predicted),
    :update  (Predicted -> Obs -> Posterior)}
   -> (Belief -> Action -> Obs -> Belief')

   Example:
     (def updater (make-updater {:predict predict-fn :update update-fn}))
     (updater belief :move :obs-x) ;=> new-belief

---

### `or-converged`

```clojure
(or-converged & preds)
```

Combine convergence predicates with OR.

   (State -> Bool) -> ... -> (State -> Bool)

   Example:
     (or-converged (value-converged? 1e-6) (point-converged? 1e-6))

---

### `point-converged?`

```clojure
(point-converged? tol)
```

Convergence predicate: ||x - prev-x|| < tol.

   R -> (State -> Bool)

   Example:
     (point-converged? 1e-6)

---

### `population-step`

```clojure
(population-step evaluate select recombine mutate population)
```

One generation of the population pipeline.

   (a -> R) -> ([Scored] -> [a]) -> ([a] -> [a]) -> (a -> a) -> [a] -> [a]

   evaluate:  individual -> fitness
   select:    scored-population -> parents
   recombine: parents -> offspring
   mutate:    individual -> individual'

   Example:
     (population-step fitness-fn tournament-select crossover mutate pop)

---

### `propagate-sets`

```clojure
(propagate-sets dynamics initial-set horizon)
```

Propagate a set through dynamics for horizon steps.

   (Set -> Set) -> Set -> Int -> [Set]

   Returns [initial-set, step-1, step-2, ...].
   Pattern: iterate + take.

   Example:
     (propagate-sets #(* 2 %) 1 3) ;=> [1 2 4 8]

---

### `sample-aggregate`

```clojure
(sample-aggregate {:keys [sampler evaluator aggregator n]})
```

Generate n samples, evaluate each, aggregate.

   {:sampler (() -> a), :evaluator (a -> b),
    :aggregator ([b] -> c), :n Int} -> c

   Example:
     (sample-aggregate {:sampler rand :evaluator identity
                        :aggregator #(/ (reduce + %) (count %)) :n 1000})
     ;=> ~0.5

---

### `sample-until`

```clojure
(sample-until sampler-fn accept?)
```

Generate samples until accept? returns true.

   (() -> a) -> (a -> Bool) -> a

   Example:
     (sample-until #(rand-int 100) #(> % 95)) ;=> 97

---

### `transform`

```clojure
(transform transformer f)
```

Apply a transformer HOF to a function.

   ((a -> b) -> (a -> c)) -> (a -> b) -> (a -> c)

   Example:
     (transform (fn [f] (fn [x] (* 2 (f x)))) inc)
     ;=> (fn [x] (* 2 (inc x)))

---

### `transform-problem`

```clojure
(transform-problem transform solver problem)
(transform-problem transform solver decode problem)
```

Transform a problem, solve it, decode the result.

   (Problem -> Problem') -> (Problem' -> Raw) -> Problem -> Result
   (Problem -> Problem') -> (Problem' -> Raw) -> (Raw -> Result) -> Problem -> Result

   Example:
     (transform-problem normalize-fn solver-fn denormalize-fn problem)

---

### `v*`

---

### `v+`

---

### `v-`

---

### `v-zero`

---

### `value-converged?`

```clojure
(value-converged? tol)
```

Convergence predicate: |value - prev-value| < tol.

   R -> (State -> Bool)

   Example:
     (value-converged? 1e-6)

---

### `vnorm`

---

### `with-strategy`

```clojure
(with-strategy config strategy-key strategy-fn)
```

Inject a strategy function into a config map.

   Config -> Keyword -> (a -> b) -> Config

   Example:
     (with-strategy config :direction-fn my-direction)

---

### `wrap-with-budget`

```clojure
(wrap-with-budget solver max-evals)
```

Wrap a solver to enforce a computational budget.

   (Problem -> Result) -> Int -> (Problem -> Result)

---

### `wrap-with-history`

```clojure
(wrap-with-history step-fn)
```

Wrap a step function to accumulate history.

   (State -> State) -> (State -> State)

   Adds :history key with vector of previous states.

---

### `wrap-with-logging`

```clojure
(wrap-with-logging step-fn log-fn)
```

Wrap a step function to log each iteration.

   (State -> State) -> (State -> State -> nil) -> (State -> State)

   Example:
     (wrap-with-logging step-fn (fn [old new] (println (:value new))))

---


## `orhof.math`

Vector and matrix operations for the HOF algorithms library.
   Pure Clojure implementations operating on standard vectors and
   nested vectors (for matrices). No external dependencies.

### `cholesky`

```clojure
(cholesky A)
```

Cholesky decomposition of a positive-definite matrix A.
   Returns lower triangular L such that A = L * L^T.

   [[R]] -> [[R]]

   Example:
     (cholesky [[4 2] [2 3]]) ;=> [[2.0 0.0] [1.0 1.4142135623730951]]

---

### `diag-matrix`

```clojure
(diag-matrix v)
```

Create a diagonal matrix from a vector.

   [R] -> [[R]]

   Example:
     (diag-matrix [3 5]) ;=> [[3 0.0] [0.0 5]]

---

### `dot`

```clojure
(dot a b)
```

Dot product of two vectors.

   [R] -> [R] -> R

   Example:
     (dot [1 2 3] [4 5 6]) ;=> 32

---

### `identity-matrix`

```clojure
(identity-matrix n)
```

n x n identity matrix.

   Int -> [[R]]

   Example:
     (identity-matrix 2) ;=> [[1.0 0.0] [0.0 1.0]]

---

### `mat-add`

```clojure
(mat-add A B)
```

Matrix addition.

   [[R]] -> [[R]] -> [[R]]

   Example:
     (mat-add [[1 2] [3 4]] [[5 6] [7 8]]) ;=> [[6 8] [10 12]]

---

### `mat-col`

```clojure
(mat-col A j)
```

Get column j from matrix A.

   [[R]] -> Int -> [R]

   Example:
     (mat-col [[10 20] [30 40]] 0) ;=> [10 30]

---

### `mat-diag`

```clojure
(mat-diag A)
```

Extract the diagonal of a square matrix.

   [[R]] -> [R]

   Example:
     (mat-diag [[1 2] [3 4]]) ;=> [1 4]

---

### `mat-mult`

```clojure
(mat-mult A B)
```

Matrix-matrix multiplication: A * B.

   [[R]] -> [[R]] -> [[R]]

   Example:
     (mat-mult [[1 2] [3 4]] [[5 6] [7 8]]) ;=> [[19 22] [43 50]]

---

### `mat-row`

```clojure
(mat-row A i)
```

Get row i from matrix A.

   [[R]] -> Int -> [R]

   Example:
     (mat-row [[10 20] [30 40]] 1) ;=> [30 40]

---

### `mat-scale`

```clojure
(mat-scale s A)
```

Scalar-matrix multiplication.

   R -> [[R]] -> [[R]]

   Example:
     (mat-scale 2 [[1 2] [3 4]]) ;=> [[2 4] [6 8]]

---

### `mat-transpose`

```clojure
(mat-transpose A)
```

Transpose of a matrix.

   [[R]] -> [[R]]

   Example:
     (mat-transpose [[1 2] [3 4]]) ;=> [[1 3] [2 4]]

---

### `mat-vec`

```clojure
(mat-vec A x)
```

Matrix-vector multiplication: A * x.

   [[R]] -> [R] -> [R]

   Example:
     (mat-vec [[1 0] [0 1]] [3 4]) ;=> [3 4]

---

### `outer-product`

```clojure
(outer-product a b)
```

Outer product of two vectors: a * b^T.

   [R] -> [R] -> [[R]]

   Example:
     (outer-product [1 2] [3 4]) ;=> [[3 4] [6 8]]

---

### `solve-linear`

```clojure
(solve-linear A b)
```

Solve Ax = b via Gaussian elimination with partial pivoting.
   Returns x.

   [[R]] -> [R] -> [R]

   Example:
     (solve-linear [[2 1] [1 3]] [5 10]) ;=> [1.0 3.0]

---

### `v*`

```clojure
(v* s v)
```

Scalar-vector multiplication.

   R -> [R] -> [R]

   Example:
     (v* 3 [1 2 4]) ;=> [3 6 12]

---

### `v+`

```clojure
(v+ a b)
```

Vector addition.

   [R] -> [R] -> [R]

   Example:
     (v+ [1 2] [3 4]) ;=> [4 6]

---

### `v-`

```clojure
(v- a b)
```

Vector subtraction.

   [R] -> [R] -> [R]

   Example:
     (v- [5 3] [1 2]) ;=> [4 1]

---

### `v-ones`

```clojure
(v-ones n)
```

Ones vector of dimension n.

   Int -> [R]

   Example:
     (v-ones 3) ;=> [1.0 1.0 1.0]

---

### `v-zero`

```clojure
(v-zero n)
```

Zero vector of dimension n.

   Int -> [R]

   Example:
     (v-zero 3) ;=> [0.0 0.0 0.0]

---

### `vnorm`

```clojure
(vnorm v)
```

Euclidean norm (L2) of a vector.

   [R] -> R

   Example:
     (vnorm [3 4]) ;=> 5.0

---


## `orhof.diff`

Differentiation as higher-order functions.
   The derivative operator is a function that takes a function
   and returns a function: (ℝ→ℝ) → (ℝ→ℝ).

### `->Dual`

```clojure
(->Dual value deriv)
```

Positional factory function for class orhof.diff.Dual.

---

### `->TapeNode`

```clojure
(->TapeNode value children grad-fn)
```

Positional factory function for class orhof.diff.TapeNode.

---

### `auto-diff`

```clojure
(auto-diff f)
```

Forward-mode automatic differentiation operator.
   Catalog: O4. Pattern: TRANSFORM-PROBLEM / COMPOSE.

   (Dual -> Dual) -> (R -> R)

   Takes a function f written in dual arithmetic (dual+, dual*, etc.)
   and returns a plain R->R derivative function. Seeds the input
   with derivative 1.0 and extracts the derivative from the output.

   Example:
     (def f (fn [x] (dual* x x)))        ;; x^2 in dual arithmetic
     ((auto-diff f) 3.0)                  ;=> 6.0

---

### `central-difference`

```clojure
(central-difference f)
(central-difference f h)
```

Central difference derivative operator.
   Catalog: O2. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Takes a scalar function f and an optional step size h, returns
   a new function that approximates f' using the central-difference
   formula: f'(x) ≈ (f(x+h) - f(x-h)) / 2h. O(h²) accuracy,
   more accurate than forward difference.

   Example:
     ((central-difference #(Math/sin %)) 0.0)       ;=> ~1.0
     ((central-difference #(* % % %) 1e-6) 2.0)     ;=> ~12.0

---

### `complex-step`

```clojure
(complex-step f)
(complex-step f h)
```

Complex-step derivative operator.
   Catalog: O3. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Returns the approximate derivative using the complex-step method.
   Avoids subtractive cancellation: f'(x) = Im(f(x + ih)) / h.
   Falls back to central difference on the JVM since native complex
   arithmetic is unavailable.

   Example:
     ((complex-step #(* % %)) 3.0)  ;=> ~6.0

---

### `directional-derivative`

```clojure
(directional-derivative f)
(directional-derivative f h)
```

Directional derivative operator.
   Catalog: O2 (directional extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Vec R -> R)

   Takes a scalar-valued function f and returns a function that
   computes the directional derivative of f at point x in
   direction d: D_d f(x) = ∇f(x) · d.

   Example:
     (let [dd (directional-derivative (fn [[x y]] (+ (* x x) (* y y))))]
       (dd [1.0 0.0] [1.0 0.0]))  ;=> ~2.0

---

### `dual`

```clojure
(dual v d)
```

Create a dual number ε-augmented value [v + dε].
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   R -> R -> Dual

   Pairs a primal value with its derivative seed. Dual numbers
   propagate derivatives through arithmetic via operator overloading.

   Example:
     (dual 3.0 1.0)  ;=> #Dual{:value 3.0 :deriv 1.0}

---

### `dual*`

```clojure
(dual* a b)
```

Dual-number multiplication via product rule:
   (a + a'ε)(b + b'ε) = ab + (a'b + ab')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Multiplies two dual numbers, applying the product rule for
   derivative propagation.

   Example:
     (dual* (dual 3.0 1.0) (dual 4.0 0.0))
     ;=> #Dual{:value 12.0 :deriv 4.0}

---

### `dual+`

```clojure
(dual+ a b)
```

Dual-number addition: (a + a'ε) + (b + b'ε) = (a+b) + (a'+b')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Adds two dual numbers, propagating both value and derivative.

   Example:
     (dual+ (dual 2.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 5.0 :deriv 1.0}

---

### `dual-`

```clojure
(dual- a b)
```

Dual-number subtraction: (a + a'ε) - (b + b'ε) = (a-b) + (a'-b')ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Subtracts two dual numbers, propagating both value and derivative.

   Example:
     (dual- (dual 5.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 2.0 :deriv 1.0}

---

### `dual-cos`

```clojure
(dual-cos a)
```

Dual-number cosine via chain rule: cos(a + a'ε) = cos(a) - a'sin(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes cosine of a dual number, propagating the derivative
   via the chain rule.

   Example:
     (dual-cos (dual 0.0 1.0))
     ;=> #Dual{:value 1.0 :deriv 0.0}

---

### `dual-deriv`

```clojure
(dual-deriv d)
```

Extract the derivative (infinitesimal) part from a dual number.
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   Dual | R -> R

   Returns the derivative component of a Dual, or 0.0 if the
   argument is a plain number.

   Example:
     (dual-deriv (dual 3.0 1.0))  ;=> 1.0
     (dual-deriv 5.0)             ;=> 0.0

---

### `dual-div`

```clojure
(dual-div a b)
```

Dual-number division via quotient rule:
   (a + a'ε)/(b + b'ε) = a/b + (a'b - ab')/b² ε.
   Catalog: O4 (arithmetic). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual -> Dual

   Divides two dual numbers, applying the quotient rule for
   derivative propagation.

   Example:
     (dual-div (dual 6.0 1.0) (dual 3.0 0.0))
     ;=> #Dual{:value 2.0 :deriv ~0.333}

---

### `dual-exp`

```clojure
(dual-exp a)
```

Dual-number exponential via chain rule: exp(a + a'ε) = exp(a) + a'exp(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes the exponential of a dual number. Since d/dx exp(x) = exp(x),
   the derivative part scales by the same exponential.

   Example:
     (dual-exp (dual 0.0 1.0))
     ;=> #Dual{:value 1.0 :deriv 1.0}

---

### `dual-log`

```clojure
(dual-log a)
```

Dual-number natural log via chain rule: log(a + a'ε) = log(a) + (a'/a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes the natural logarithm of a dual number, propagating
   the derivative as 1/x.

   Example:
     (dual-log (dual 1.0 1.0))
     ;=> #Dual{:value 0.0 :deriv 1.0}

---

### `dual-pow`

```clojure
(dual-pow a n)
```

Dual-number power via chain rule: (a + a'ε)^n = a^n + n·a^(n-1)·a'ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> R -> Dual

   Raises a dual number to a constant integer/real power, propagating
   the derivative via the power rule.

   Example:
     (dual-pow (dual 3.0 1.0) 2)
     ;=> #Dual{:value 9.0 :deriv 6.0}

---

### `dual-sin`

```clojure
(dual-sin a)
```

Dual-number sine via chain rule: sin(a + a'ε) = sin(a) + a'cos(a)ε.
   Catalog: O4 (transcendental). Pattern: TRANSFORM-PROBLEM.

   Dual -> Dual

   Computes sine of a dual number, propagating the derivative
   via the chain rule.

   Example:
     (dual-sin (dual 0.0 1.0))
     ;=> #Dual{:value 0.0 :deriv 1.0}

---

### `dual-value`

```clojure
(dual-value d)
```

Extract the primal (real) value from a dual number.
   Catalog: O4 (support). Pattern: TRANSFORM-PROBLEM.

   Dual | R -> R

   Returns the value component of a Dual, or the number itself
   if it is not a Dual.

   Example:
     (dual-value (dual 3.0 1.0))  ;=> 3.0
     (dual-value 5.0)             ;=> 5.0

---

### `forward-difference`

```clojure
(forward-difference f)
(forward-difference f h)
```

Forward difference derivative operator.
   Catalog: O1. Pattern: PARAMETERIZE.

   (R -> R) -> R? -> (R -> R)

   Takes a scalar function f and an optional step size h, returns
   a new function that approximates f' using the forward-difference
   formula: f'(x) ≈ (f(x+h) - f(x)) / h. O(h) accuracy.

   Example:
     ((forward-difference #(* % %)) 3.0)       ;=> ~6.0
     ((forward-difference #(* % %) 1e-6) 3.0)  ;=> ~6.0

---

### `gradient`

```clojure
(gradient f)
(gradient f h)
```

Gradient operator — lifts a scalar field to its gradient field.
   Catalog: O2 (vector extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f: ℝⁿ→ℝ and returns ∇f: ℝⁿ→ℝⁿ,
   a vector of partial derivatives computed via central differences.

   Example:
     ((gradient (fn [[x y]] (+ (* x x) (* y y)))) [3.0 4.0])
     ;=> [~6.0 ~8.0]

---

### `hessian`

```clojure
(hessian f)
(hessian f h)
```

Hessian operator — lifts a scalar field to its second-derivative matrix.
   Catalog: O2 (second-order extension). Pattern: PARAMETERIZE.

   (Vec R -> R) -> R? -> (Vec R -> Mat R)

   Takes a scalar-valued function f: ℝⁿ→ℝ and returns H: ℝⁿ→ℝⁿˣⁿ,
   the matrix of second partial derivatives computed via finite
   differences on a 2×2 stencil.

   Example:
     ((hessian (fn [[x y]] (+ (* x x) (* x y)))) [1.0 2.0])
     ;=> [[~2.0 ~1.0] [~1.0 ~0.0]]

---

### `jacobian`

```clojure
(jacobian f)
(jacobian f h)
```

Jacobian operator — lifts a vector-valued function to its derivative matrix.
   Catalog: O2 (matrix extension). Pattern: PARAMETERIZE.

   (Vec R -> Vec R) -> R? -> (Vec R -> Mat R)

   Takes a vector-valued function f: ℝⁿ→ℝᵐ and returns J: ℝⁿ→ℝᵐˣⁿ,
   the matrix of all first partial derivatives (∂fᵢ/∂xⱼ) computed
   via central differences.

   Example:
     ;; f([x,y]) = [x*y, x+y]
     ((jacobian (fn [[x y]] [(* x y) (+ x y)])) [2.0 3.0])
     ;=> [[~3.0 ~2.0] [~1.0 ~1.0]]

---

### `map->Dual`

```clojure
(map->Dual m__7997__auto__)
```

Factory function for class orhof.diff.Dual, taking a map of keywords to field values.

---

### `map->TapeNode`

```clojure
(map->TapeNode m__7997__auto__)
```

Factory function for class orhof.diff.TapeNode, taking a map of keywords to field values.

---

### `regression-gradient`

```clojure
(regression-gradient f n-samples)
(regression-gradient f n-samples delta)
```

Gradient estimation by linear regression over random perturbations.
   Catalog: O6. Pattern: REDUCE / MAP-OVER.

   (Vec R -> R) -> Int -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f, a sample count, and an optional
   perturbation scale delta. Returns a gradient function that estimates
   ∇f(x) by perturbing x n-samples times, observing the change in f,
   and solving for the gradient via least-squares regression.

   Example:
     (let [g (regression-gradient (fn [[x y]] (+ (* x x) (* y y))) 50)]
       (g [3.0 4.0]))  ;=> [~6.0 ~8.0]

---

### `reverse-ad`

```clojure
(reverse-ad f n-vars)
```

Reverse-mode automatic differentiation operator.
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
     (grad-f [3.0 4.0])  ;=> [4.0 3.0]

---

### `spsa-gradient`

```clojure
(spsa-gradient f c)
(spsa-gradient f c delta)
```

Simultaneous Perturbation Stochastic Approximation gradient estimator.
   Catalog: O7. Pattern: PARAMETERIZE / GENERATE.

   (Vec R -> R) -> R -> R? -> (Vec R -> Vec R)

   Takes a scalar-valued function f, a gain parameter c, and an optional
   perturbation magnitude delta. Returns a gradient function that
   estimates ∇f(x) using only 2 function evaluations regardless of
   dimension, by perturbing all coordinates simultaneously with
   random ±delta.

   Example:
     (let [g (spsa-gradient (fn [[x y]] (+ (* x x) (* y y))) 1.0)]
       (g [3.0 4.0]))  ;=> [~6.0 ~8.0]  (stochastic, approximate)

---

### `tape*`

```clojure
(tape* a b)
```

Tape multiplication — records the mul operation for backpropagation.
   Catalog: O5 (arithmetic). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode -> TapeNode

   Multiplies two tape nodes, recording the product-rule gradients
   ∂(a*b)/∂a = b and ∂(a*b)/∂b = a in the computation graph.

   Example:
     (tape* {:value 3.0 :id 0 :children []}
            {:value 4.0 :id 1 :children []})
     ;=> {:value 12.0 :op :mul ...}

---

### `tape+`

```clojure
(tape+ a b)
```

Tape addition — records the add operation for backpropagation.
   Catalog: O5 (arithmetic). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode -> TapeNode

   Adds two tape nodes, recording that ∂(a+b)/∂a = 1 and
   ∂(a+b)/∂b = 1 in the computation graph.

   Example:
     (tape+ {:value 2.0 :id 0 :children []}
            {:value 3.0 :id 1 :children []})
     ;=> {:value 5.0 :op :add ...}

---

### `tape-exp`

```clojure
(tape-exp a)
```

Tape exponential — records the exp operation for backpropagation.
   Catalog: O5 (transcendental). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode

   Computes exponential of a tape node, recording that
   ∂exp(a)/∂a = exp(a) in the computation graph.

   Example:
     (tape-exp {:value 0.0 :id 0 :children []})
     ;=> {:value 1.0 :op :exp ...}

---

### `tape-sin`

```clojure
(tape-sin a)
```

Tape sine — records the sin operation for backpropagation.
   Catalog: O5 (transcendental). Pattern: TRANSFORM-PROBLEM / GENERATE.

   TapeNode -> TapeNode

   Computes sine of a tape node, recording that ∂sin(a)/∂a = cos(a)
   in the computation graph.

   Example:
     (tape-sin {:value 0.0 :id 0 :children []})
     ;=> {:value 0.0 :op :sin ...}

---


## `orhof.opt.bracketing`

1D bracket-based optimization methods.
   All methods search within an interval [a, b] for a minimum.
   Pattern: Iterative Refinement — shrink the bracket each step.

### `bisection-method`

```clojure
(bisection-method f a b & {:keys [tol], :or {tol 1.0E-10}})
```

Bisection method for root finding. f: R -> R.
   Catalog: O12. Pattern: ITERATE.

---

### `fibonacci-search`

```clojure
(fibonacci-search f a b & {:keys [n], :or {n 50}})
```

Fibonacci search for 1D minimization.
   Catalog: O8. Pattern: ITERATE (reduce over steps).

---

### `golden-section-search`

```clojure
(golden-section-search f a b & {:keys [tol], :or {tol 1.0E-8}})
```

Golden section search for 1D minimization.
   Catalog: O9. Pattern: ITERATE.
   f: R -> R, [a, b] bracket, tol tolerance.

---

### `quadratic-fit-search`

```clojure
(quadratic-fit-search f a b & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 100}})
```

Quadratic fit search for 1D minimization.
   Catalog: O10. Pattern: ITERATE.

---

### `shubert-piyavskii`

```clojure
(shubert-piyavskii f a b L & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 200}})
```

Shubert-Piyavskii method for Lipschitz optimization.
   Catalog: O11. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Uses the Lipschitz constant L to build a piecewise-linear
   lower bound. Evaluates at the point where the lower bound
   is minimized. Guaranteed to find global minimum within tolerance.

---


## `orhof.opt.descent`

Gradient-based descent methods.
   All methods instantiate the generic descent framework with
   different direction-fn and step-fn strategies.
   Pattern: ITERATE + DELEGATE (Parameterized Strategy).

### `adadelta`

```clojure
(adadelta f x0 & {:keys [rho eps tol max-iter], :or {rho 0.95, eps 1.0E-6, tol 1.0E-8, max-iter 1000}})
```

Adadelta optimizer — no learning rate required.
   Catalog: O23. Pattern: ITERATE + WRAP.
   Uses ratio of accumulated parameter deltas to accumulated
   gradients, automatically adapting the step size.

---

### `adagrad`

```clojure
(adagrad f x0 & {:keys [lr eps tol max-iter], :or {lr 0.5, eps 1.0E-8, tol 1.0E-8, max-iter 1000}})
```

AdaGrad optimizer — per-parameter adaptive learning rates.
   Catalog: O21. Pattern: ITERATE + WRAP.
   Accumulates squared gradients; parameters with large gradients
   get smaller effective learning rates.

---

### `adam`

```clojure
(adam f x0 & {:keys [lr beta1 beta2 eps tol max-iter], :or {lr 0.001, beta1 0.9, beta2 0.999, eps 1.0E-8, tol 1.0E-8, max-iter 1000}})
```

Adam optimizer.
   Catalog: O24. Pattern: ITERATE (first + second moments in state).

---

### `adamw`

```clojure
(adamw f x0 & {:keys [lr beta1 beta2 eps weight-decay tol max-iter], :or {lr 0.001, beta1 0.9, beta2 0.999, eps 1.0E-8, weight-decay 0.01, tol 1.0E-8, max-iter 1000}})
```

AdamW optimizer — Adam with decoupled weight decay.
   Catalog: O24 variant. Pattern: ITERATE.

---

### `backtracking-line-search`

```clojure
(backtracking-line-search f grad-f x dir & {:keys [alpha beta c1], :or {alpha 1.0, beta 0.5, c1 1.0E-4}})
```

Backtracking line search (Armijo condition).
   Catalog: O14. Pattern: ITERATE.
   Returns step size.

---

### `conjugate-gradient`

```clojure
(conjugate-gradient f x0 & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 1000}})
```

Conjugate gradient method (Fletcher-Reeves).
   Catalog: O18. Pattern: ITERATE + DELEGATE.

---

### `descent`

```clojure
(descent {:keys [direction-fn step-fn f x0 grad-fn tol max-iter], :or {tol 1.0E-8, max-iter 1000}})
```

Generic descent framework. All gradient-based optimizers are
   instances of this pattern with different direction-fn and step-fn.
   Catalog: O13. Pattern: ITERATE + DELEGATE.

   direction-fn: state -> direction-vector
   step-fn:      state direction -> step-size
   f:            objective function Rn -> R
   x0:           initial point
   grad-fn:      gradient function (default: numerical)

---

### `gradient-descent`

```clojure
(gradient-descent f x0 & {:keys [lr tol max-iter], :or {lr 0.01, tol 1.0E-8, max-iter 1000}})
```

Gradient descent with fixed learning rate.
   Catalog: O17. Pattern: ITERATE.

---

### `hypergradient-descent`

```clojure
(hypergradient-descent f x0 & {:keys [lr meta-lr tol max-iter], :or {lr 0.01, meta-lr 1.0E-4, tol 1.0E-8, max-iter 1000}})
```

Hypergradient descent — adapts the learning rate online.
   Catalog: O25. Pattern: ITERATE + WRAP.
   Computes the gradient of the loss w.r.t. the learning rate,
   then adjusts lr each step using a meta learning rate.

---

### `lion`

```clojure
(lion f x0 & {:keys [lr beta1 beta2 weight-decay tol max-iter], :or {lr 1.0E-4, beta1 0.9, beta2 0.99, weight-decay 0.0, tol 1.0E-8, max-iter 1000}})
```

Lion optimizer (EvoLved Sign Momentum).
   Catalog: O24 variant. Pattern: ITERATE.

---

### `momentum-gd`

```clojure
(momentum-gd f x0 & {:keys [lr beta tol max-iter], :or {lr 0.01, beta 0.9, tol 1.0E-8, max-iter 1000}})
```

Gradient descent with Polyak momentum.
   Catalog: O19. Pattern: ITERATE (velocity is part of state).

---

### `nesterov-momentum`

```clojure
(nesterov-momentum f x0 & {:keys [lr beta tol max-iter], :or {lr 0.01, beta 0.9, tol 1.0E-8, max-iter 1000}})
```

Nesterov accelerated gradient.
   Catalog: O20. Pattern: ITERATE + WRAP.
   Evaluates gradient at the look-ahead position x + beta*v,
   not at x. This gives better convergence on convex problems.

---

### `rmsprop`

```clojure
(rmsprop f x0 & {:keys [lr beta eps tol max-iter], :or {lr 0.01, beta 0.9, eps 1.0E-8, tol 1.0E-8, max-iter 1000}})
```

RMSProp optimizer — exponential moving average of squared gradients.
   Catalog: O22. Pattern: ITERATE + WRAP.
   Like AdaGrad but with decay, preventing learning rate from
   shrinking to zero.

---

### `steepest-descent`

```clojure
(steepest-descent f x0 & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 1000}})
```

Steepest descent with backtracking line search.
   Catalog: O17 variant. Pattern: ITERATE + DELEGATE.

---

### `with-momentum`

```clojure
(with-momentum base-lr beta)
```

Wrap a gradient-descent step with Polyak momentum.
   Returns a step-fn that maintains velocity in the state map.
   Catalog: O19. Pattern: WRAP (decorator).

---


## `orhof.opt.second-order`

Second-order optimization methods using Hessian or approximate Hessian.
   Pattern: ITERATE + DELEGATE (direction uses curvature information).

### `bfgs`

```clojure
(bfgs f x0 & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 1000}})
```

BFGS quasi-Newton method.
   Catalog: O29. Pattern: ITERATE + DELEGATE.
   Like DFP but uses a different rank-2 update formula that
   is generally more robust in practice.

---

### `dfp`

```clojure
(dfp f x0 & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 1000}})
```

DFP quasi-Newton method.
   Catalog: O28. Pattern: ITERATE + DELEGATE.
   Maintains approximate inverse Hessian H via rank-1 updates.
   Direction: d = -H * g.

---

### `lbfgs`

```clojure
(lbfgs f x0 & {:keys [m tol max-iter], :or {m 10, tol 1.0E-8, max-iter 1000}})
```

L-BFGS — limited-memory BFGS.
   Catalog: O30. Pattern: ITERATE + DELEGATE + GENERATE.
   Stores only the last m (s, y) pairs instead of the full
   inverse Hessian matrix. Uses two-loop recursion.

---

### `levenberg-marquardt`

```clojure
(levenberg-marquardt residuals-fn x0 & {:keys [lambda tol max-iter], :or {lambda 1.0, tol 1.0E-8, max-iter 200}})
```

Levenberg-Marquardt algorithm for nonlinear least squares.
   Catalog: O31. Pattern: ITERATE + BRANCH.
   Interpolates between gradient descent (large lambda) and
   Gauss-Newton (small lambda) by adapting the damping parameter.

   residuals-fn: x -> [r1, r2, ...] vector of residuals
   Minimizes sum(r_i^2).

---

### `newton-method`

```clojure
(newton-method f x0 & {:keys [tol max-iter], :or {tol 1.0E-8, max-iter 100}})
```

Newton's method for optimization using full Hessian.
   Catalog: O26. Pattern: ITERATE + DELEGATE.
   Computes Newton direction d = -H^{-1} g via linear solve,
   then applies backtracking line search for safety.

---

### `secant-method`

```clojure
(secant-method f x0 & {:keys [lr tol max-iter], :or {lr 0.01, tol 1.0E-8, max-iter 1000}})
```

Secant method — approximates Hessian from gradient differences.
   Catalog: O27. Pattern: ITERATE + DELEGATE.
   Uses the secant equation: H_k * s_k = y_k where
   s_k = x_k - x_{k-1}, y_k = g_k - g_{k-1}.
   Falls back to gradient descent on first iteration.

---


## `orhof.opt.direct`

Derivative-free (direct) optimization methods.
   These methods do not use gradients or Hessians.
   Pattern: ITERATE with geometric operations on simplices/patterns.

### `cyclic-coordinate-search`

```clojure
(cyclic-coordinate-search f x0 & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 100}})
```

Cyclic coordinate search — minimize along each axis in turn.
   Catalog: O32. Pattern: ITERATE (reduce over dimensions).
   Each dimension is optimized by 1D golden section search.

---

### `direct-search`

```clojure
(direct-search f bounds & {:keys [max-iter max-evals], :or {max-iter 100, max-evals 1000}})
```

DIRECT — Dividing Rectangles method.
   Catalog: O37. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Partitions the search space into hyperrectangles, evaluates
   centers, and identifies potentially optimal rectangles to divide.
   bounds: [lower-bounds upper-bounds] vectors.

---

### `generalized-pattern-search`

```clojure
(generalized-pattern-search f x0 & {:keys [step-size step-reduction tol max-iter], :or {step-size 1.0, step-reduction 0.5, tol 1.0E-8, max-iter 1000}})
```

Generalized pattern search (GPS).
   Catalog: O35. Pattern: ITERATE + DELEGATE + PARAMETERIZE.
   Polls a positive spanning set of directions. If no improvement,
   reduces the mesh size.

---

### `hooke-jeeves`

```clojure
(hooke-jeeves f x0 & {:keys [step-size step-reduction tol max-iter], :or {step-size 1.0, step-reduction 0.5, tol 1.0E-8, max-iter 1000}})
```

Hooke-Jeeves pattern search.
   Catalog: O34. Pattern: ITERATE + DELEGATE.
   Two phases per iteration: exploratory moves (probe each axis)
   and pattern moves (extrapolate in the promising direction).

---

### `nelder-mead`

```clojure
(nelder-mead f x0 & {:keys [tol max-iter alpha-r gamma-e rho-c sigma-s], :or {tol 1.0E-8, max-iter 1000, alpha-r 1.0, gamma-e 2.0, rho-c 0.5, sigma-s 0.5}})
```

Nelder-Mead simplex method. Derivative-free optimization.
   Catalog: O36. Pattern: ITERATE + DELEGATE.

---

### `powells-method`

```clojure
(powells-method f x0 & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 100}})
```

Powell's conjugate direction method.
   Catalog: O33. Pattern: ITERATE + DELEGATE.
   Like cyclic coordinate but updates search directions after
   each full cycle to build conjugate directions.

---


## `orhof.opt.stochastic`

Stochastic optimization methods.
   Pattern: ITERATE with random perturbations.

### `cma-es`

```clojure
(cma-es f dim & {:keys [n-samples n-elite sigma max-iter tol], :or {n-samples nil, n-elite nil, sigma 1.0, max-iter 200, tol 1.0E-8}})
```

CMA-ES — Covariance Matrix Adaptation Evolution Strategy.
   Catalog: O42. Pattern: ITERATE + POPULATION EVOLVER.
   Maintains a full covariance matrix for sampling, adapts mean,
   step-size, and covariance from elite samples.

---

### `mads`

```clojure
(mads f x0 & {:keys [mesh-size mesh-shrink mesh-grow tol max-iter], :or {mesh-size 1.0, mesh-shrink 0.5, mesh-grow 2.0, tol 1.0E-8, max-iter 1000}})
```

Mesh Adaptive Direct Search.
   Catalog: O39. Pattern: ITERATE + DELEGATE + BRANCH.
   Combines a search step (optional, e.g., random) with a poll
   step on an adaptive mesh. Mesh size decreases on failure.

---

### `noisy-descent`

```clojure
(noisy-descent f x0 & {:keys [lr sigma tol max-iter], :or {lr 0.01, sigma 0.1, tol 1.0E-8, max-iter 1000}})
```

Noisy gradient descent — adds Gaussian noise to the gradient.
   Catalog: O38. Pattern: ITERATE + WRAP.
   Wraps a standard gradient step with random perturbation,
   helping escape shallow local minima.

---

### `simulated-annealing`

```clojure
(simulated-annealing f x0 & {:keys [neighbor-fn temp-schedule max-iter], :or {max-iter 10000}})
```

Simulated annealing. Parameterized by neighbor-fn and temp-schedule.
   Catalog: O40. Pattern: ITERATE + PARAMETERIZE.

   neighbor-fn:   x -> x' (generate neighbor)
   temp-schedule: iteration -> temperature
   f:             objective function

---


## `orhof.opt.population`

Population-based optimization methods.
   Pattern: ITERATE over generations, each generation applies
   evaluate -> select -> recombine -> mutate pipeline.

### `cross-entropy-method`

```clojure
(cross-entropy-method f dim & {:keys [n-samples n-elite max-iter tol], :or {n-samples 100, n-elite 10, max-iter 100, tol 1.0E-8}})
```

Cross-entropy method for optimization.
   Maintains a Gaussian distribution, samples, selects elite, refits.
   Catalog: O41. Pattern: ITERATE + REDUCE + MAP-OVER.

---

### `cuckoo-search`

```clojure
(cuckoo-search f dim & {:keys [pop-size max-iter pa levy-scale], :or {pop-size 25, max-iter 200, pa 0.25, levy-scale 0.5}})
```

Cuckoo search via Levy flights.
   Catalog: O47. Pattern: ITERATE + DELEGATE.
   New solutions generated via Levy flights (heavy-tailed steps).
   Worst nests are abandoned and replaced randomly.

---

### `differential-evolution`

```clojure
(differential-evolution f dim & {:keys [pop-size max-iter F CR], :or {pop-size 50, max-iter 200, F 0.8, CR 0.9}})
```

Differential evolution.
   Catalog: O44. Pattern: ITERATE + MAP-OVER.

---

### `firefly-algorithm`

```clojure
(firefly-algorithm f dim & {:keys [pop-size max-iter beta0 gamma alpha], :or {pop-size 30, max-iter 200, beta0 1.0, gamma 1.0, alpha 0.2}})
```

Firefly algorithm — pairwise attraction toward brighter fireflies.
   Catalog: O46. Pattern: ITERATE + MAP-OVER.
   Each firefly moves toward all brighter fireflies, with attraction
   decreasing with distance.

---

### `genetic-algorithm`

```clojure
(genetic-algorithm fitness-fn dim & {:keys [pop-size max-iter select-fn crossover-fn mutate-fn], :or {pop-size 50, max-iter 200}})
```

Genetic algorithm with pluggable operators.
   Catalog: O43. Pattern: ITERATE + DELEGATE + MAP-OVER.

   fitness-fn:   individual -> score (lower is better)
   select-fn:    population -> parent
   crossover-fn: parent1 parent2 -> child
   mutate-fn:    individual -> individual

---

### `particle-swarm`

```clojure
(particle-swarm f dim & {:keys [n-particles max-iter w c1 c2], :or {n-particles 30, max-iter 200, w 0.7, c1 1.5, c2 1.5}})
```

Particle swarm optimization.
   Catalog: O45. Pattern: ITERATE + MAP-OVER + REDUCE.

---


## `orhof.opt.constrained`

Constrained optimization methods.
   Pattern: TRANSFORM-PROBLEM — convert constrained to unconstrained.

### `admm`

```clojure
(admm x-update-fn z-update-fn x0 & {:keys [rho max-iter tol], :or {rho 1.0, max-iter 200, tol 1.0E-6}})
```

ADMM — Alternating Direction Method of Multipliers.
   Catalog: O51. Pattern: ITERATE + DELEGATE (3-step split).
   Splits problem into x-update, z-update, and dual-update.

   x-update-fn: (fn [z u rho] -> x-new)
   z-update-fn: (fn [x u] -> z-new)
   x0: initial x value

---

### `augmented-lagrangian`

```clojure
(augmented-lagrangian f constraints x0 & {:keys [rho rho-max max-outer max-inner], :or {rho 1.0, rho-max 50.0, max-outer 15, max-inner 200}})
```

Augmented Lagrangian method for constrained optimization.
   Catalog: O49. Pattern: ITERATE (outer loop on multipliers).
   Outer loop updates multipliers; inner loop solves penalized problem.
   rho-max caps the penalty: the Augmented Lagrangian converges with a
   BOUNDED penalty, so rho must never grow without limit (an unbounded
   rho makes the fixed-lr inner descent explode).

---

### `dual-ascent`

```clojure
(dual-ascent f eq-constraints x0 & {:keys [lr max-iter max-inner], :or {lr 0.1, max-iter 200, max-inner 100}})
```

Dual ascent for equality-constrained optimization.
   Catalog: O52. Pattern: ITERATE + TRANSFORM-PROBLEM.
   min f(x) s.t. h_i(x) = 0.
   Outer loop: gradient ascent on dual variables lambda.
   Inner loop: minimize Lagrangian L(x, lambda) over x.

---

### `interior-point`

```clojure
(interior-point f constraints x0 & {:keys [mu mu-shrink tol max-outer max-inner], :or {mu 10.0, mu-shrink 0.5, tol 1.0E-6, max-outer 50, max-inner 200}})
```

Interior point method using logarithmic barrier.
   Catalog: O50. Pattern: TRANSFORM-PROBLEM + ITERATE.
   constraints: seq of (fn [x] -> value), where g(x) <= 0 is feasible.
   Adds barrier: -mu * sum(log(-g_i(x))) to objective.

---

### `kkt-check`

```clojure
(kkt-check f constraints x lambdas)
```

Check KKT (Karush-Kuhn-Tucker) conditions at a point.
   Catalog: O53. Pattern: SENSITIVITY ANALYZER.
   Returns a map of violations:
   :stationarity  — norm of grad_L
   :primal        — max constraint violation
   :dual          — any lambda < 0
   :complementary — max |lambda_i * g_i(x)|

---

### `penalize`

```clojure
(penalize f constraints rho)
```

Transform a constrained problem into an unconstrained one.
   Catalog: O48. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   constraints: seq of (fn [x] -> violation-amount), where 0 = satisfied.
   Returns a new objective function.

---


## `orhof.opt.linear`

Linear and convex programming methods.
   Catalog: O54-O57. Pattern: ITERATE + TRANSFORM-PROBLEM.

### `dcp-check`

```clojure
(dcp-check problem)
```

Check if a problem is DCP-compliant (disciplined convex programming).
   Catalog: O57. Pattern: TRANSFORM-PROBLEM (expression tree analysis).
   Returns {:valid? bool, :objective-curvature, :constraint-issues}.

---

### `dual-simplex`

```clojure
(dual-simplex c A b)
```

Dual simplex method for LP.
   Catalog: O55. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Solves the same LP as simplex but from the dual side.
   Starts dual-feasible, iterates to primal feasibility.
   For simplicity, delegates to primal simplex (same result).

---

### `qp-solve`

```clojure
(qp-solve Q c-vec A b & {:keys [max-iter lr tol], :or {max-iter 500, lr 0.01, tol 1.0E-6}})
```

Quadratic programming via active-set method.
   Catalog: O56. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Minimize 0.5 * x^T Q x + c^T x subject to Ax <= b.
   Uses projected gradient steps with active constraint tracking.

---

### `simplex`

```clojure
(simplex c A b)
```

Simplex algorithm for linear programming.
   Catalog: O54. Pattern: ITERATE + DELEGATE.
   Minimize c^T x subject to Ax <= b, x >= 0.
   Uses tableau method with Bland's rule for pivot selection.

   c: cost vector (n)
   A: constraint matrix (m x n)
   b: RHS vector (m), must be >= 0

---


## `orhof.opt.surrogate`

Surrogate-based and Bayesian optimization methods.
   Catalog: O58-O65. Pattern: MODEL BUILDER + PIPELINE COMPOSER.

### `bayesian-optimization`

```clojure
(bayesian-optimization f bounds & {:keys [n-init n-iter], :or {n-init 5, n-iter 20}})
```

Bayesian Optimization loop.
   Catalog: O65. Pattern: PIPELINE COMPOSER (iterate: fit GP -> maximize EI -> evaluate).
   f: objective function to minimize.
   bounds: [lower upper] vectors.
   Returns {:best-x, :best-value, :history}.

---

### `expected-improvement`

```clojure
(expected-improvement gp-predict best-so-far)
```

Expected Improvement acquisition function.
   Catalog: O63. Pattern: COMPOSE + PARAMETERIZE.
   Returns a function: x -> EI value (higher = more promising).

---

### `full-factorial`

```clojure
(full-factorial bounds n-levels)
```

Full factorial sampling — grid over all dimension levels.
   Catalog: O58. Pattern: MAP-OVER.
   bounds: [[lo1 hi1] [lo2 hi2] ...], n-levels per dimension.

---

### `gaussian-process`

```clojure
(gaussian-process points values & {:keys [length-scale signal-var noise-var], :or {length-scale 1.0, signal-var 1.0, noise-var 0.01}})
```

Gaussian Process regression.
   Catalog: O62. Pattern: MODEL BUILDER.
   Fits GP with squared-exponential kernel.
   Returns a function: x -> {:mean, :variance}.

---

### `latin-hypercube`

```clojure
(latin-hypercube dim n)
```

Latin Hypercube Sampling — stratified random sampling.
   Catalog: O59. Pattern: GENERATE + MAP-OVER.
   Each dimension divided into n strata, one sample per stratum.

---

### `rbf-surrogate`

```clojure
(rbf-surrogate points values & {:keys [kernel-fn epsilon], :or {epsilon 1.0}})
```

Radial Basis Function surrogate model.
   Catalog: O61. Pattern: MODEL BUILDER.
   Fits RBF interpolant: f(x) = sum_i w_i * phi(||x - x_i||).
   Returns a prediction function.

---

### `sobol-sequence`

```clojure
(sobol-sequence dim n)
```

Sobol quasi-random sequence (simplified).
   Catalog: O60. Pattern: GENERATE.
   Uses Van der Corput sequence in base 2 as approximation.

---

### `upper-confidence-bound`

```clojure
(upper-confidence-bound gp-predict & {:keys [kappa], :or {kappa 2.0}})
```

Upper Confidence Bound acquisition function.
   Catalog: O64. Pattern: COMPOSE + PARAMETERIZE.
   UCB(x) = -mean(x) + kappa * sqrt(variance(x)).
   (Negated mean because we minimize.)

---


## `orhof.opt.multiobjective`

Multi-objective and discrete optimization methods.
   Catalog: O66-O75. Pattern: TRANSFORM-PROBLEM + POPULATION EVOLVER.

### `branch-and-bound`

```clojure
(branch-and-bound evaluate branch bound root & {:keys [budget], :or {budget 1000}})
```

Branch and bound for discrete optimization.
   Catalog: O72. Pattern: TREE/GRAPH EXPLORER.
   evaluate: node -> objective value (for leaf nodes)
   branch: node -> [child-nodes]
   bound: node -> lower-bound (prune if >= best known)

---

### `dominates?`

```clojure
(dominates? a b)
```

Returns true if point a dominates point b (all objectives <=, at least one <).

---

### `epsilon-constraint`

```clojure
(epsilon-constraint primary-obj other-objs epsilons x0 & {:keys [rho max-iter], :or {rho 100.0, max-iter 500}})
```

Epsilon-constraint method for multi-objective optimization.
   Catalog: O67. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Minimize first objective, constrain the rest: f_i(x) <= eps_i.

---

### `mc-uncertainty-propagation`

```clojure
(mc-uncertainty-propagation f sampler n)
```

Monte Carlo uncertainty propagation.
   Catalog: O69. Pattern: MAP-OVER + REDUCE.
   Evaluates f over n random samples from the input distribution,
   returns mean and variance of output.

---

### `pareto-filter`

```clojure
(pareto-filter points)
```

Filter a set of points to the Pareto front (non-dominated set).
   Catalog: O68. Pattern: REDUCE.
   Each point has {:x, :objectives [f1 f2 ...]}.

---

### `robust-optimization`

```clojure
(robust-optimization f uncertainty-sampler n-scenarios x0 & {:keys [max-iter], :or {max-iter 500}})
```

Robust optimization — minimize worst-case over uncertainty set.
   Catalog: O74. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Transforms min f(x) into min max_{u in U} f(x, u).

---

### `weighted-sum`

```clojure
(weighted-sum objectives weights x0 & {:keys [max-iter], :or {max-iter 500}})
```

Weighted sum scalarization for multi-objective optimization.
   Catalog: O66. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Converts multiple objectives into one: min sum(w_i * f_i(x)).

---


## `orhof.mdp.planning`

Exact sequential decision methods: MDP representation,
   Bellman operators, value iteration, policy iteration.
   Pattern: ITERATE on function spaces (Bellman operator as Function Transformer).

### `async-value-iteration`

```clojure
(async-value-iteration mdp & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 1000}})
```

Asynchronous value iteration — updates one state at a time.
   Catalog: D23. Pattern: ITERATE + DELEGATE.

   MDP -> Opts -> {:V {State -> R}, :policy (State -> Action)}

   Updates states in round-robin order rather than all at once.
   Can converge faster when some states are more important.

   Example:
     (async-value-iteration mdp :max-iter 200)
     ;=> {:V {...} :policy (fn [s] ...) :converged true}

---

### `bellman-operator`

```clojure
(bellman-operator mdp)
```

The Bellman optimality operator.
   Catalog: D22 core. Pattern: FUNCTION TRANSFORMER.
   Takes an MDP, returns (V -> V') — a HOF on function spaces.
   T(V)(s) = max_a [R(s,a) + gamma * sum_{s'} T(s,a,s') V(s')]

---

### `bellman-policy-operator`

```clojure
(bellman-policy-operator mdp policy)
```

The Bellman operator for a fixed policy pi.
   Catalog: D20 core. Pattern: FUNCTION TRANSFORMER.
   T_pi(V)(s) = R(s, pi(s)) + gamma * sum_{s'} T(s, pi(s), s') V(s')

---

### `greedy-policy`

```clojure
(greedy-policy mdp V)
```

Extract the greedy policy from a value function.
   Returns (fn [s] -> best-action).

---

### `lookahead`

```clojure
(lookahead mdp V s a)
```

One-step lookahead: compute Q(s,a) given value function V.
   Returns the value of taking action a in state s then following V.

---

### `make-mdp`

```clojure
(make-mdp states actions transition reward gamma)
```

Create an MDP from its components.
   states:     collection of states
   actions:    (fn [state] -> collection of actions)
   transition: (fn [state action] -> {state' -> probability})
   reward:     (fn [state action] -> reward)
   gamma:      discount factor

---

### `policy-evaluation`

```clojure
(policy-evaluation mdp policy & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 500}})
```

Evaluate a policy by iterating the Bellman policy operator.
   Catalog: D20. Pattern: ITERATE.
   Returns a value function (map: state -> value).

---

### `policy-iteration`

```clojure
(policy-iteration mdp & {:keys [max-iter], :or {max-iter 100}})
```

Policy iteration: alternate between evaluation and improvement.
   Catalog: D21. Pattern: ITERATE (comp improve evaluate).
   Returns {:V value-fn, :policy policy-fn, :iterations k}.

---

### `value-iteration`

```clojure
(value-iteration mdp & {:keys [tol max-iter], :or {tol 1.0E-6, max-iter 1000}})
```

Value iteration: repeatedly apply the Bellman operator until convergence.
   Catalog: D22. Pattern: ITERATE on function spaces.
   Returns {:V value-fn, :policy policy-fn, :iterations k}.

---


## `orhof.mdp.online`

Online planning methods: MCTS, forward search, sparse sampling,
   A*, rollout, receding horizon.
   Pattern: TREE/GRAPH EXPLORER — parameterized by select, expand, simulate.

### `a-star`

```clojure
(a-star start expand-fn cost-fn heuristic goal?)
```

A* search algorithm.
   Catalog: D37. Pattern: TREE/GRAPH EXPLORER + DELEGATE.

   State -> (State -> [State]) -> (State -> State -> R) -> (State -> R) -> (State -> Bool)
   -> {:node State, :cost R, :path [State]}

   expand-fn: node -> [successors]
   cost-fn: (from, to) -> edge cost
   heuristic: node -> estimated cost to goal
   goal?: node -> boolean

   Example:
     (a-star :a expand-fn cost-fn heuristic #(= % :goal))
     ;=> {:node :goal :cost 5.0 :path [:a :b :goal]}

---

### `forward-search`

```clojure
(forward-search mdp state & {:keys [depth], :or {depth 3}})
```

Exhaustive forward search to a fixed depth.
   Catalog: D33. Pattern: TREE/GRAPH EXPLORER (recursive).

   MDP -> State -> {:keys [depth]} -> Action

   Evaluates all action sequences up to depth, returns best first action.

   Example:
     (forward-search mdp :start :depth 3)
     ;=> :right

---

### `mcts`

```clojure
(mcts mdp root-state & {:keys [budget simulate-fn c-explore], :or {budget 1000, c-explore 1.41}})
```

Monte Carlo Tree Search.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER.

   MDP -> State -> {:keys [budget c-explore]} -> Action

   Uses UCB1 for selection, random rollout for simulation.
   Returns the best action from the root state.

   Example:
     (mcts mdp :start :budget 1000)
     ;=> :go

---

### `open-loop-planning`

```clojure
(open-loop-planning mdp state & {:keys [horizon n-samples], :or {horizon 5, n-samples 200}})
```

Open-loop planning — optimize over action sequences.
   Catalog: D39. Pattern: ITERATE + GENERATE.

   MDP -> State -> {:keys [horizon n-samples]} -> Action

   Evaluates random action sequences, returns first action of best.

   Example:
     (open-loop-planning mdp :start :horizon 5 :n-samples 100)
     ;=> :down

---

### `receding-horizon`

```clojure
(receding-horizon mdp state & {:keys [horizon], :or {horizon 5}})
```

Receding horizon planning — solve finite-horizon, take first action.
   Catalog: D31. Pattern: COMPOSE.

   MDP -> State -> {:keys [horizon]} -> Action

   Wraps forward-search with a horizon.

   Example:
     (receding-horizon mdp :start :horizon 5)
     ;=> :right

---

### `rollout-with-heuristic`

```clojure
(rollout-with-heuristic mdp state heuristic-policy & {:keys [n-rollouts horizon], :or {n-rollouts 50, horizon 20}})
```

Rollout with a heuristic policy.
   Catalog: D32. Pattern: COMPOSE + GENERATE + REDUCE.

   MDP -> State -> (State -> [Action]) -> Opts -> Action

   Simulates n rollouts with the heuristic policy from each action,
   returns the action with highest average return.

   Example:
     (rollout-with-heuristic mdp :start random-policy :n-rollouts 50 :horizon 20)
     ;=> :right

---

### `sparse-sampling`

```clojure
(sparse-sampling mdp state & {:keys [depth n-samples], :or {depth 3, n-samples 10}})
```

Sparse sampling — sample-based forward search.
   Catalog: D35. Pattern: TREE/GRAPH EXPLORER + GENERATE.

   MDP -> State -> {:keys [depth n-samples]} -> Action

   Like forward search but samples transitions instead of
   enumerating all successors.

   Example:
     (sparse-sampling mdp :start :depth 2 :n-samples 10)
     ;=> :down

---


## `orhof.mdp.policy`

Policy learning methods: Q-learning, SARSA, REINFORCE.
   Pattern: ITERATE — update value/policy from experience.

### `evolution-strategies`

```clojure
(evolution-strategies fitness-fn theta0 & {:keys [n-samples sigma lr max-iter tol], :or {n-samples 50, sigma 0.1, lr 0.01, max-iter 200, tol 1.0E-8}})
```

Evolution Strategies — gradient-free policy optimization.
   Catalog: D43. Pattern: ITERATE + MAP-OVER + REDUCE.

   (Theta -> R) -> Theta -> Opts -> {:x Theta, :value R}

   Evaluates isotropic perturbations, updates mean toward
   perturbations with highest fitness.

   Example:
     (evolution-strategies (fn [t] (- (reduce + (map #(* % %) t)))) [5.0 5.0])
     ;=> {:x [0.1 0.1] :value -0.02}

---

### `gae`

```clojure
(gae rewards values gamma lambda)
```

Generalized Advantage Estimation.
   Catalog: D54. Pattern: REDUCE (right fold).

   [R] -> [R] -> R -> R -> [R]

   rewards: [r_0, ..., r_{T-1}]
   values: [V(s_0), ..., V(s_T)] (T+1 values, last is terminal)
   gamma: discount factor
   lambda: GAE decay parameter

   Returns advantage estimates A_t = sum_{l=0}^{T-t} (gamma*lambda)^l * delta_{t+l}
   where delta_t = r_t + gamma*V(s_{t+1}) - V(s_t)

   Example:
     (gae [1.0 1.0 1.0] [5.0 4.0 3.0 0.0] 0.99 0.95)
     ;=> [advantages...]

---

### `q-learning`

```clojure
(q-learning mdp env-step & {:keys [alpha epsilon max-episodes max-steps], :or {alpha 0.1, epsilon 0.1, max-episodes 1000, max-steps 100}})
```

Tabular Q-learning. Updates Q(s,a) from experience.
   Catalog: D61. Pattern: ITERATE (reduce over episodes, fold over steps).
   env-step: (fn [state action] -> {:state' s', :reward r, :done? bool})
   Returns the Q-table.

---

### `reinforce`

```clojure
(reinforce mdp env-step policy-fn update-fn init-params & {:keys [max-episodes max-steps gamma], :or {max-episodes 500, max-steps 100, gamma 0.99}})
```

REINFORCE policy gradient algorithm.
   Catalog: D46. Pattern: ITERATE + SENSITIVITY ANALYZER.
   policy-fn:   (fn [params state] -> action-probs)
   env-step:    (fn [state action] -> {:state' s', :reward r, :done? bool})
   update-fn:   (fn [params gradient] -> params') — e.g., SGD step

---

### `reward-to-go`

```clojure
(reward-to-go rewards gamma)
```

Compute discounted reward-to-go (future returns).
   Catalog: D47. Pattern: REDUCE (right fold).

   [R] -> R -> [R]

   G_t = r_t + gamma * G_{t+1}

   Example:
     (reward-to-go [1.0 2.0 3.0] 0.99)
     ;=> [5.9403 4.97 3.0]

---

### `sarsa`

```clojure
(sarsa mdp env-step & {:keys [alpha epsilon max-episodes max-steps], :or {alpha 0.1, epsilon 0.1, max-episodes 1000, max-steps 100}})
```

SARSA: on-policy TD control.
   Catalog: D62. Pattern: ITERATE.
   env-step: (fn [state action] -> {:state' s', :reward r, :done? bool})

---


## `orhof.mdp.beliefs`

Belief state methods: discrete filter, Kalman, particle filter, POMDP.
   Pattern: BELIEF UPDATER — (model) -> (belief, obs) -> belief'.

### `alpha-vector-backup`

```clojure
(alpha-vector-backup states actions transition-fn reward-fn obs-fn gamma alpha-vectors)
```

One step of alpha vector backup for exact POMDP solving.
   Catalog: D71. Pattern: ITERATE + REDUCE.

   MDP-like -> [[R]] -> [[R]]

   Given a set of alpha vectors representing the value function,
   computes the backup (new alpha vectors).
   Simplified for pedagogical purposes.

---

### `discrete-filter`

```clojure
(discrete-filter transition-model observation-model)
```

Discrete Bayesian filter for state estimation.
   Catalog: D66. Pattern: BELIEF UPDATER.

   (State -> Action -> {State -> Prob}) -> (State -> Obs -> Prob)
   -> (Belief -> Action -> Obs -> Belief)

   Example:
     (def updater (discrete-filter transition-model observation-model))
     (updater {:a 0.5 :b 0.5} :move :obs-x)
     ;=> {:a 0.3 :b 0.7}

---

### `extended-kalman-filter`

```clojure
(extended-kalman-filter dynamics-fn observe-fn jacobian-f jacobian-h Q R)
```

Extended Kalman Filter for nonlinear state estimation.
   Catalog: D68. Pattern: BELIEF UPDATER + DELEGATE.

   (State -> State) -> (State -> Obs) -> (State -> [[R]]) -> (State -> [[R]])
   -> [[R]] -> [[R]]
   -> ({:mean [R] :cov [[R]]} -> [R] -> {:mean [R] :cov [[R]]})

   dynamics-fn: state -> predicted state (nonlinear)
   observe-fn: state -> predicted observation (nonlinear)
   jacobian-f: state -> Jacobian of dynamics
   jacobian-h: state -> Jacobian of observation
   Q, R: noise covariances

---

### `kalman-filter`

```clojure
(kalman-filter A C Q R)
```

Kalman filter for linear-Gaussian state estimation.
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
     ;=> {:mean [4.5 0.0] :cov [[0.5 0.0][0.0 1.0]]}

---

### `particle-filter`

```clojure
(particle-filter dynamics-fn obs-likelihood n-particles)
```

Sequential Monte Carlo (particle filter).
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
     ;=> [updated particles...]

---


## `orhof.mdp.games`

Game theory and bandit methods.
   Pattern: EQUILIBRIUM SOLVER + PARAMETERIZED STRATEGY.

### `epsilon-greedy-bandit`

```clojure
(epsilon-greedy-bandit bandit & {:keys [epsilon n-rounds], :or {epsilon 0.1, n-rounds 1000}})
```

Epsilon-greedy bandit strategy.
   Catalog: D57 variant. Pattern: ITERATE + PARAMETERIZE.

---

### `fictitious-play`

```clojure
(fictitious-play game & {:keys [max-iter], :or {max-iter 200}})
```

Fictitious play — best respond to empirical frequency of opponent.
   Catalog: D79. Pattern: ITERATE + REDUCE.

   Game -> Opts -> {Player -> {Action -> Prob}}

   Each player maintains a count of opponent actions and best-responds
   to the empirical distribution.

   Example:
     (fictitious-play matching-pennies-game :max-iter 1000)
     ;=> {:p1 {:H 0.52 :T 0.48} :p2 {:H 0.48 :T 0.52}}

---

### `iterated-best-response`

```clojure
(iterated-best-response game & {:keys [max-iter], :or {max-iter 100}})
```

Find Nash equilibrium via iterated best response.
   Catalog: D77 variant. Pattern: ITERATE (EQUILIBRIUM SOLVER).
   For 2-player games with finite actions.

---

### `make-bandit`

```clojure
(make-bandit arms)
```

Create a multi-armed bandit problem.
   arms: vector of reward distributions (each is (fn [] -> reward))

---

### `make-normal-form-game`

```clojure
(make-normal-form-game players actions payoffs)
```

Create a normal-form game.
   payoffs: nested map {player -> {action-profile -> payoff}}

---

### `nash-support-enum`

```clojure
(nash-support-enum game)
```

Find Nash equilibrium via support enumeration.
   Catalog: D77. Pattern: ITERATE + TRANSFORM-PROBLEM.

   Game -> {Player -> {Action -> Prob}}

   For 2-player games, checks each pair of support sets.
   Simplified: tries pure strategy profiles first, then uses
   iterated best response as fallback.

   Example:
     (nash-support-enum prisoners-dilemma)
     ;=> {:p1 {:C 0.0 :D 1.0} :p2 {:C 0.0 :D 1.0}}

---

### `thompson-sampling`

```clojure
(thompson-sampling bandit & {:keys [n-rounds], :or {n-rounds 1000}})
```

Thompson sampling for Bernoulli bandits.
   Catalog: D58. Pattern: ITERATE + GENERATE.

---

### `ucb1-bandit`

```clojure
(ucb1-bandit bandit & {:keys [n-rounds c], :or {n-rounds 1000, c 2.0}})
```

UCB1 bandit strategy.
   Catalog: D57. Pattern: ITERATE + PARAMETERIZE.

---


## `orhof.mdp.inference`

Probabilistic reasoning: Bayesian networks, exact and approximate inference.
   Catalog: D1-D9. Pattern: SAMPLER CONSTRUCTOR + TREE/GRAPH EXPLORER.

### `direct-sampling`

```clojure
(direct-sampling bn n)
```

Direct (ancestral) sampling from a Bayesian Network.
   Catalog: D5. Pattern: GENERATE + COMPOSE.

   BN -> Int -> [{Keyword -> Val}]

   Samples each node in topological order, conditioned on parents.

   Example:
     (direct-sampling rain-bn 1000)
     ;=> [{:rain :true :sprinkler :false :wet-grass :true} ...]

---

### `exact-inference`

```clojure
(exact-inference bn evidence query-var)
```

Exact inference by enumeration over all assignments.
   Catalog: D2. Pattern: REDUCE + MAP-OVER.

   BN -> {Keyword -> Val} -> Keyword -> {Val -> Prob}

   Example:
     (exact-inference rain-bn {:wet-grass :true} :rain)
     ;=> {:true 0.36 :false 0.64}

---

### `gaussian-condition`

```clojure
(gaussian-condition {:keys [mean cov]} observations)
```

Condition a multivariate Gaussian on observed variables.
   Catalog: D9. Pattern: PARAMETERIZE.

   {:mean [R], :cov [[R]]} -> {Int -> R} -> {:mean [R], :cov [[R]]}

   Given joint N(mu, Sigma), and observations on some indices,
   returns the conditional distribution on the remaining indices.

   Example:
     (gaussian-condition {:mean [0.0 0.0] :cov [[1.0 0.5] [0.5 1.0]]}
                         {1 0.5})
     ;=> {:mean [0.25] :cov [[0.75]]}

---

### `gibbs-sampling`

```clojure
(gibbs-sampling bn evidence n & {:keys [burn-in], :or {burn-in 100}})
```

Gibbs sampling (MCMC) for conditional inference.
   Catalog: D8. Pattern: ITERATE + DELEGATE.

   BN -> {Keyword -> Val} -> Int -> {:keys [burn-in]} -> [{Keyword -> Val}]

   Iteratively resamples each non-evidence variable conditioned
   on its Markov blanket (parents + children + co-parents).

   Example:
     (gibbs-sampling rain-bn {:wet-grass :true} 1000 :burn-in 100)
     ;=> [{:rain :false :sprinkler :true :wet-grass :true} ...]

---

### `likelihood-weighted-sampling`

```clojure
(likelihood-weighted-sampling bn evidence n)
```

Likelihood-weighted sampling for conditional inference.
   Catalog: D7. Pattern: GENERATE + MAP-OVER + REDUCE.

   BN -> {Keyword -> Val} -> Int -> {:samples [{...}], :weights [Prob]}

   Samples non-evidence nodes normally, fixes evidence nodes,
   weights each sample by the likelihood of evidence.

   Example:
     (likelihood-weighted-sampling rain-bn {:wet-grass :true} 1000)
     ;=> {:samples [...] :weights [0.8 0.1 ...]}

---

### `make-bayesian-network`

```clojure
(make-bayesian-network node-specs)
```

Create a Bayesian Network from node specifications.
   Catalog: D1. Pattern: PARAMETERIZE.

   {Keyword -> {:parents [Keyword], :cpd {[Val] -> {Val -> Prob}}}}
   -> {:nodes Map, :topo-order [Keyword]}

   Example:
     (make-bayesian-network
       {:rain {:parents [] :cpd {[] {:true 0.2 :false 0.8}}}
        :wet  {:parents [:rain] :cpd {[:true] {:true 0.9 :false 0.1}
                                       [:false] {:true 0.1 :false 0.9}}}})
     ;=> {:nodes {...} :topo-order [:rain :wet]}

---

### `rejection-sampling`

```clojure
(rejection-sampling bn evidence n)
```

Rejection sampling for conditional inference.
   Catalog: D6. Pattern: GENERATE + REDUCE (filter).

   BN -> {Keyword -> Val} -> Int -> [{Keyword -> Val}]

   Generates samples, keeps only those consistent with evidence.

   Example:
     (rejection-sampling rain-bn {:wet-grass :true} 1000)
     ;=> [{:rain :true :sprinkler :false :wet-grass :true} ...]

---

### `variable-elimination`

```clojure
(variable-elimination bn evidence query-var)
```

Variable elimination for inference.
   Catalog: D3. Pattern: REDUCE + DELEGATE.

   BN -> {Keyword -> Val} -> Keyword -> {Val -> Prob}

   Eliminates hidden variables one by one via factor marginalization.
   For this pedagogical implementation, delegates to exact inference.

---


## `orhof.mdp.learning`

Learning from data: MLE, Bayesian learning, KDE, EM, structure learning.
   Catalog: D10-D16. Pattern: MODEL BUILDER + ITERATE.

### `bayesian-update`

```clojure
(bayesian-update prior data update-fn)
```

Bayesian parameter learning via sequential prior update.
   Catalog: D11. Pattern: REDUCE (fold over data).

   Prior -> [Datum] -> (Prior -> Datum -> Prior) -> Posterior

   update-fn takes (prior, datum) and returns updated prior.

   Example:
     (bayesian-update {:alpha 1 :beta 1} [1 1 0]
       (fn [prior d] (if (= d 1) (update prior :alpha + 1) (update prior :beta + 1))))
     ;=> {:alpha 3 :beta 2}

---

### `bic-score`

```clojure
(bic-score structure data)
```

Bayesian Information Criterion score for a BN structure.
   Catalog: D14. Pattern: REDUCE + MAP-OVER.

   {Node -> [Parent]} -> [{Node -> Val}] -> R

   Lower BIC = better model. BIC = -2*LL + k*log(n).

---

### `em-gmm`

```clojure
(em-gmm data k & {:keys [max-iter], :or {max-iter 100}})
```

EM algorithm for Gaussian Mixture Model.
   Catalog: D13. Pattern: ITERATE (comp m-step e-step).

   [R] -> Int -> Opts -> {:means [R], :variances [R], :weights [R]}

   Example:
     (em-gmm [0.0 1.0 5.0 6.0] 2 :max-iter 50)
     ;=> {:means [0.5 5.5] :variances [0.25 0.25] :weights [0.5 0.5]}

---

### `k2-search`

```clojure
(k2-search node-order data & {:keys [max-parents], :or {max-parents 3}})
```

K2 greedy structure learning for Bayesian networks.
   Catalog: D15. Pattern: ITERATE + DELEGATE + BRANCH.

   [Keyword] -> [{Keyword -> Val}] -> Opts -> {Keyword -> [Keyword]}

   Greedily adds parent that most improves BIC score.

   Example:
     (k2-search [:a :b :c] data :max-parents 2)
     ;=> {:a [] :b [:a] :c [:a :b]}

---

### `kde`

```clojure
(kde data & {:keys [bandwidth kernel], :or {bandwidth 1.0}})
```

Kernel Density Estimation — nonparametric density estimation.
   Catalog: D12. Pattern: PARAMETERIZE + REDUCE.

   [R] -> {:keys [bandwidth kernel]} -> (R -> R)

   Returns a density function. Default kernel: Gaussian.

   Example:
     (def f (kde [0.0 1.0 2.0] :bandwidth 0.5))
     (f 1.0) ;=> ~0.5

---

### `mle`

```clojure
(mle log-lik-fn data theta0 & {:keys [max-iter lr], :or {max-iter 500, lr 0.01}})
```

Maximum Likelihood Estimation via gradient ascent on log-likelihood.
   Catalog: D10. Pattern: ITERATE + REDUCE.

   (Theta -> Datum -> R) -> [Datum] -> Theta -> Opts -> {:x Theta, :value R}

   log-lik-fn: (theta, datum) -> log-likelihood contribution.
   Maximizes sum of log-likelihoods over data.

   Example:
     (mle (fn [theta x] (- (* (- x theta) (- x theta)))) data [0.0])
     ;=> {:x [3.0] ...}

---


## `orhof.mdp.approx`

Approximate value functions: nearest neighbor, kernel, linear, neural.
   Catalog: D25-D30. Pattern: MODEL BUILDER + ITERATE.

### `kernel-smoothing-vf`

```clojure
(kernel-smoothing-vf data & {:keys [bandwidth kernel-fn], :or {bandwidth 1.0}})
```

Kernel-smoothed value function approximation.
   Catalog: D26. Pattern: PARAMETERIZE + REDUCE.

   {State -> R} -> {:keys [bandwidth]} -> (State -> R)

   Returns a Nadaraya-Watson kernel regression estimator.

   Example:
     (def vf (kernel-smoothing-vf {[0] 0.0 [1] 1.0 [2] 4.0} :bandwidth 0.5))
     (vf [1.5]) ;=> ~2.5

---

### `linear-interpolation-vf`

```clojure
(linear-interpolation-vf grid-data)
```

Linear interpolation value function on a 1D grid.
   Catalog: D27. Pattern: PARAMETERIZE.

   [[R R]] -> (R -> R)

   grid-data: sorted vector of [x value] pairs.

   Example:
     (def vf (linear-interpolation-vf [[0.0 0.0] [1.0 2.0] [2.0 6.0]]))
     (vf 0.5) ;=> 1.0

---

### `linear-regression-vf`

```clojure
(linear-regression-vf feature-fn data)
```

Linear regression value function: V(s) = w^T phi(s).
   Catalog: D29. Pattern: PARAMETERIZE + REDUCE.

   (State -> [R]) -> [[State R]] -> (State -> R)

   feature-fn maps states to feature vectors.
   Fits weights via least squares.

   Example:
     (def vf (linear-regression-vf (fn [s] [(first s) (second s) 1.0])
                                    [[[0 0] 0.0] [[1 0] 2.0] [[0 1] 3.0]]))
     (vf [1 1]) ;=> ~5.0

---

### `nearest-neighbor-vf`

```clojure
(nearest-neighbor-vf data & {:keys [distance-fn k], :or {k 1}})
```

Nearest-neighbor value function approximation.
   Catalog: D25. Pattern: PARAMETERIZE + REDUCE.

   {State -> R} -> (State -> R)

   Returns a function that looks up the value of the closest
   state in the data.

   Example:
     (def vf (nearest-neighbor-vf {[0 0] 1.0 [1 1] 5.0}))
     (vf [0 0]) ;=> 1.0

---

### `neural-network-vf`

```clojure
(neural-network-vf layer-sizes data & {:keys [max-iter lr], :or {max-iter 200, lr 0.01}})
```

Simple feedforward neural network value function.
   Catalog: D30. Pattern: PARAMETERIZE + ITERATE.

   [Int] -> [[State R]] -> (State -> R)

   layer-sizes: [input hidden ... output] dimensions.
   Single hidden layer with tanh activation. Trained via GD.

   Example:
     (def vf (neural-network-vf [2 4 1] training-data :max-iter 500))

---


## `orhof.val.spec`

Temporal logic specifications and system simulation.
   Specifications ARE functions: trajectory -> robustness value.
   Temporal operators ARE higher-order functions on specifications.
   Pattern: FUNCTION TRANSFORMER.

### `always`

```clojure
(always spec)
```

Temporal operator [] (always/globally).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (always spec) checks that spec holds at every time step.
   Robustness: min over all time steps.

---

### `bounded-always`

```clojure
(bounded-always spec a b)
```

Bounded globally: [][a,b] phi.

---

### `bounded-eventually`

```clojure
(bounded-eventually spec a b)
```

Bounded eventually: <>[a,b] phi.

---

### `cross-validation`

```clojure
(cross-validation fit-fn eval-fn data & {:keys [k], :or {k 5}})
```

K-fold cross-validation for model assessment.
   Catalog: V4. Pattern: MAP-OVER + REDUCE.

   (Data -> Model) -> (Model -> Data -> R) -> Data -> {:keys [k]}
   -> {:scores [R], :mean R}

   fit-fn: training data -> model
   eval-fn: model -> test data -> score
   data: full dataset

   Example:
     (cross-validation fit-fn eval-fn data :k 5)
     ;=> {:scores [0.1 0.2 ...] :mean 0.15}

---

### `eventually`

```clojure
(eventually spec)
```

Temporal operator <> (eventually/finally).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (eventually spec) checks that spec holds at some time step.
   Robustness: max over all time steps.

---

### `make-predicate-spec`

```clojure
(make-predicate-spec predicate margin-fn)
```

Create a specification from a predicate on states.
   predicate: state -> boolean
   Returns a robustness function: state -> R (positive = satisfied).

---

### `make-system`

```clojure
(make-system dynamics initial-state)
```

Create a dynamical system.
   dynamics: (fn [state disturbance] -> state')
   initial-state: the starting state

---

### `reachability-spec`

```clojure
(reachability-spec safe-set)
```

Create a reachability specification: trajectory must stay in safe set.
   Catalog: V8. Pattern: PARAMETERIZE.

   {:lo R, :hi R} -> (Trajectory -> R)

   safe-set has :lo and :hi bounds. Robustness = min distance to boundary.

   Example:
     (def spec (reachability-spec {:lo -5.0 :hi 5.0}))
     (spec [0.0 1.0 2.0]) ;=> 3.0

---

### `rollout`

```clojure
(rollout system disturbances)
```

Simulate a system trajectory given a sequence of disturbances.
   Pattern: reduce over disturbances, collecting trajectory.
   Returns a vector of states.

---

### `spec-and`

```clojure
(spec-and spec1 spec2)
```

Conjunction: phi1 /\ phi2. Robustness: min.

---

### `spec-not`

```clojure
(spec-not spec)
```

Negation: -phi. Robustness: negated.

---

### `spec-or`

```clojure
(spec-or spec1 spec2)
```

Disjunction: phi1 \/ phi2. Robustness: max.

---

### `until-op`

```clojure
(until-op spec1 spec2)
```

Temporal operator U (until).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (until-op spec1 spec2): spec1 holds until spec2 becomes true.
   Robustness: max_j min(spec2(t_j), min_{i<j} spec1(t_i))

---


## `orhof.val.falsify`

Falsification methods: optimization-based, random, stress testing.
   Pattern: PIPELINE COMPOSER — Falsification = Optimizer . Robustness . Rollout.

### `adaptive-stress-test`

```clojure
(adaptive-stress-test system spec-fn dim horizon & {:keys [budget], :or {budget 500}})
```

Adaptive stress testing using random tree exploration.
   Catalog: V17-V20. Pattern: TREE/GRAPH EXPLORER.
   system: dynamical system, spec-fn: specification,
   dim: disturbance dim, horizon: time steps, budget: rollouts.

---

### `disturbance-falsification`

```clojure
(disturbance-falsification system spec-fn horizon dim & {:keys [sampler n-samples], :or {n-samples 1000}})
```

Disturbance-based falsification with custom sampler.
   Catalog: V10. Pattern: GENERATE + PARAMETERIZE.

   System -> Spec -> Int -> Int -> {:keys [sampler n-samples]}
   -> {:falsified? Bool, :robustness R, ...}

   sampler: () -> disturbance-vector (one step)

   Example:
     (disturbance-falsification sys spec 5 2
       :sampler (fn [] [0.1 0.2]) :n-samples 500)

---

### `falsify`

```clojure
(falsify system spec-fn optimizer horizon dim)
```

Falsification via optimization.
   Catalog: V12. Pattern: PIPELINE COMPOSER.
   Composes: optimizer(lambda d. -rho(spec, rollout(system, d)))

   system:    dynamical system
   spec-fn:   specification (trajectory -> robustness)
   optimizer: (fn [objective x0] -> result)
   horizon:   number of time steps
   dim:       disturbance dimension per step

   Returns {:falsified? bool, :disturbance, :robustness, :trajectory}

---

### `fuzzing`

```clojure
(fuzzing system spec-fn seed-disturbance dim & {:keys [mutate-fn max-iter], :or {max-iter 500}})
```

Mutation-based fuzzing for falsification.
   Catalog: V11. Pattern: ITERATE + DELEGATE.

   System -> Spec -> [R] -> Int -> {:keys [mutate-fn max-iter]}
   -> {:falsified? Bool, :robustness R, ...}

   Iteratively mutates the disturbance vector, keeping improvements.

   Example:
     (fuzzing sys spec [0 0 0] 1
       :mutate-fn (fn [d] (mapv #(+ % (* 0.1 (rand))) d))
       :max-iter 500)

---

### `random-falsification`

```clojure
(random-falsification system spec-fn horizon dim & {:keys [n-samples bounds], :or {n-samples 1000, bounds [-1.0 1.0]}})
```

Random falsification: sample random disturbances and check spec.
   Catalog: V9. Pattern: GENERATE + MAP-OVER.

---

### `rl-falsification`

```clojure
(rl-falsification system spec-fn horizon dim & {:keys [max-episodes max-steps epsilon alpha], :or {max-episodes 200, max-steps 10, epsilon 0.3, alpha 0.1}})
```

RL-based falsification — learn a policy to choose disturbances.
   Catalog: V20. Pattern: ITERATE + DELEGATE.

   System -> Spec -> Int -> Int -> Opts
   -> {:falsified? Bool, :robustness R, ...}

   Uses epsilon-greedy Q-learning where the 'state' is discretized
   system state and 'actions' are disturbance bins.

   Example:
     (rl-falsification sys spec 5 1 :max-episodes 200)

---

### `robustness-objective`

```clojure
(robustness-objective system spec-fn horizon dim)
```

Create a robustness-based objective for any optimizer.
   Catalog: V13. Pattern: COMPOSE.

   System -> Spec -> Int -> Int -> (Disturbance-flat -> R)

   Composes rollout + spec evaluation into a single objective function
   that any optimizer can minimize.

   Example:
     (def obj (robustness-objective sys spec 5 1))
     (obj [0.1 0.2 0.3 0.4 0.5]) ;=> 2.3 (robustness)

---

### `shooting-method`

```clojure
(shooting-method system spec-fn horizon dim & {:keys [n-samples bounds], :or {n-samples 200, bounds [-1.0 1.0]}})
```

Shooting method — evaluate random action/disturbance sequences.
   Catalog: V16. Pattern: GENERATE + MAP-OVER.

   System -> Spec -> Int -> Int -> {:keys [n-samples]}
   -> {:falsified? Bool, :robustness R, :best-sequence [...]}

   Example:
     (shooting-method sys spec 5 1 :n-samples 200)

---


## `orhof.val.sampling`

Sampling methods: rejection, MCMC, importance sampling.
   Pattern: SAMPLER CONSTRUCTOR — build a sampling function from a target.

### `cross-entropy-estimation`

```clojure
(cross-entropy-estimation system spec-fn horizon dim & {:keys [n-samples n-elite max-iter rho], :or {n-samples 500, n-elite 50, max-iter 10, rho 0.1}})
```

Cross-entropy method for rare event probability estimation.
   Catalog: V29. Pattern: ITERATE + REDUCE.
   Iteratively improves sampling distribution to focus on failure region.

---

### `direct-mc-estimation`

```clojure
(direct-mc-estimation sampler event? n)
```

Direct Monte Carlo probability estimation.
   Catalog: V26. Pattern: MAP-OVER + REDUCE.

   (() -> X) -> (X -> Bool) -> Int -> {:probability R}

   Example:
     (direct-mc-estimation sample-fn event? 10000)
     ;=> {:probability 0.05}

---

### `gibbs-sampling`

```clojure
(gibbs-sampling conditionals init-state n & {:keys [burn-in], :or {burn-in 100}})
```

Gibbs sampling with custom conditional distributions.
   Catalog: V23/D8. Pattern: ITERATE + DELEGATE.

   {Keyword -> (State -> Val)} -> State -> Int -> {:keys [burn-in]} -> [State]

   conditionals: map of variable -> (fn [current-state] -> new-value)
   Each step resamples every variable from its conditional.

   Example:
     (gibbs-sampling {:x (fn [s] (sample-x-given-y (:y s)))
                      :y (fn [s] (sample-y-given-x (:x s)))}
                     {:x 0.0 :y 0.0} 1000)

---

### `hmc`

```clojure
(hmc log-prob grad-log-prob x0 & {:keys [n-samples step-size n-leapfrog burn-in], :or {n-samples 500, step-size 0.1, n-leapfrog 10, burn-in 50}})
```

Hamiltonian Monte Carlo sampler.
   Catalog: V24. Pattern: ITERATE + COMPOSE.

   (X -> R) -> (X -> X) -> X -> Opts -> [X]

   log-prob: x -> log p(x)
   grad-log-prob: x -> gradient of log p(x)
   Uses leapfrog integrator for Hamiltonian dynamics.

   Example:
     (hmc (fn [x] (- (* 0.5 (first x) (first x))))
          (fn [x] [(- (first x))])
          [0.0] :n-samples 500)

---

### `importance-sampling`

```clojure
(importance-sampling target-pdf proposal-sampler proposal-pdf indicator n)
```

Importance sampling for probability estimation.
   Catalog: V27. Pattern: MAP-OVER + REDUCE.
   target-pdf: x -> p(x)
   proposal-sampler: () -> x
   proposal-pdf: x -> q(x)
   indicator: x -> boolean (event of interest)
   n: number of samples

---

### `metropolis-hastings`

```clojure
(metropolis-hastings target-pdf proposal x0 & {:keys [burn-in thin], :or {burn-in 100, thin 1}})
```

Metropolis-Hastings MCMC sampler.
   Catalog: V22. Pattern: ITERATE.
   target-pdf: x -> p(x) (unnormalized)
   proposal: (fn [x] -> x') — symmetric proposal
   Returns a sampler function that produces n samples.

---

### `rejection-sampler`

```clojure
(rejection-sampler target-pdf proposal-sampler proposal-pdf M)
```

Rejection sampling. Returns a sampler function.
   Catalog: V21. Pattern: SAMPLER CONSTRUCTOR.
   target-pdf: x -> p(x) (unnormalized)
   proposal-sampler: () -> x
   proposal-pdf: x -> q(x)
   M: bound such that p(x) <= M * q(x)

---

### `sequential-mc`

```clojure
(sequential-mc dynamics-fn obs-likelihood particles observations)
```

Sequential Monte Carlo (particle filter for general targets).
   Catalog: V30. Pattern: ITERATE + MAP-OVER + REDUCE.

   (X -> X) -> (X -> Obs -> R) -> [X] -> [Obs] -> [X]

   Propagates particles through dynamics, reweights by observations.

   Example:
     (sequential-mc dynamics-fn likelihood-fn initial-particles observations)

---


## `orhof.val.reach`

Reachability analysis: interval arithmetic, set propagation.
   Pattern: SET PROPAGATOR — propagate sets through dynamics.

### `bfs-reachability`

```clojure
(bfs-reachability start expand-fn goal-set)
```

Breadth-first search reachability on a discrete graph.
   Catalog: V48. Pattern: ITERATE + DELEGATE.

   State -> (State -> [State]) -> #{State}
   -> {:reached? Bool, :visited #{State}, :path [State]}

   Example:
     (bfs-reachability :a (fn [n] [:b :c]) #{:c})
     ;=> {:reached? true :visited #{:a :b :c}}

---

### `ellipsoid-propagation`

```clojure
(ellipsoid-propagation A e0 horizon)
```

Forward reachability using ellipsoid set representation.
   Catalog: V36. Pattern: SET PROPAGATOR + ITERATE.

   [[R]] -> Ellipsoid -> Int -> [Ellipsoid]

   For linear system x' = Ax: center' = A*c, shape' = A*P*A^T.

   Example:
     (ellipsoid-propagation A e0 5)

---

### `interval-contains?`

```clojure
(interval-contains? iset point)
```

Check if a point is inside an interval set.

---

### `interval-set`

```clojure
(interval-set lo hi)
```

Represent a set as an axis-aligned box (interval in each dimension).

---

### `interval-union`

```clojure
(interval-union a b)
```

Compute the bounding box of two interval sets.

---

### `linear-reachability`

```clojure
(linear-reachability A B w-set u-set initial-set horizon)
```

Forward reachability for a linear system x' = Ax + Bu + w.
   Catalog: V33-V36. Pattern: SET PROPAGATOR (iterate + propagate-sets).
   A: state matrix, B: input matrix,
   w-set/u-set/initial-set: interval sets, horizon: steps.
   Uses interval arithmetic for overapproximation.

---

### `make-ellipsoid`

```clojure
(make-ellipsoid center shape-matrix)
```

Create an ellipsoid: {x : (x-c)^T P^{-1} (x-c) <= 1}.
   Catalog: V36. Pattern: PARAMETERIZE.

   [R] -> [[R]] -> {:center [R], :shape [[R]]}

   Example:
     (make-ellipsoid [0 0] [[1 0] [0 1]])

---

### `make-zonotope`

```clojure
(make-zonotope center generators)
```

Create a zonotope: center + generator matrix.
   Catalog: V35. Pattern: PARAMETERIZE.

   [R] -> [[R]] -> {:center [R], :generators [[R]]}

   A zonotope Z = {c + sum_i a_i * g_i : a_i in [-1, 1]}

   Example:
     (make-zonotope [0 0] [[1 0] [0 1]])
     ;=> {:center [0 0] :generators [[1 0] [0 1]]}

---

### `probabilistic-reachability`

```clojure
(probabilistic-reachability transition-map initial horizon)
```

Probabilistic reachability — propagate probability distributions.
   Catalog: V51. Pattern: ITERATE + MAP-OVER + REDUCE.

   {State -> {State -> Prob}} -> {State -> Prob} -> Int -> [{State -> Prob}]

   transition-map: {from -> {to -> prob}}
   initial: {state -> probability}

   Example:
     (probabilistic-reachability {1 {2 0.8 1 0.2} 2 {3 1.0}} {1 1.0} 3)

---

### `zonotope-propagation`

```clojure
(zonotope-propagation A z0 horizon)
```

Forward reachability using zonotope set representation.
   Catalog: V35. Pattern: SET PROPAGATOR + ITERATE.

   [[R]] -> Zonotope -> Int -> [Zonotope]

   For linear system x' = Ax: center' = A*center, generators' = A*generators.

   Example:
     (zonotope-propagation A z0 5) ;=> [z0 z1 z2 z3 z4 z5]

---


## `orhof.val.explain`

Explainability and monitoring: sensitivity analysis, Shapley values,
   runtime monitoring.
   Pattern: SENSITIVITY ANALYZER + FUNCTION TRANSFORMER.

### `conformal-predictor`

```clojure
(conformal-predictor model calibration-data & {:keys [alpha], :or {alpha 0.1}})
```

Create a conformal prediction set.
   Catalog: V60. Pattern: PARAMETERIZE + REDUCE.

   (X -> R) -> [{:x X :y R}] -> {:keys [alpha]} -> (X -> {:lo R :hi R})

   Calibrates nonconformity scores on calibration data, then
   returns prediction intervals at confidence level 1-alpha.

   Example:
     (def pred (conformal-predictor model cal-data :alpha 0.1))
     (pred 5.0) ;=> {:lo 4.8 :hi 5.2}

---

### `counterfactual`

```clojure
(counterfactual model x0 desired-class & {:keys [max-iter lr lambda], :or {max-iter 200, lr 0.1, lambda 10.0}})
```

Find a counterfactual explanation: closest input with desired output.
   Catalog: V57. Pattern: TRANSFORM-PROBLEM + ITERATE.

   (X -> Class) -> X -> Class -> Opts -> {:x X}

   Minimizes distance to original while achieving desired class.

   Example:
     (counterfactual classifier [-1.0] :positive :max-iter 100)
     ;=> {:x [0.01]}

---

### `ensemble-disagreement`

```clojure
(ensemble-disagreement models x)
```

Measure uncertainty via ensemble model disagreement.
   Catalog: V61. Pattern: MAP-OVER + REDUCE.

   [(X -> R)] -> X -> {:mean R, :variance R}

   Example:
     (ensemble-disagreement [model1 model2 model3] [1.0 2.0])
     ;=> {:mean 5.0 :variance 0.5}

---

### `make-monitor`

```clojure
(make-monitor spec-fn window-size)
```

Create a stateful runtime monitor from a specification.
   Returns (fn [observation] -> {:verdict :safe/:unsafe/:unknown, :robustness R}).
   Uses internal state to buffer observations.

---

### `monitor-step`

```clojure
(monitor-step spec-fn window-size)
```

Pure monitor step function: (state, observation) -> state'.
   Catalog: V63. Pattern: FUNCTION TRANSFORMER.
   State is {:buffer [...] :verdict :safe/:unsafe/:unknown}.

---

### `permutation-importance`

```clojure
(permutation-importance model data labels & {:keys [n-repeats], :or {n-repeats 5}})
```

Permutation feature importance.
   Catalog: V54. Pattern: MAP-OVER + REDUCE.

   (X -> R) -> [X] -> [R] -> [R]

   For each feature, shuffles it across the dataset and measures
   the increase in prediction error.

   Example:
     (permutation-importance model data labels)
     ;=> [0.1 0.5] (feature 1 more important)

---

### `robustness-sensitivity`

```clojure
(robustness-sensitivity system spec-fn disturbance dim & {:keys [h], :or {h 0.01}})
```

Compute sensitivity of robustness to each disturbance dimension.
   Catalog: V53-V55. Pattern: SENSITIVITY ANALYZER.
   Uses finite differences on the robustness function.

---

### `shapley-values`

```clojure
(shapley-values system spec-fn disturbance dim & {:keys [n-samples], :or {n-samples 100}})
```

Approximate Shapley values for failure attribution.
   Catalog: V55. Pattern: SENSITIVITY ANALYZER + MAP-OVER + REDUCE.
   Estimates how much each disturbance dimension contributes to failure.

---


## `orhof.search`

Pattern 7, the Tree/Graph Explorer, as composable higher-order functions:
   unfold a search space and fold it in the same pass. The space is never built.

     search      top-down: a reducing function folds the nodes in strategy
                 order, and what it has folded so far steers the unfold
                 (pruning, bounds, early exit)
     explore     the unfold alone: the nodes as a reducible, seqable collection
     hylo        bottom-up: the value of a node is a fold of its children's values
     hylo-eager  the same on an explicit stack, for trees too deep for the JVM stack
     converge    the one-child case: the stopping rule `iterate` lacks

   A strategy decides which pending node comes next. Like a reducing function
   it is one function with three arities, and its frontier is plain data:

     (strategy)                 -> an empty frontier
     (strategy frontier)        -> [node frontier'], or nil when nothing is pending
     (strategy frontier nodes)  -> the frontier with the sibling `nodes` added

   depth-first and breadth-first are strategies, (best-first priority) returns
   one, unique-by and unique wrap one for graphs. Everything composes with
   what Clojure already has: successor functions with mapcat, strategy
   transformers with comp, the fold with transducers, hylo with comp and juxt.

   This namespace needs nothing but clojure.core.

### `best-first`

```clojure
(best-first priority)
```

Returns a strategy that takes the node with the lowest (priority node)
   first; ties leave in insertion order. Priorities are compared with
   `compare`, so numbers and vectors of numbers both work.

   (Node -> Priority) -> Strategy

   The frontier is a sorted map from priority to a queue of nodes.

   Example:
     (into [] (explore (best-first count) {} "ccc" "a" "bb")) ;=> ["a" "bb" "ccc"]

---

### `breadth-first`

```clojure
(breadth-first )
(breadth-first queue)
(breadth-first queue nodes)
```

Strategy: the oldest nodes first, level by level. The frontier is a
   PersistentQueue.

   Example:
     (into [] (explore breadth-first {:a [:b :c] :b [:d]} :a)) ;=> [:a :b :c :d]

---

### `by-identity`

```clojure
(by-identity )
```

Returns a key function for unique-by and for the :memo option of hylo and
   hylo-eager that tells nodes apart by identity instead of value: the same
   object always gets the same key, equal but distinct objects get different
   keys. Each key is a small number, so a lookup costs the same however large
   the node is.

   () -> (Node -> Int)

   Use it when nodes are big immutable structures that share sub-structure,
   such as expression graphs. The function remembers every node it has seen;
   let it go with the search it was made for.

   Example:
     (let [id (by-identity), e [:+ :x :y]]
       [(= (id e) (id e)) (= (id e) (id [:+ :x :y]))]) ;=> [true false]

---

### `converge`

```clojure
(converge close? xs)
```

The first x in xs for which (close? previous x) holds; the last x if xs
   ends first; nil if xs is empty. The stopping rule `iterate` lacks.

   (a -> a -> Bool) -> [a] -> a

   Example:
     (converge = (iterate #(Math/floor (/ % 2)) 100.0)) ;=> 0.0
     (converge #(< (abs (- %1 %2)) 1e-12) (iterate #(* 0.5 (+ % (/ 2.0 %))) 1.0)) ;=> 1.414213562373095

---

### `depth-first`

```clojure
(depth-first )
(depth-first frames)
(depth-first frames nodes)
```

Strategy: the newest siblings first. Pre-order, like tree-seq.

   The frontier is a list of sibling seqs, so siblings keep their order and a
   lazy successor seq is only realised as far as the search backtracks into it.

   Example:
     (into [] (explore depth-first {:a [:b :c] :b [:d]} :a)) ;=> [:a :b :d :c]

---

### `explore`

```clojure
(explore strategy expand & roots)
```

The nodes reachable from `roots`, in strategy order, as a collection that
   is both reducible and seqable. Nothing is expanded until it is consumed, so
   the space may be infinite.

   Strategy -> (Node -> [Node]) -> Node... -> Reducible Node

   (expand node) -> the child nodes; nil or empty prunes.

   It is clojure.core/iteration whose continuation token is the frontier, so
   it works with reduce, transduce, into, sequence, eduction and the seq
   functions.

   Example:
     (take 5 (explore breadth-first (fn [n] [(* 2 n) (inc (* 2 n))]) 1)) ;=> (1 2 3 4 5)

---

### `hylo`

```clojure
(hylo expand fold & {:keys [memo]})
```

Returns the function node -> value defined by an unfold and a fold
   (a hylomorphism):

     (expand node)             -> the child nodes
     (fold node child-values)  -> the value of the node

   (Node -> [Node]) -> (Node -> [Value] -> Value) -> (Node -> Value)

   child-values is lazy: a child whose value `fold` never reads is never
   expanded, which is how alpha-beta and branch and bound prune.

   Options:
     :memo key-fn   remember the value of every (key-fn node), like memoize.
                    Equal keys are solved once: dynamic programming on a
                    graph without cycles. Keep keys small, or use (by-identity).

   The recursion uses the JVM stack: about 1,500 levels on the default 1 MB
   thread stack. For deeper spaces use hylo-eager.

   Example:
     ;; paths from corner to corner of a 16x16 grid
     ((hylo (fn [[x y]] (cond-> [] (< x 16) (conj [(inc x) y]) (< y 16) (conj [x (inc y)])))
            (fn [_ counts] (if (seq counts) (reduce + counts) 1N))
            :memo identity)
      [0 0])
     ;=> 601080390N

---

### `hylo-eager`

```clojure
(hylo-eager expand fold & {:keys [memo]})
```

Like hylo, on an explicit stack: depth is limited by memory, not by the JVM
   stack. The price is that every child is evaluated before its parent, so
   `fold` gets all the child values (a vector) and cannot prune by not
   reading them.

   (Node -> [Node]) -> (Node -> [Value] -> Value) -> (Node -> Value)

   Whenever `fold` reads every child value, hylo and hylo-eager return the
   same result. Options:
     :memo key-fn   as in hylo. With :memo a cycle in the space throws
                    instead of looping forever.

   Example:
     ;; the length of a chain a million nodes long
     ((hylo-eager (fn [n] (when (pos? n) [(dec n)]))
                  (fn [_ lengths] (if (seq lengths) (inc (first lengths)) 0)))
      1000000)
     ;=> 1000000

---

### `search`

```clojure
(search strategy rf init expand & roots)
```

Folds the space reachable from `roots` while unfolding it.

   Strategy -> (Acc -> Node -> Acc) -> Acc -> (Acc -> Node -> [Node]) -> Node... -> Acc

   (rf acc node)     -> the new acc, or (reduced acc) to stop the search
   (expand acc node) -> the child nodes. It sees what the fold knows so far,
                        so it can prune: nil cuts the branch below `node`.

   Runs in a loop, so depth is limited by memory, not by the stack.

   Example:
     ;; branch and bound: the largest sum of a subset of xs that is <= 10
     (let [xs [7 5 4 3]]
       (search depth-first
               (fn [best [_ sum]] (max best sum))
               0
               (fn [best [i sum]]                       ; prune what cannot beat `best`
                 (when (and (< i (count xs)) (> (reduce + sum (subvec xs i)) best))
                   (cond-> [[(inc i) sum]]
                     (<= (+ sum (xs i)) 10) (conj [(inc i) (+ sum (xs i))]))))
               [0 0]))
     ;=> 10

---

### `unique`

```clojure
(unique strategy)
```

Strategy transformer: each node is taken at most once. (unique-by identity strategy).

   Strategy -> Strategy

   Example:
     (into [] (explore (unique depth-first) {:a [:b] :b [:a]} :a)) ;=> [:a :b]

---

### `unique-by`

```clojure
(unique-by key strategy)
```

Strategy transformer for graphs: each (key node) is taken at most once,
   so a search over a graph with cycles terminates.

   (Node -> Key) -> Strategy -> Strategy

   The frontier becomes [seen inner-frontier]. Keys are compared by value, so
   keep them small (ids, coordinates): a lookup that lands on another key
   walks both keys, which is slow for deep nested data. For nodes that are
   large immutable structures use (by-identity).

   Example:
     (def graph {:a [:b :c] :b [:d] :c [:d] :d [:a]})
     (into [] (explore (unique-by identity breadth-first) graph :a)) ;=> [:a :b :c :d]

---


## `orhof.search.graph`

Graph search on orhof.search: shortest paths and reachability.
   Pattern: TREE/GRAPH EXPLORER — explore with a strategy; unique-by makes
   the tree a graph.

### `reachable`

```clojure
(reachable successors & roots)
```

The set of nodes that can be reached from `roots`.
   Catalog: V48. Pattern: TREE/GRAPH EXPLORER (explore + breadth-first + unique).

   (Node -> [Node]) -> Node... -> #{Node}

   Example:
     (reachable {:a [:b] :b [:a :c] :d [:a]} :a) ;=> #{:a :b :c}

---

### `shortest-path`

```clojure
(shortest-path neighbors start goal? & {:keys [heuristic key], :or {heuristic (constantly 0), key identity}})
```

Cheapest path from `start` to a node satisfying `goal?`: Dijkstra's
   algorithm, or A* when given a heuristic.
   Catalog: D37. Pattern: TREE/GRAPH EXPLORER (explore + best-first + unique-by).

   (Node -> {Node Cost}) -> Node -> (Node -> Bool) -> {:node Node, :cost R, :path [Node]}

   neighbors: node -> {node' cost} or [[node' cost] ...], costs >= 0
   Options:
     :heuristic  node -> an estimate of the remaining cost that never
                 overestimates and is consistent (default: 0, Dijkstra)
     :key        node -> what makes two nodes the same state (default: identity)

   Returns nil when no goal can be reached. The graph may be infinite.

   Example:
     (shortest-path {:a {:b 7 :c 9} :b {:d 15} :c {:d 11} :d {}} :a #{:d})
     ;=> {:node :d :cost 20 :path [:a :c :d]}

---


## `orhof.search.games`

Game-tree search on orhof.search: minimax, alpha-beta, expectiminimax and
   Monte Carlo tree search.
   Pattern: TREE/GRAPH EXPLORER — the minimax family is hylo; MCTS is iterate
   over two explore walks and a reduce.

   A game is a map of functions, its rules:
     :game-over?   state -> boolean
     :legal-moves  state -> [move]
     :apply-move   state, move -> state
     :evaluate     state -> score for the maximising player    (minimax family)
     :kind         state -> :max, :min or :chance              (expectiminimax)
     :outcomes     state -> {state' probability}               (expectiminimax)
     :to-move      state -> player                             (mcts; default: one player)
     :reward       final state, player -> number               (mcts)

### `alpha-beta`

```clojure
(alpha-beta rules state depth alpha beta maximizing?)
```

Minimax value of `state` with alpha-beta pruning: the same value as
   minimax from a fraction of the nodes.
   Pattern: TREE/GRAPH EXPLORER (hylo). The value of a node is a function of
   the window, so a child is searched only when its parent calls it, with the
   window narrowed so far; `reduced` is the cutoff.

   Rules -> State -> Int -> R -> R -> Bool -> R

   Example:
     (alpha-beta nim [4 1] 10 -1 1 true) ;=> 1     ; nim as under minimax

---

### `expectiminimax`

```clojure
(expectiminimax rules state depth)
```

Minimax with chance nodes: the value of a chance node is the expectation
   over its outcomes.
   Pattern: TREE/GRAPH EXPLORER (hylo). The fold is max, min or a weighted sum.

   Rules -> State -> Int -> R

   The rules also give (:kind state) -> :max, :min or :chance and, at a chance
   node, (:outcomes state) -> {state' probability}.

   Example:
     ;; a coin flip between a sure 1 and a choice of 4 or 0
     (def flip {:kind :chance
                :outcomes {{:value 1} 0.5
                           {:kind :max :moves [{:value 4} {:value 0}]} 0.5}})
     (expectiminimax {:game-over? :value :evaluate :value :kind :kind :outcomes :outcomes
                      :legal-moves :moves :apply-move (fn [_ child] child)}
                     flip 5)
     ;=> 2.5

---

### `mcts`

```clojure
(mcts rules state & {:keys [budget c-explore evaluate prior]})
```

Monte Carlo tree search: run `budget` simulations from `state` and play
   the most visited move.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER + GENERATE.

   Rules -> State -> {:keys [budget c-explore evaluate prior]}
   -> {:move Move, :visits Int, :value R}

   The rules need :legal-moves, :apply-move, :game-over? and :reward, plus
   :to-move when there is more than one player. :value is the mean reward of
   the chosen move for the player to move. Returns nil if the game is over.

   Options:
     :budget     number of simulations (default 1000)
     :c-explore  exploration constant (default sqrt 2)
     :evaluate   (fn [state player] -> reward) used instead of random playouts
     :prior      (fn [state] -> (fn [move] -> probability)); switches the
                 selection rule from UCB1 to PUCT, as in AlphaZero

   Example:
     ;; nim as under minimax, plus who moves and who won
     (def nim' (assoc nim
                      :to-move (fn [[_ player]] player)
                      :reward  (fn [[_ player] p] (if (= p (- player)) 1.0 0.0))))
     (:move (mcts nim' [4 1] :budget 500)) ;=> 1

---

### `mcts-statistics`

```clojure
(mcts-statistics rules state & {:keys [budget c-explore evaluate prior]})
```

The statistics of Monte Carlo tree search after `budget` simulations from
   `state`: {state {:n visits, :w total reward}}, the reward being that of
   the player who moved into the state.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER (iterate over one simulation).

   Rules -> State -> {:keys [budget c-explore evaluate prior]} -> {State {:n Int, :w R}}

   Options as for mcts.

   Example:
     (:n ((mcts-statistics nim' [4 1] :budget 200) [4 1])) ;=> 200     ; nim' as under mcts

---

### `minimax`

```clojure
(minimax rules state depth maximizing?)
```

Minimax value of `state`, searching `depth` plies.
   Pattern: TREE/GRAPH EXPLORER (hylo). The fold is max or min.

   Rules -> State -> Int -> Bool -> R

   Example:
     ;; nim: take 1 or 2 sticks; whoever takes the last one wins.
     ;; A state is [sticks player-to-move], the players are 1 and -1.
     (def nim {:game-over?  (fn [[sticks _]] (zero? sticks))
               :legal-moves (fn [[sticks _]] (filter #(<= % sticks) [1 2]))
               :apply-move  (fn [[sticks player] n] [(- sticks n) (- player)])
               :evaluate    (fn [[_ player]] (- player))})     ; the one who just moved won
     (minimax nim [4 1] 10 true) ;=> 1
     (minimax nim [3 1] 10 true) ;=> -1

---


## `orhof.search.mdp`

Online planning on orhof.search: forward search, branch and bound, sparse
   sampling, and planning in belief space for POMDPs.
   Pattern: TREE/GRAPH EXPLORER — each is a fold of the lookahead tree, in
   which decision nodes (pick an action) alternate with chance nodes (nature
   picks an outcome). Forward search and sparse sampling read every child, so
   they run on hylo-eager and no depth is too deep; branch and bound skips
   children, so it runs on hylo.

   MDPs are the maps of orhof.mdp.planning/make-mdp. Each function returns
   {:action a, :value v}: the best action at the state and its value.

### `belief-mdp`

```clojure
(belief-mdp pomdp)
```

The MDP whose states are the beliefs of a POMDP, so that every MDP
   algorithm plans under partial observability unchanged. A belief is a map
   {state probability}.
   Catalog: D71 (the same problem alpha vectors solve). Pattern: TRANSFORM-PROBLEM.

   POMDP -> MDP

   The POMDP is a map:
     :actions      state -> [action]            (the same for every state of a belief)
     :transition   state, action -> {state' probability}
     :observation  action, state' -> {observation probability}
     :reward       state, action -> reward
     :gamma        discount factor

   A step of the belief MDP is one Bayes update per possible observation.

   Example:
     ;; tiger: a tiger behind one of two doors; listening is right 85% of the time
     (:action (forward-search (belief-mdp tiger) {:left 0.5 :right 0.5} :depth 4))  ;=> :listen
     (:action (forward-search (belief-mdp tiger) {:left 0.99 :right 0.01} :depth 4)) ;=> :open-right

---

### `branch-and-bound`

```clojure
(branch-and-bound mdp state lower upper & {:keys [depth]})
```

Forward search that tries the actions of a state in order of their upper
   bound and stops at the first one that cannot beat the best found so far.
   The same answer as forward-search with :leaf lower, from fewer evaluations.
   Pattern: TREE/GRAPH EXPLORER (hylo; the unread children are never expanded).

   MDP -> State -> (State -> R) -> (State -> Action -> R) -> {:keys [depth]}
   -> {:action Action, :value R}

   (lower s)    <= the value of s; used at the horizon
   (upper s a)  >= the value of taking a in s

   Example:
     ;; mdp as under forward-search: no state is worth less than -10 or more than 0
     (branch-and-bound mdp [0 0] (fn [s] (if (= s [3 3]) 0.0 -10.0)) (constantly 0.0) :depth 6)
     ;=> {:action :down :value -4.68559}

---

### `forward-search`

```clojure
(forward-search mdp state & {:keys [depth leaf]})
```

Exhaustive lookahead to a fixed depth, solving equal states at equal depth once.
   Catalog: D33. Pattern: TREE/GRAPH EXPLORER (hylo-eager with :memo).

   MDP -> State -> {:keys [depth leaf]} -> {:action Action, :value R}

   Options:
     :depth  number of steps to look ahead (default 3)
     :leaf   state -> estimate of its value, used at the horizon (default 0).
             With the converged values of value iteration the result is exact
             at any depth.

   A state without actions is a leaf.

   Example:
     ;; mdp: a 4x4 grid, -1 a step until the corner [3 3], gamma 0.9
     (forward-search mdp [0 0] :depth 4)
     ;=> {:action :right :value -3.439}

---

### `reachable-states`

```clojure
(reachable-states mdp state)
```

Every state the MDP can reach from `state`: the :states that planning from
   there needs.
   Pattern: TREE/GRAPH EXPLORER (explore + unique).

   MDP -> State -> #{State}

   Example:
     (plan/value-iteration (assoc mdp :states (reachable-states mdp start)))

---

### `sparse-sampling`

```clojure
(sparse-sampling mdp state & {:keys [depth n-samples leaf]})
```

Forward search on a fixed number of sampled outcomes per action instead of
   all of them, so the cost does not depend on the number of states.
   Catalog: D35. Pattern: TREE/GRAPH EXPLORER + GENERATE (hylo-eager over a sampled tree).

   MDP -> State -> {:keys [depth n-samples leaf]} -> {:action Action, :value R}

   Options:
     :depth      number of steps to look ahead (default 3)
     :n-samples  sampled outcomes per action (default 10)
     :leaf       state -> estimate of its value at the horizon (default 0)

   Example:
     (sparse-sampling mdp [0 0] :depth 2 :n-samples 20)     ; mdp as under forward-search
     ;=> {:action :right :value -1.9}

---


## `orhof.search.val`

Falsification by tree search on orhof.search: the most likely way for a
   system to fail under a finite set of disturbances.
   Pattern: TREE/GRAPH EXPLORER — adaptive stress testing is Monte Carlo tree
   search over disturbance sequences; the exact answer is a shortest path.

   Systems are the maps of orhof.val.spec/make-system. The disturbance model
   is a map {disturbance probability}. A margin is a function
   state -> robustness; a negative margin is a violation.

   Both functions return
     {:falsified? Bool, :disturbances [d], :trajectory [state],
      :robustness R, :log-likelihood R}
   where :robustness is the lowest margin on the trajectory.

### `adaptive-stress-test`

```clojure
(adaptive-stress-test system disturbances margin horizon & {:keys [budget c-explore miss-penalty]})
```

Adaptive stress testing: searches for the most likely disturbance sequence
   that drives the system to a violation within `horizon` steps. Monte Carlo
   tree search picks each disturbance in turn.
   Catalog: V17-V20. Pattern: TREE/GRAPH EXPLORER (mcts inside iterate).

   System -> {Disturbance Prob} -> (State -> R) -> Int
   -> {:keys [budget c-explore miss-penalty]}
   -> {:falsified? Bool, :disturbances [d], :trajectory [State], :robustness R, :log-likelihood R}

   The reward of a trajectory is its log-likelihood; one that does not fail
   pays (miss-penalty robustness) on top, so near misses rank above safe runs.

   Options:
     :budget        simulations per step (default 800)
     :c-explore     exploration constant; keep it on the scale of the miss
                    penalty (default 40.0)
     :miss-penalty  robustness -> penalty (default 50 + 10 * robustness)

   The search is stochastic: it finds a failure, not always the most likely
   one. most-likely-failure gives the exact answer when it is affordable.

   Example:
     ;; a heater that switches on below 20 degrees; fail outside [18, 25]
     (def heater (orhof.val.spec/make-system
                  (fn [temp d] (+ temp (if (< temp 20.0) 2.0 0.0) d (* -0.05 (- temp 20.0))))
                  20.0))
     (def weather {-1.0 0.05, 0.0 0.9, 1.0 0.05})         ; cold snap, calm, heat wave
     (defn margin [temp] (min (- temp 18.0) (- 25.0 temp)))
     (:falsified? (adaptive-stress-test heater weather margin 8)) ;=> true

---

### `most-likely-failure`

```clojure
(most-likely-failure system disturbances margin horizon)
```

The most likely disturbance sequence that drives the system to a violation
   within `horizon` steps, exactly: a shortest path over disturbance
   sequences in which a step costs -log p. States reached by different
   sequences of the same length are searched once.
   Catalog: V17-V20 (the exact counterpart). Pattern: TREE/GRAPH EXPLORER
   (explore + best-first + unique-by).

   System -> {Disturbance Prob} -> (State -> R) -> Int
   -> {:falsified? Bool, :disturbances [d], :trajectory [State], :robustness R, :log-likelihood R}

   Returns {:falsified? false} when no sequence fails.

   Example:
     ;; heater, weather and margin as under adaptive-stress-test
     (:disturbances (most-likely-failure heater weather margin 8))
     ;=> [-1.0 1.0 1.0 1.0 1.0 1.0]     ; a cold snap switches the heater on, five heat waves do the rest

---


## `orhof.search.opt`

Global and discrete optimization on orhof.search: Lipschitz branch and
   bound, DIRECT, the simplex method and integer programming.
   Pattern: TREE/GRAPH EXPLORER — each divides a region and keeps the best
   point found; `search` folds the pieces while a strategy picks the next one
   to divide. The simplex method is the one-child case: iterate + converge.

   Everything minimizes, like the rest of orhof.

### `direct`

```clojure
(direct f bounds & {:keys [eps max-evals], :or {eps 1.0E-4, max-evals 500}})
```

DIRECT: global minimum of f over a box, without derivatives and without a
   Lipschitz constant. It divides the rectangles that could contain the
   minimum for some rate of change of f (Jones, Perttunen and Stuckman, 1993).
   Catalog: O37. Pattern: TREE/GRAPH EXPLORER (search + its own strategy).

   ([R] -> R) -> [[R] [R]] -> {:keys [eps max-evals]} -> {:x [R], :value R, :evals Int}

   bounds: [lower-bounds upper-bounds]
   Options:
     :max-evals  budget of function evaluations; the search finishes the
                 division in progress, so it may overshoot by a few (default 500)
     :eps        how much better than the best value a rectangle must promise
                 to be, relative to that value (default 1e-4)

   Example:
     (direct (fn [[x y]] (+ (* x x) (* y y))) [[-3.0 -2.0] [5.0 4.0]] :max-evals 200)
     ;=> {:x [~0.001 ~0.001] :value ~4e-6 :evals 201}

---

### `integer-program`

```clojure
(integer-program c A b)
```

Minimizes c . x subject to A x <= b, x >= 0 and x integer, by branch and
   bound: the bound of a node is its linear relaxation, and a fractional x_j
   splits it into x_j <= floor and x_j >= floor + 1.
   Catalog: O72. Pattern: TREE/GRAPH EXPLORER (search + best-first).

   [Int] -> [[Int]] -> [Int] -> {:status :optimal, :x [Int], :value Int}
                              | {:status :infeasible} | {:status :unbounded}

   Give integers or ratios, not doubles: the test for an integral x is exact.
   The feasible region must be bounded.

   Example:
     ;; a knapsack: maximize 5x + 4y  subject to  6x + 4y <= 24, x + 2y <= 6
     (integer-program [-5 -4] [[6 4] [1 2]] [24 6])
     ;=> {:status :optimal :x [4 0] :value -20}

---

### `linear-program`

```clojure
(linear-program c A b)
```

Minimizes c . x subject to A x <= b, x >= 0, by the two-phase tableau
   simplex method with Bland's rule.
   Catalog: O54. Pattern: ITERATE (converge over iterate: a vertex is a fixed
   point of the pivot step when it is optimal).

   [R] -> [[R]] -> [R] -> {:status :optimal, :x [R], :value R}
                        | {:status :infeasible} | {:status :unbounded}

   b may be negative, so `>=` rows (negate them) and `=` rows (a pair) can be
   stated. On integers and ratios the arithmetic is exact: the result is a
   vertex, not an approximation of one. Meant for small problems.

   Example:
     ;; minimize -3x - 5y  subject to  x <= 4, 2y <= 12, 3x + 2y <= 18
     (linear-program [-3 -5] [[1 0] [0 2] [3 2]] [4 12 18])
     ;=> {:status :optimal :x [2 6] :value -36}

---

### `lipschitz-min`

```clojure
(lipschitz-min f a b L & {:keys [tol], :or {tol 1.0E-6}})
```

Global minimum of f on [a, b] to within tol, given a Lipschitz constant L
   (|f(x) - f(y)| <= L |x - y|).
   Pattern: TREE/GRAPH EXPLORER (search + best-first). An interval is a node;
   its lower bound is f(midpoint) - L * half-width. An interval whose bound
   cannot beat the best point found is not divided.

   (R -> R) -> R -> R -> R -> {:keys [tol]} -> {:x R, :value R, :evals Int}

   Example:
     (lipschitz-min #(Math/sin %) 0.0 10.0 1.0)
     ;=> {:x ~4.712389 :value ~-1.0 :evals 6643}

---

### `potentially-optimal-first`

```clojure
(potentially-optimal-first eps)
```

DIRECT's selection rule as a strategy for orhof.search: take every
   potentially optimal rectangle of the current set, then select again.
   Catalog: O37. Pattern: TREE/GRAPH EXPLORER (a strategy is one function).

   R -> Strategy

   Rectangles are maps with :size (centre-to-corner distance) and :y (the
   value at the centre).

   Example:
     (search (potentially-optimal-first 1e-4) rf init expand root)

---


## `orhof.search.learn`

Tree learners on orhof.search: decision trees, random forests, regression
   trees and gradient boosting.
   Pattern: TREE/GRAPH EXPLORER + MODEL BUILDER — growing a tree is an unfold
   (split the rows) whose fold is the constructor, so hylo returns the tree
   itself. A forest is `repeatedly` over that; boosting is `iterate` over it.

   Rows are maps. Trees are plain data:
     classification  {:attribute a, :branches {value subtree}, :default label} | {:label label}
     regression      {:feature f, :threshold t, :left subtree, :right subtree}  | {:value mean}

### `classify`

```clojure
(classify tree row)
```

The label a decision tree gives a row. A value the tree has not seen falls
   back to the majority label at that node.

   Tree -> Row -> Label

   Example:
     (classify tree {:sky :fog :wind :calm}) ;=> :no     ; tree as under decision-tree

---

### `classify-forest`

```clojure
(classify-forest forest row)
```

The label most trees of a forest give a row.

   [Tree] -> Row -> Label

   Example:
     (classify-forest forest row)

---

### `decision-tree`

```clojure
(decision-tree rows attributes target & {:keys [choose], :or {choose identity}})
```

Grows a classification tree by ID3: split on the attribute with the
   highest information gain until the rows agree or nothing is left to gain.
   Pattern: TREE/GRAPH EXPLORER (hylo; the fold is the constructor).

   [Row] -> [Attribute] -> Attribute -> {:keys [choose]} -> Tree

   rows: maps; attributes: the keys to split on; target: the key of the label
   Options:
     :choose  attributes -> the attributes to consider at this split
              (default: all of them; random-forest draws a random few)

   Example:
     (def tree (decision-tree [{:sky :sun :wind :calm :play :yes}
                               {:sky :sun :wind :gale :play :no}
                               {:sky :rain :wind :calm :play :no}
                               {:sky :rain :wind :gale :play :no}]
                              [:sky :wind] :play))
     (classify tree {:sky :sun :wind :calm}) ;=> :yes

---

### `gradient-boosting`

```clojure
(gradient-boosting rows features target & {:keys [n-rounds lr max-depth], :or {n-rounds 50, lr 0.1, max-depth 2}})
```

Gradient boosting for squared error: each round fits a small regression
   tree to the residuals of the model so far and adds a fraction of it.
   Pattern: ITERATE (the round) + TREE/GRAPH EXPLORER (regression-tree).

   [Row] -> [Feature] -> Feature -> {:keys [n-rounds lr max-depth]}
   -> {:base R, :lr R, :trees [Tree]}

   Options:
     :n-rounds   number of trees (default 50)
     :lr         learning rate, the fraction of each tree that is added (default 0.1)
     :max-depth  depth of each tree (default 2)

   Example:
     (def model (gradient-boosting rows [:x] :y :n-rounds 200))
     (predict-boosted model {:x 1.5})

---

### `predict`

```clojure
(predict tree row)
```

The value a regression tree gives a row.

   Tree -> Row -> R

   Example:
     (predict tree {:x 1.5}) ;=> 5.0     ; tree as under regression-tree

---

### `predict-boosted`

```clojure
(predict-boosted model row)
```

The value a boosted model gives a row: the base value plus the learning
   rate times the sum of its trees.

   {:base R, :lr R, :trees [Tree]} -> Row -> R

   Example:
     (predict-boosted model {:x 1.5})     ; model as under gradient-boosting

---

### `random-forest`

```clojure
(random-forest rows attributes target & {:keys [n-trees n-features], :or {n-trees 25}})
```

Grows decision trees on bootstrap samples of the rows, each split choosing
   among a few randomly drawn attributes.
   Pattern: GENERATE (repeatedly) + TREE/GRAPH EXPLORER (decision-tree).

   [Row] -> [Attribute] -> Attribute -> {:keys [n-trees n-features]} -> [Tree]

   Options:
     :n-trees     number of trees (default 25)
     :n-features  attributes drawn at each split (default: floor of the
                  square root of the number of attributes, at least 1)

   Example:
     (def forest (random-forest rows [:a :b :c :d] :label :n-trees 40))
     (classify-forest forest {:a 1 :b 0 :c 2 :d 1}) ;=> :yes

---

### `regression-tree`

```clojure
(regression-tree rows features target & {:keys [max-depth min-rows], :or {max-depth 3, min-rows 1}})
```

Grows a regression tree (CART): split on the feature and threshold that
   most reduce the squared error, down to a maximum depth.
   Pattern: TREE/GRAPH EXPLORER (hylo; the fold is the constructor).

   [Row] -> [Feature] -> Feature -> {:keys [max-depth min-rows]} -> Tree

   Options:
     :max-depth  deepest split (default 3)
     :min-rows   fewest rows on either side of a split (default 1)

   Example:
     (def tree (regression-tree [{:x 1.0 :y 5.0} {:x 2.0 :y 5.0} {:x 3.0 :y 9.0}] [:x] :y))
     (predict tree {:x 2.9}) ;=> 9.0

---


## `orhof.search.diff`

Backpropagation (reverse-mode automatic differentiation) on orhof.search.
   Pattern: TREE/GRAPH EXPLORER — the forward pass is a memoised fold of the
   expression graph; the backward pass is a reduce over its nodes in an order
   that visits every node after all the nodes that use it.

   An expression is a number, a keyword (a variable) or a vector
   [op & operands]. A sub-expression that is the same object in several
   places is one node of the graph: bind it once with `let`, and its value
   and its adjoint are computed once however often it is used.

### `backprop`

```clojure
(backprop expression env & {:keys [operations]})
```

The value of an expression and its derivative with respect to every
   variable, at the point `env`: one forward pass and one backward pass,
   whatever the number of variables.
   Catalog: O5. Pattern: TREE/GRAPH EXPLORER (hylo-eager forward; explore +
   best-first + reduce backward).

   Expression -> {Variable R} -> {:keys [operations]} -> {:value R, :gradient {Variable R}}

   The graph may be as deep as memory allows: neither pass recurses, and
   nodes are told apart by identity, so the cost is linear in the number of
   nodes however large the expressions under them.

   Example:
     ;; d/dx and d/dy of x*y + sin x at (2, 3)
     (backprop [:+ [:* :x :y] [:sin :x]] {:x 2.0 :y 3.0})
     ;=> {:value 6.909297 :gradient {:x 2.583853 :y 2.0}}

     ;; a shared sub-expression is one node: (x*x) * (x*x)
     (let [square [:* :x :x]]
       (backprop [:* square square] {:x 1.5}))
     ;=> {:value 5.0625 :gradient {:x 13.5}}

---

### `operations`

The operations backprop knows: {op {:value f, :partials g}}.
     :value     applies the operation to the values of the operands
     :partials  (fn [operand-values result]) -> the derivative of the result
                with respect to each operand
   Pass an extended map as :operations to add your own.

   Example:
     (assoc operations :relu {:value    #(max 0.0 %)
                              :partials (fn [[x] _] [(if (pos? x) 1.0 0.0)])})

---


