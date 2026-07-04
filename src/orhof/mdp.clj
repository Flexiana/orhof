(ns orhof.mdp
  "Facade namespace — re-exports all decision making algorithms.
   Existing code requiring orhof.mdp continues to work."
  (:require [orhof.mdp.planning :as plan]
            [orhof.mdp.online :as online]
            [orhof.mdp.policy :as pol]
            [orhof.mdp.beliefs :as bel]
            [orhof.mdp.games :as games]))

;; Planning
(def make-mdp plan/make-mdp)
(def async-value-iteration plan/async-value-iteration)
(def bellman-operator plan/bellman-operator)
(def bellman-policy-operator plan/bellman-policy-operator)
(def lookahead plan/lookahead)
(def greedy-policy plan/greedy-policy)
(def value-iteration plan/value-iteration)
(def policy-evaluation plan/policy-evaluation)
(def policy-iteration plan/policy-iteration)

;; Online
(def mcts online/mcts)
(def forward-search online/forward-search)
(def sparse-sampling online/sparse-sampling)
(def rollout-with-heuristic online/rollout-with-heuristic)
(def a-star online/a-star)
(def receding-horizon online/receding-horizon)
(def open-loop-planning online/open-loop-planning)

;; Policy
(def q-learning pol/q-learning)
(def sarsa pol/sarsa)
(def reinforce pol/reinforce)
(def evolution-strategies pol/evolution-strategies)
(def reward-to-go pol/reward-to-go)
(def gae pol/gae)

;; Beliefs
(def discrete-filter bel/discrete-filter)
(def kalman-filter bel/kalman-filter)
(def extended-kalman-filter bel/extended-kalman-filter)
(def particle-filter bel/particle-filter)

;; Games & Bandits
(def make-bandit games/make-bandit)
(def epsilon-greedy-bandit games/epsilon-greedy-bandit)
(def ucb1-bandit games/ucb1-bandit)
(def thompson-sampling games/thompson-sampling)
(def make-normal-form-game games/make-normal-form-game)
(def iterated-best-response games/iterated-best-response)
(def fictitious-play games/fictitious-play)
(def nash-support-enum games/nash-support-enum)
