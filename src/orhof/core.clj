(ns orhof.core
  "The twelve fundamental higher-order function patterns.

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
     fit-model       — data-driven function construction"
  (:require [orhof.math :as m]))

;; =============================================================
;; Vector Math — re-exported from orhof.math for convenience
;; =============================================================

(def v+ m/v+)
(def v- m/v-)
(def v* m/v*)
(def dot m/dot)
(def vnorm m/vnorm)
(def v-zero m/v-zero)

;; =============================================================
;; Pattern 1: Iterative Refinement Operator (IRO)
;; =============================================================

(defn iterative-solve
  "The universal iterative solver. Pattern 1: IRO.

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
     ;=> {:x 0.039 :value 0.0015 :converged true :iterations 8}"
  [{:keys [step-fn converged? init-state max-iter]
    :or   {max-iter 1000}}]
  (let [indexed-states (->> (iterate step-fn init-state)
                            (take (inc max-iter))
                            (map-indexed vector))
        [i final] (reduce (fn [_ [i state]]
                            (if (converged? state)
                              (reduced [i state])
                              [i state]))
                          [0 init-state]
                          indexed-states)]
    (assoc final :iterations i :converged (boolean (converged? final)))))

(defn iterate-trajectory
  "Full trajectory capture via iterate.

   {:step-fn (State -> State), :init-state State, :max-iter Int}
   -> (Seq State)

   Returns a lazy sequence of all states.

   Example:
     (iterate-trajectory {:init-state 1 :step-fn inc :max-iter 5})
     ;=> (1 2 3 4 5 6)"
  [{:keys [step-fn init-state max-iter] :or {max-iter 1000}}]
  (->> (iterate step-fn init-state)
       (take (inc max-iter))))

