# orhof — Operations Research in Higher-Order Functions

A dependency-free Clojure library showing that a large body of **operations-research
algorithms** — continuous and discrete optimization, sequential decision making, and
validation — are compositions of a small set of **higher-order function (HOF)
patterns**.

It is the companion library to the book *Applied Higher Order Functions: A Functional
Architecture of Optimization, Decision Making, and Validation* (Jiri Knesl). Every code
listing in the book runs against this library.

- **294 public functions** across **34 namespaces**
- **Zero dependencies** beyond Clojure 1.12
- Three domains: optimization · Markov decision processes / RL · temporal-logic validation
- One tree-search core (`search`, `explore`, `hylo`) under the book's tree-search algorithms

## Install

From Clojars:

```clojure
io.github.flexiana/orhof {:mvn/version "1.0.0"}
```

Or as a git dependency:

```clojure
io.github.Flexiana/orhof {:git/tag "v1.0.0" :git/sha "<sha of the tag>"}
```

## Quick start

```clojure
(require '[orhof.optimize :as opt]
         '[orhof.mdp      :as mdp]
         '[orhof.validate :as val])

;; --- Optimization: gradient descent on a paraboloid ---
(opt/gradient-descent (fn [[x y]] (+ (* x x) (* y y))) [5.0 5.0] :lr 0.1 :max-iter 500)
;; => {:x [~0.0 ~0.0] :value ~0.0 :iterations n :converged? true ...}

;; Same problem, Adam — swap one function, same framework
(opt/adam (fn [[x y]] (+ (* x x) (* y y))) [5.0 5.0] :lr 0.1 :max-iter 500)

;; --- Decision making: a multi-armed bandit via Thompson sampling ---
(def bandit (mdp/make-bandit [(fn [] (if (< (rand) 0.3) 1.0 0.0))
                              (fn [] (if (< (rand) 0.7) 1.0 0.0))]))
(mdp/thompson-sampling bandit :n-rounds 2000)   ; => {:alphas ... :betas ... :counts ...}

;; --- Validation: falsify a temporal-logic specification ---
;; see orhof.validate / orhof.val.* and docs/API.md

;; --- Tree search: unfold a space and fold it in the same pass ---
(require '[orhof.search :refer [explore search hylo breadth-first best-first unique]])

(take 5 (explore breadth-first (fn [n] [(* 2 n) (inc (* 2 n))]) 1))   ; an infinite tree
;; => (1 2 3 4 5)

((hylo (fn [[x y]] (cond-> [] (< x 16) (conj [(inc x) y]) (< y 16) (conj [x (inc y)])))
       (fn [_ counts] (if (seq counts) (reduce + counts) 1N))
       :memo identity)                                               ; dynamic programming
 [0 0])
;; => 601080390N   paths across a 16x16 grid
```

## Namespaces

| Namespace | What it provides |
|---|---|
| `orhof.core` | HOF primitives — iterative refinement, function transformers, pipeline composition, convergence predicates — plus vector math |
| `orhof.math` | linear algebra, calculus, and probability utilities |
| `orhof.diff` | differentiation as a HOF: `gradient`, `hessian`, finite differences, autodiff |
| `orhof.optimize`, `orhof.opt.*` | the generic `descent` framework, Newton/quasi-Newton, Adam & friends, genetic algorithms, PSO, simulated annealing, constrained (penalty / barrier / augmented Lagrangian), linear/quadratic, surrogates, multi-objective |
| `orhof.mdp`, `orhof.mdp.*` | value & policy iteration, Q-learning, MCTS, bandits, belief filters (Kalman/particle), games/equilibria, inference |
| `orhof.validate`, `orhof.val.*` | temporal-logic specs (`always`, `eventually`, `until`), robustness semantics, falsification, reachability, sampling, counterfactual explanation |
| `orhof.search` | the tree-search pattern as composable HOFs: strategies (`depth-first`, `breadth-first`, `best-first`, `unique`), `search`, `explore`, `hylo`, `hylo-eager`, `converge` |
| `orhof.search.*` | algorithms built on it: shortest paths (`graph`), minimax, alpha-beta, expectiminimax, MCTS (`games`), forward search, branch and bound, sparse sampling, belief-space planning (`mdp`), adaptive stress testing (`val`), Lipschitz search, DIRECT, simplex, integer programming (`opt`), decision trees, forests, boosting (`learn`), backpropagation (`diff`) |
| `orhof.examples` | cross-domain compositions that show the patterns interlocking |

Full generated reference: [`docs/API.md`](docs/API.md). Design notes: [`ARCHITECTURE.md`](ARCHITECTURE.md),
[`docs/SEARCH.md`](docs/SEARCH.md).

## The idea

The book's thesis is that these algorithms are not hundreds of unrelated recipes but
instances of a few higher-order patterns. Two examples the code makes concrete:

- **One `descent` framework, many optimizers.** Gradient descent, steepest descent,
  conjugate gradient, and Adam are the same template parameterized by a *direction
  function* and a *step function*.
- **The Bellman operator is a function transformer** `mdp -> (V -> V')`. Value iteration
  is just its fixpoint; temporal-logic operators are HOFs on specifications that return
  continuous robustness values.
- **Tree search is one unfold and one fold.** Dijkstra, alpha-beta, branch and bound,
  DIRECT, MCTS, ID3 and backpropagation differ in what they plug in: a *successor
  function*, a *fold*, and for the top-down ones a *strategy* that orders the frontier.
  The space is never built.

## Testing

```bash
clojure -X:test
```

236 tests. Stochastic algorithms (GA, bandits, PSO, sampling, …) are seeded via a shared
fixture, so the suite is deterministic.

## Releasing

Push a tag `vX.Y.Z`. The Release workflow runs the tests and deploys `X.Y.Z` to Clojars
as `io.github.flexiana/orhof`. It needs the repository secrets `CLOJARS_USERNAME` and
`CLOJARS_PASSWORD` (a Clojars deploy token). Locally: `clojure -T:build jar`.

## License

**MIT** — see [LICENSE](LICENSE). Copyright © 2026 Jiri Knesl.
The companion book is a separate work, licensed CC BY-SA 4.0.
