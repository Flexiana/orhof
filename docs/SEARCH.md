# Tree search in orhof

`orhof.search` turns Pattern 7 of the book, the Tree/Graph Explorer, into a few
higher-order functions. A search space is unfolded and folded in the same pass, so it is
never built in memory. The namespace needs nothing but `clojure.core`.

## The functions

| Function | What it does | Runs as |
|---|---|---|
| `(search strategy rf init expand & roots)` | folds the nodes top-down; the accumulator steers the unfold | a loop |
| `(explore strategy expand & roots)` | the nodes as a reducible, seqable collection | `clojure.core/iteration` |
| `(hylo expand fold)` | returns `node -> value`, a fold of the children's values; children are lazy | recursion |
| `(hylo-eager expand fold)` | the same on an explicit stack; every child is evaluated | a loop |
| `(converge close? xs)` | the first element close to its predecessor | `reduce` |
| `depth-first`, `breadth-first`, `(best-first priority)` | strategies | list, queue, sorted map |
| `(unique-by key strategy)`, `(unique strategy)` | make a strategy safe on graphs | a set of keys |
| `(by-identity)` | a key function that tells nodes apart by identity | `IdentityHashMap` |

Which one to reach for:

- A running best prunes the rest, or the search stops at a goal: `search`.
- You only want the nodes, in some order: `explore`.
- The value of a node is computed from the values of its children: `hylo`.
- Every node has one child: `iterate`, then `converge`.

## A strategy is one function

A strategy decides which pending node comes next. Like a reducing function it has three
arities, and its frontier is plain data:

```clojure
(strategy)                 ; -> an empty frontier
(strategy frontier)        ; -> [node frontier'], or nil when nothing is pending
(strategy frontier nodes)  ; -> the frontier with the sibling nodes added
```

A new strategy or strategy transformer is therefore one `fn`, with no type and no
protocol. This one gives up after `n` nodes:

```clojure
(defn at-most [n strategy]
  (fn ([] [0 (strategy)])
      ([[taken frontier]]
       (when (< taken n)
         (when-let [[node frontier] (strategy frontier)]
           [node [(inc taken) frontier]])))
      ([[taken frontier] nodes] [taken (strategy frontier nodes)])))
```

## What is built on it

| Algorithm | Function | Scheme | Checked against |
|---|---|---|---|
| Dijkstra, A* | `graph/shortest-path` | `explore` + `best-first` + `unique-by` | `orhof.mdp.online/a-star`; A* against Dijkstra on 60 mazes |
| Reachability | `graph/reachable`, `mdp/reachable-states` | `explore` + `unique` | by hand |
| Minimax | `games/minimax` | `hylo` | tic-tac-toe: value 0, 549,946 nodes |
| Alpha-beta | `games/alpha-beta` | `hylo`; a value is a function of the window | minimax on 72 positions; 18,297 nodes, the count of the two-loop version |
| Expectiminimax | `games/expectiminimax` | `hylo` | plain recursion on 200 random trees |
| MCTS, AlphaZero-style MCTS | `games/mcts` | `iterate`; two `explore` walks and a `reduce` | a perfect player: an optimal move on 60 of 60 positions |
| Forward search | `mdp/forward-search` | `hylo-eager` + `:memo` | d Bellman sweeps on 30 random MDPs |
| Branch and bound (MDP) | `mdp/branch-and-bound` | `hylo`, unread children | forward search; 1,281 action evaluations instead of 1,530 |
| Sparse sampling | `mdp/sparse-sampling` | `hylo-eager` over a sampled tree | forward search |
| POMDP planning | `mdp/belief-mdp` | a problem transformer, then any MDP algorithm | the tiger problem by hand, depths 1 to 5 |
| Adaptive stress testing | `val/adaptive-stress-test` | `mcts` inside `iterate` | brute force over 3^8 sequences |
| Most likely failure, exact | `val/most-likely-failure` | `shortest-path` | brute force over 3^8 sequences |
| Lipschitz optimization | `opt/lipschitz-min` | `search` + `best-first` | a 480,000-point grid |
| DIRECT | `opt/direct` | `search` + its own strategy | four standard test functions, within 0.01% in 400 evaluations |
| Simplex | `opt/linear-program` | `iterate` + `converge` | vertex enumeration on 150 LPs, exactly; `orhof.opt.linear/simplex` |
| Branch and bound (IP) | `opt/integer-program` | `search` + `best-first` | brute force on 200 instances |
| Decision tree (ID3) | `learn/decision-tree` | `hylo`; the fold is the constructor | Quinlan's play-tennis tree |
| Random forest | `learn/random-forest` | `repeatedly` over `decision-tree` | beats one tree on 8 of 8 seeds, 0.896 against 0.831 |
| Regression tree, gradient boosting | `learn/regression-tree`, `learn/gradient-boosting` | `hylo`; `iterate` | error 0.4855 to 0.0006 in 200 rounds |
| Backpropagation | `diff/backprop` | `hylo-eager` forward; `explore` + `reduce` backward | central differences on 300 random graphs; `orhof.diff` |

These namespaces use the conventions of the rest of the library: MDPs are the maps of
`orhof.mdp.planning/make-mdp`, systems are the maps of `orhof.val.spec/make-system`,
options are keyword arguments, results are maps, optimizers minimize, and all randomness
goes through `clojure.core/rand`.

## How it composes

Each claim is a test in `test/orhof/search_test.clj`.

- **Successor functions** are plain functions. Two of them compose with `mapcat`
  (`(fn [node] (mapcat g (f node)))`), and decorators such as a depth limit or a pruning
  predicate stack with `->>`. Iterative deepening is `some` over depth limits; beam
  search is `iterate` over the beam.
