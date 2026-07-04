(ns orhof.validate
  "Facade namespace — re-exports all validation algorithms.
   Existing code requiring orhof.validate continues to work."
  (:require [orhof.val.spec :as spec]
            [orhof.val.falsify :as fal]
            [orhof.val.sampling :as samp]
            [orhof.val.reach :as reach]
            [orhof.val.explain :as expl]))

;; Specs
(def make-predicate-spec spec/make-predicate-spec)
(def always spec/always)
(def eventually spec/eventually)
(def until-op spec/until-op)
(def spec-and spec/spec-and)
(def spec-or spec/spec-or)
(def spec-not spec/spec-not)
(def bounded-always spec/bounded-always)
(def bounded-eventually spec/bounded-eventually)
(def make-system spec/make-system)
(def rollout spec/rollout)

;; Falsification
(def falsify fal/falsify)
(def random-falsification fal/random-falsification)
(def adaptive-stress-test fal/adaptive-stress-test)

;; Sampling
(def rejection-sampler samp/rejection-sampler)
(def metropolis-hastings samp/metropolis-hastings)
(def importance-sampling samp/importance-sampling)
(def cross-entropy-estimation samp/cross-entropy-estimation)

;; Reachability
(def interval-set reach/interval-set)
(def interval-contains? reach/interval-contains?)
(def interval-union reach/interval-union)
(def linear-reachability reach/linear-reachability)

;; Explainability
(def robustness-sensitivity expl/robustness-sensitivity)
(def shapley-values expl/shapley-values)
(def make-monitor expl/make-monitor)
