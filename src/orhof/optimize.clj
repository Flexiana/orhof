(ns orhof.optimize
  "Facade namespace — re-exports all optimization algorithms.
   Existing code requiring orhof.optimize continues to work."
  (:require [orhof.opt.bracketing :as brk]
            [orhof.opt.descent :as desc]
            [orhof.opt.second-order :as so]
            [orhof.opt.direct :as dir]
            [orhof.opt.stochastic :as sto]
            [orhof.opt.population :as pop]
            [orhof.opt.constrained :as con]))

;; Bracketing
(def golden-section-search brk/golden-section-search)
(def fibonacci-search brk/fibonacci-search)
(def bisection-method brk/bisection-method)
(def quadratic-fit-search brk/quadratic-fit-search)

;; Descent
(def descent desc/descent)
(def backtracking-line-search desc/backtracking-line-search)
(def gradient-descent desc/gradient-descent)
(def steepest-descent desc/steepest-descent)
(def conjugate-gradient desc/conjugate-gradient)
(def with-momentum desc/with-momentum)
(def momentum-gd desc/momentum-gd)
(def adam desc/adam)
(def adamw desc/adamw)
(def lion desc/lion)
(def nesterov-momentum desc/nesterov-momentum)
(def adagrad desc/adagrad)
(def rmsprop desc/rmsprop)
(def adadelta desc/adadelta)
(def hypergradient-descent desc/hypergradient-descent)

;; Second-order
(def newton-method so/newton-method)
(def secant-method so/secant-method)
(def dfp so/dfp)
(def bfgs so/bfgs)
(def lbfgs so/lbfgs)
(def levenberg-marquardt so/levenberg-marquardt)

;; Direct
(def nelder-mead dir/nelder-mead)
(def cyclic-coordinate-search dir/cyclic-coordinate-search)
(def powells-method dir/powells-method)
(def hooke-jeeves dir/hooke-jeeves)
(def generalized-pattern-search dir/generalized-pattern-search)
(def direct-search dir/direct-search)

;; Stochastic
(def simulated-annealing sto/simulated-annealing)
(def noisy-descent sto/noisy-descent)
(def mads sto/mads)
(def cma-es sto/cma-es)

;; Population
(def cross-entropy-method pop/cross-entropy-method)
(def genetic-algorithm pop/genetic-algorithm)
(def particle-swarm pop/particle-swarm)
(def differential-evolution pop/differential-evolution)
(def firefly-algorithm pop/firefly-algorithm)
(def cuckoo-search pop/cuckoo-search)

;; Constrained
(def penalize con/penalize)
(def augmented-lagrangian con/augmented-lagrangian)
(def interior-point con/interior-point)
(def admm con/admm)
(def dual-ascent con/dual-ascent)
(def kkt-check con/kkt-check)

;; Factory
(defn make-optimizer
  "Factory function that returns a configured optimizer.
   (make-optimizer :adam {:lr 0.001}) -> (fn [f x0] result)"
  [method & {:as config}]
  (case method
    :gradient-descent (fn [f x0] (gradient-descent f x0
                                                    :lr (:lr config 0.01)
                                                    :max-iter (:max-iter config 1000)))
    :steepest-descent (fn [f x0] (steepest-descent f x0
                                                    :max-iter (:max-iter config 1000)))
    :adam             (fn [f x0] (adam f x0
                                       :lr (:lr config 0.001)
                                       :max-iter (:max-iter config 1000)))
    :nelder-mead      (fn [f x0] (nelder-mead f x0
                                               :max-iter (:max-iter config 1000)))
    :simulated-annealing (fn [f x0] (simulated-annealing f x0
                                                          :max-iter (:max-iter config 10000)))
    :cross-entropy    (fn [f x0] (cross-entropy-method f (count x0)
                                                        :max-iter (:max-iter config 100)))
    :genetic          (fn [f x0] (genetic-algorithm f (count x0)
                                                     :max-iter (:max-iter config 200)))
    :pso              (fn [f x0] (particle-swarm f (count x0)
                                                  :max-iter (:max-iter config 200)))
    :de               (fn [f x0] (differential-evolution f (count x0)
                                                          :max-iter (:max-iter config 200)))))