- **Strategies** compose with `comp`, because a transformer takes a strategy and returns
  one. Order matters, as with transducers: a budget of 6 outside `unique` yields 6 nodes,
  inside it 5.
- **The fold** of `search` is any reducing function, so transducers apply. `transduce`
  over `explore` equals `search` with the transduced reducing function.
- **`hylo` returns a function**, so `map`, `comp` and `juxt` apply. Two laws hold on 100
  random trees: folding while unfolding equals building the tree and then folding it, and
  two folds in one pass equal two passes.
- **Whole algorithms** compose because they take and return functions and plain data:
  `(forward-search (belief-mdp pomdp) belief)` plans under partial observability, and
  `(:V (value-iteration mdp))` is a `:leaf` estimate for `forward-search`.

## Limits

Measured on JVM 21 with the default 1 MB thread stack.

- **`hylo` recurses.** It overflows the stack at about 1,500 levels (plain recursion
  reaches about 6,000; a level of `hylo` costs four frames). Game trees, lookahead trees
  and decision trees are far shallower. Expression graphs and dynamic programs over long
  inputs are not: use `hylo-eager`, which folded a chain of a million nodes in 1.6 s.
- **`hylo-eager` cannot prune.** It evaluates every child before the parent, so alpha-beta
  and branch and bound need `hylo`. Whenever the fold reads every child the two agree.
- **Keys are compared by value.** `unique`, `unique-by` and `:memo` keep their keys in
  hash sets and maps, and a lookup that lands on another key compares the two with `=`.
  For keys that are deep nested data that walk is slow, and both `=` and `hash` recurse:
  `unique` over a 40,000-level expression overflows the stack, and with a 512 MB stack
  takes 8.3 s. With `(unique-by (by-identity) ...)` it takes 0.2 s. `diff/backprop` uses
  identity keys and differentiates that expression in 0.8 s.
- **`search` is terminal, like `reduce`.** It returns a value, and it folds a node when
  the strategy takes it, not when `expand` creates it.
- **MCTS keeps its statistics per state**, so positions reached by different move orders
  share them.
- **`linear-program` is a dense tableau** meant for small problems. `integer-program`
  needs integers or ratios, and a bounded feasible region.
- **`adaptive-stress-test` is stochastic.** On the thermostat it found a failure from 20
  of 20 seeds and the most likely one from 11. `most-likely-failure` is exact.

## Older functions with the same job

The functions below predate `orhof.search` and are unchanged.

| Older function | Counterpart | What the counterpart adds |
|---|---|---|
| `orhof.core/explore-tree` | `search`, `explore` | the selection rule is a strategy; the frontier is a stack, queue or sorted map instead of a vector that is rescanned |
| `orhof.mdp.online/a-star` | `graph/shortest-path` | a priority queue; no cap on the number of expansions; a `:key` for nodes that carry more than their state |
| `orhof.val.reach/bfs-reachability` | `graph/reachable` | several roots; no cap on the number of expansions |
| `orhof.mdp.online/forward-search` | `mdp/forward-search` | equal states at equal depth are solved once; returns the value too; a `:leaf` estimate |
| `orhof.mdp.online/sparse-sampling` | `mdp/sparse-sampling` | returns the value too; a `:leaf` estimate |
| `orhof.mdp.online/mcts` | `games/mcts` | two-player games; playouts that run to the end of the game; an evaluator and a prior (PUCT) |
| `orhof.opt.multiobjective/branch-and-bound` | `search` + `best-first` | any strategy; see `opt/integer-program` for a use |
| `orhof.opt.direct/direct-search` | `opt/direct` | divides every potentially optimal rectangle, the rule of Jones, Perttunen and Stuckman, instead of the best one |
| `orhof.opt.linear/simplex` | `opt/linear-program` | two phases, so `b` may be negative; reports infeasible and unbounded problems; exact arithmetic |
| `orhof.val.falsify/adaptive-stress-test` | `val/adaptive-stress-test` | tree search over a discrete disturbance model instead of random sampling |
| `orhof.diff/reverse-ad` | `diff/backprop` | expressions are data; a node used in several places has one adjoint |

## What clojure.core already has

| In core | What it covers | What it leaves out |
|---|---|---|
| `tree-seq` | depth-first, pre-order walk of a tree | other orders; graphs; pruning by what has been seen so far |
| `iteration` | a reducible, seqable unfold with a continuation token | it is linear; `explore` is `iteration` whose token is a frontier |
| `reduce` + `reduced` | a fold that can stop early | the fold cannot steer the generator |
| `iterate` | the one-child unfold | a stopping rule; `converge` is one |
| `clojure.walk/postwalk` | a bottom-up fold of a nested structure | the structure must exist first; no laziness, no memo |
| `clojure.zip` | a cursor over a tree that exists | depth-first only |
| `memoize` | caching by arguments | nothing; `hylo :memo` is `memoize` with a key function |

## References

- E. Meijer, M. Fokkinga, R. Paterson, *Functional Programming with Bananas, Lenses,
  Envelopes and Barbed Wire*, 1991: hylomorphisms.
- D. R. Jones, C. D. Perttunen, B. E. Stuckman, *Lipschitzian Optimization Without the
  Lipschitz Constant*, JOTA 79, 1993: DIRECT.
- J. R. Quinlan, *Induction of Decision Trees*, Machine Learning 1, 1986: ID3 and the
  play-tennis table.
- R. G. Bland, *New Finite Pivoting Rules for the Simplex Method*, 1977.
- L. Kocsis, C. Szepesvári, *Bandit Based Monte-Carlo Planning*, 2006: UCT.
- R. Lee et al., *Adaptive Stress Testing: Finding Likely Failure Events with
  Reinforcement Learning*, JAIR 69, 2020.
