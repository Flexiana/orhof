# Architecture

`orhof` is organized as a thin layer of higher-order **primitives** (`orhof.core`) that
the three domain layers — optimization, decision making, validation — build on. Nothing
depends on anything outside Clojure 1.12.

## Layers

```
orhof.core                 HOF primitives + vector math (the vocabulary)
orhof.math   orhof.diff    numerical utilities; differentiation as a HOF

optimization   orhof.optimize + orhof.opt.{bracketing,descent,second-order,direct,
                                            stochastic,population,constrained,linear,
                                            surrogate,multiobjective}
decision       orhof.mdp      + orhof.mdp.{planning,online,policy,beliefs,games,
                                           inference,learning,approx}
validation     orhof.validate + orhof.val.{spec,falsify,sampling,reach,explain}

orhof.examples             cross-domain compositions
```

The `orhof.optimize`, `orhof.mdp`, and `orhof.validate` namespaces are curated facades
that re-export the most-used functions from their submodules, so callers can `require`
one namespace per domain.

## Key design patterns

- **The `descent` framework** (`orhof.opt.descent`) is the generic template for
  gradient-based optimization: `(descent {:direction-fn ... :step-fn ... :f f :x0 x0})`.
  Gradient descent, steepest descent, conjugate gradient, momentum, and Adam differ only
  in the direction/step functions they plug in.

- **The Bellman operator is a function transformer** `mdp -> (V -> V')`
  (`orhof.mdp`). Value iteration applies it to a fixpoint; policy iteration alternates
  evaluation and greedy improvement over the same operator.

- **Temporal-logic operators are HOFs on specifications** (`orhof.val.spec`):
  `always`, `eventually`, and `until` take spec functions and return spec functions, with
  robustness semantics that yield continuous truth values instead of booleans — which is
  what makes falsification (`orhof.val.falsify`) an optimization problem.

- **Rich return maps.** Algorithms return maps (`:x`, `:value`, `:iterations`,
  `:converged?`, `:trajectory`, `:status`, …) rather than bare answers, so one
  algorithm's output composes into another's input.

## Conventions

- Optional parameters are keyword arguments with sensible defaults.
- Convergence predicates end in `?` and are first-class values.
- Stochastic functions route all randomness through `clojure.core/rand`
  (`rand-int`/`rand-nth` delegate to it), so tests seed a single RNG for determinism.

## Regenerating the API reference

`docs/API.md` is generated from namespace and function docstrings:

```bash
clojure -M generate_docs.clj
```