(defn iterate-until
  "Iterate step-fn until pred holds.

   (a -> a) -> (a -> Bool) -> a -> a

   Example:
     (iterate-until #(* 2 %) #(> % 100) 1) ;=> 128"
  [step-fn pred init]
  (->> (iterate step-fn init)
       (take 100001)
       (drop-while (complement pred))
       first))

;; --- Convergence predicates (composable) ---

(defn value-converged?
  "Convergence predicate: |value - prev-value| < tol.

   R -> (State -> Bool)

   Example:
     (value-converged? 1e-6)"
  [tol]
  (fn [state]
    (when-let [prev (:prev-value state)]
      (< (abs (- (:value state) prev)) tol))))

(defn point-converged?
  "Convergence predicate: ||x - prev-x|| < tol.

   R -> (State -> Bool)

   Example:
     (point-converged? 1e-6)"
  [tol]
  (fn [state]
    (when-let [prev (:prev-x state)]
      (< (vnorm (v- (:x state) prev)) tol))))

(defn or-converged
  "Combine convergence predicates with OR.

   (State -> Bool) -> ... -> (State -> Bool)

   Example:
     (or-converged (value-converged? 1e-6) (point-converged? 1e-6))"
  [& preds]
  (fn [state] (some #(% state) preds)))

;; =============================================================
;; Pattern 2: Fold (reduce)
;; =============================================================

(defn fold-over-stream
  "Fold an update function over a stream of observations.

   (State -> Obs -> State) -> State -> [Obs] -> State

   Example:
     (fold-over-stream + 0 [1 2 3 4]) ;=> 10"
  [update-fn init-state observations]
  (reduce update-fn init-state observations))

(defn fold-collecting
  "Like fold-over-stream but returns all intermediate states.

   (State -> Obs -> State) -> State -> [Obs] -> (Seq State)

   Example:
     (fold-collecting + 0 [1 2 3]) ;=> (0 1 3 6)"
  [update-fn init-state observations]
  (reductions update-fn init-state observations))

;; =============================================================
;; Pattern 3: Composition (comp)
;; =============================================================

(defn compose-pipeline
  "Compose stages left-to-right (data flows first -> last).

   (a -> b) -> (b -> c) -> ... -> (a -> z)

   Example:
     ((compose-pipeline inc #(* 2 %)) 3) ;=> 8"
  [& stages]
  (apply comp (reverse stages)))

;; =============================================================
;; Pattern 5: Generate-and-Test (repeatedly + filter)
;; =============================================================

(defn sample-until
  "Generate samples until accept? returns true.

   (() -> a) -> (a -> Bool) -> a

   Example:
     (sample-until #(rand-int 100) #(> % 95)) ;=> 97"
  [sampler-fn accept?]
  (->> (repeatedly sampler-fn)
       (filter accept?)
       first))

(defn sample-aggregate
  "Generate n samples, evaluate each, aggregate.

   {:sampler (() -> a), :evaluator (a -> b),
    :aggregator ([b] -> c), :n Int} -> c

   Example:
     (sample-aggregate {:sampler rand :evaluator identity
                        :aggregator #(/ (reduce + %) (count %)) :n 1000})
     ;=> ~0.5"
  [{:keys [sampler evaluator aggregator n]}]
  (->> (repeatedly n sampler)
       (map evaluator)
       aggregator))

;; =============================================================
;; Pattern 6: Population Evolver
;; =============================================================

(defn population-step
  "One generation of the population pipeline.

   (a -> R) -> ([Scored] -> [a]) -> ([a] -> [a]) -> (a -> a) -> [a] -> [a]

   evaluate:  individual -> fitness
   select:    scored-population -> parents
   recombine: parents -> offspring
   mutate:    individual -> individual'

   Example:
     (population-step fitness-fn tournament-select crossover mutate pop)"
  [evaluate select recombine mutate population]
  (let [scored (map (fn [ind] {:individual ind :fitness (evaluate ind)}) population)
        parents (select scored)
        offspring (recombine parents)
        mutated (map mutate offspring)]
    mutated))

(defn evolve
  "Evolve a population k-max generations.

   ([a] -> Int -> [a]) -> [a] -> Int -> [a]

   Pattern: reduce over generation indices.

   Example:
     (evolve (fn [pop k] (map inc pop)) [0 0 0] 5) ;=> [5 5 5]"
  [pop-transform population k-max]
  (reduce (fn [pop k] (pop-transform pop k))
          population
          (range k-max)))

(defn best-of
  "Return the best individual (minimization).

   (a -> R) -> [a] -> a

   Example:
     (best-of #(Math/abs %) [-3 1 -5 2]) ;=> 1"
  [fitness-fn population]
  (apply min-key fitness-fn population))

;; =============================================================
;; Pattern 7: Tree/Graph Explorer
;; =============================================================

(defn explore-tree
  "Explore a tree from root with budget.

   (Node -> [Node]) -> ([Node] -> Node) -> (Node -> R) -> Node -> Int
   -> {:best-node Node, :best-val R}

   expand:   node -> children
   select:   frontier -> next node to expand
   evaluate: node -> value (lower is better)

   Pattern: reduce over budget steps.

   Example:
     (explore-tree expand-fn first eval-fn root 100)"
  [expand select evaluate root budget]
  (let [init-val (evaluate root)]
    (reduce (fn [{:keys [frontier best-node best-val] :as state} _]
              (if (empty? frontier)
                (reduced (assoc state :done true))
                (let [node      (select frontier)
                      children  (expand node)
                      rest-f    (vec (remove #{node} frontier))
                      frontier' (into rest-f children)
                      [bn bv]   (reduce (fn [[bn bv] c]
                                          (let [v (evaluate c)]
                                            (if (< v bv) [c v] [bn bv])))
                                        [best-node best-val]
                                        children)]
                  {:frontier frontier' :best-node bn :best-val bv})))
            {:frontier [root] :best-node root :best-val init-val}
            (range budget))))

;; =============================================================
;; Pattern 2b: Function Transformer
;; =============================================================

(defn transform
  "Apply a transformer HOF to a function.

   ((a -> b) -> (a -> c)) -> (a -> b) -> (a -> c)

   Example:
     (transform (fn [f] (fn [x] (* 2 (f x)))) inc)
     ;=> (fn [x] (* 2 (inc x)))"
  [transformer f]
  (transformer f))

(defn compose-transformers
  "Compose transformers left-to-right.

   ((f -> f) -> ... -> (f -> f)) -> (f -> f)

   (compose-transformers T1 T2 T3) = (fn [f] (T3 (T2 (T1 f))))"
  [& transformers]
  (fn [f] (reduce (fn [g t] (t g)) f transformers)))

;; =============================================================
;; Pattern 8: Belief Updater
;; =============================================================

(defn make-updater
  "Create a belief update function from a model.

   {:predict (Belief -> Action -> Predicted),
    :update  (Predicted -> Obs -> Posterior)}
   -> (Belief -> Action -> Obs -> Belief')

   Example:
     (def updater (make-updater {:predict predict-fn :update update-fn}))
     (updater belief :move :obs-x) ;=> new-belief"
  [model]
  (fn [belief action observation]
    (let [predicted ((:predict model) belief action)]
      ((:update model) predicted observation))))

;; =============================================================
;; Pattern 10: Set Propagator
;; =============================================================

(defn propagate-sets
  "Propagate a set through dynamics for horizon steps.

   (Set -> Set) -> Set -> Int -> [Set]

   Returns [initial-set, step-1, step-2, ...].
   Pattern: iterate + take.

   Example:
     (propagate-sets #(* 2 %) 1 3) ;=> [1 2 4 8]"
  [dynamics initial-set horizon]
  (->> (iterate dynamics initial-set)
       (take (inc horizon))
       vec))

(defn lift-to-set
  "Lift a point function to operate on a set (collection).

   (a -> b) -> ([a] -> [b])

   Example:
     ((lift-to-set inc) [1 2 3]) ;=> (2 3 4)"
  [f]
  (fn [s] (map f s)))

(defn lift-to-interval
  "Lift a scalar function to interval arithmetic.

   (R -> R) -> ([R R] -> [R R])

   Example:
     ((lift-to-interval #(* % %)) [2 3]) ;=> [4 9]"
  [f]
  (fn [[lo hi]]
    (let [a (f lo) b (f hi)]
      [(min a b) (max a b)])))

;; =============================================================
;; Pattern 11: Sensitivity Analyzer
;; =============================================================

(defn analyze-sensitivity
  "Analyze sensitivity of f using a perturbation method.

   (a -> b) -> ((a -> b) -> Info) -> Info

   Example:
     (analyze-sensitivity my-fn finite-difference-method)"
  [f method]
  (method f))

;; =============================================================
;; Decorators (Pattern 2 applied to step functions)
;; =============================================================

(defn wrap-with-logging
  "Wrap a step function to log each iteration.

   (State -> State) -> (State -> State -> nil) -> (State -> State)

   Example:
     (wrap-with-logging step-fn (fn [old new] (println (:value new))))"
  [step-fn log-fn]
  (fn [state]
    (let [new-state (step-fn state)]
      (log-fn state new-state)
      new-state)))

(defn wrap-with-budget
  "Wrap a solver to enforce a computational budget.

   (Problem -> Result) -> Int -> (Problem -> Result)"
  [solver max-evals]
  (fn [problem]
    (let [eval-count (atom 0)
          counted-f  (fn [x]
                       (swap! eval-count inc)
                       ((:objective problem) x))]
      (solver (assoc problem
                     :objective counted-f
                     :budget max-evals
                     :eval-count eval-count)))))

(defn wrap-with-history
  "Wrap a step function to accumulate history.

   (State -> State) -> (State -> State)

   Adds :history key with vector of previous states."
  [step-fn]
  (fn [state]
    (let [new-state (step-fn state)]
      (update new-state :history (fnil conj []) (dissoc state :history)))))

;; =============================================================
;; Pattern 12: Pipeline Composer (transform-problem)
;; =============================================================

(defn transform-problem
  "Transform a problem, solve it, decode the result.

   (Problem -> Problem') -> (Problem' -> Raw) -> Problem -> Result
   (Problem -> Problem') -> (Problem' -> Raw) -> (Raw -> Result) -> Problem -> Result

   Example:
     (transform-problem normalize-fn solver-fn denormalize-fn problem)"
  ([transform solver problem]
   (transform-problem transform solver identity problem))
  ([transform solver decode problem]
   (-> problem transform solver decode)))

;; =============================================================
;; Algorithm Factory (Pattern 3: Parameterized Strategy)
;; =============================================================

(defn make-algorithm
  "Create a configured solver from algorithm + strategy config.

   (Config -> Result) -> Config -> (Problem -> Result)

   Example:
     (def solver (make-algorithm optimize {:lr 0.01 :max-iter 500}))
     (solver {:objective sphere :x0 [5 5]})"
  [algorithm-fn config]
  (fn [problem]
    (algorithm-fn (merge config problem))))

(defn with-strategy
  "Inject a strategy function into a config map.

   Config -> Keyword -> (a -> b) -> Config

   Example:
     (with-strategy config :direction-fn my-direction)"
  [config strategy-key strategy-fn]
  (assoc config strategy-key strategy-fn))

;; =============================================================
;; Pattern 4: Model Builder
;; =============================================================

(defn fit-model
  "Fit a model to data, returning a prediction function.

   {:fit (Data -> (X -> Y))} -> Data -> (X -> Y)

   Example:
     (fit-model {:fit (fn [data] (fn [x] (mean data)))} [1 2 3])
     ;=> (fn [x] 2.0)"
  [model-class data]
  ((:fit model-class) data))
