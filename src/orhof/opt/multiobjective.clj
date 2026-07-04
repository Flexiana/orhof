(ns orhof.opt.multiobjective
  "Multi-objective and discrete optimization methods.
   Catalog: O66-O75. Pattern: TRANSFORM-PROBLEM + POPULATION EVOLVER."
  (:require [orhof.core :as c]
            [orhof.math :as m]
            [orhof.opt.descent :as desc]))

;; =============================================================
;; WEIGHTED SUM METHOD
;; =============================================================

(defn weighted-sum
  "Weighted sum scalarization for multi-objective optimization.
   Catalog: O66. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Converts multiple objectives into one: min sum(w_i * f_i(x))."
  [objectives weights x0 & {:keys [max-iter] :or {max-iter 500}}]
  (let [scalarized (fn [x] (reduce + (map (fn [f w] (* w (f x))) objectives weights)))]
    (desc/gradient-descent scalarized x0 :max-iter max-iter :lr 0.01)))

;; =============================================================
;; EPSILON-CONSTRAINT
;; =============================================================

(defn epsilon-constraint
  "Epsilon-constraint method for multi-objective optimization.
   Catalog: O67. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Minimize first objective, constrain the rest: f_i(x) <= eps_i."
  [primary-obj other-objs epsilons x0 & {:keys [rho max-iter]
                                           :or   {rho 100.0 max-iter 500}}]
  (let [constraints (mapv (fn [fi eps]
                            (fn [x] (max 0.0 (- (fi x) eps))))
                          other-objs epsilons)
        penalized (fn [x]
                    (+ (primary-obj x)
                       (* rho (reduce + (map (fn [c] (let [v (c x)] (* v v)))
                                              constraints)))))]
    (desc/gradient-descent penalized x0 :max-iter max-iter :lr 0.01)))

;; =============================================================
;; PARETO FILTER
;; =============================================================

(defn dominates?
  "Returns true if point a dominates point b (all objectives <=, at least one <)."
  [a b]
  (let [objs-a (:objectives a)
        objs-b (:objectives b)]
    (and (every? true? (map <= objs-a objs-b))
         (some true? (map < objs-a objs-b)))))

(defn pareto-filter
  "Filter a set of points to the Pareto front (non-dominated set).
   Catalog: O68. Pattern: REDUCE.
   Each point has {:x, :objectives [f1 f2 ...]}."
  [points]
  (reduce (fn [front point]
            (let [;; Remove any in front dominated by new point
                  front' (filterv #(not (dominates? point %)) front)]
              ;; Add new point if not dominated by any in front
              (if (some #(dominates? % point) front')
                front'
                (conj front' point))))
          []
          points))

;; =============================================================
;; BRANCH AND BOUND
;; =============================================================

(defn branch-and-bound
  "Branch and bound for discrete optimization.
   Catalog: O72. Pattern: TREE/GRAPH EXPLORER.
   evaluate: node -> objective value (for leaf nodes)
   branch: node -> [child-nodes]
   bound: node -> lower-bound (prune if >= best known)"
  [evaluate branch bound root & {:keys [budget] :or {budget 1000}}]
  (reduce (fn [{:keys [frontier best-node best-val] :as state} _]
            (if (empty? frontier)
              (reduced state)
              (let [;; Select node with best bound
                    node     (apply min-key bound frontier)
                    rest-f   (vec (remove #{node} frontier))
                    lb       (bound node)
                    children (branch node)]
                (if (>= lb best-val)
                  ;; Prune: bound worse than best known
                  {:frontier rest-f :best-node best-node :best-val best-val}
                  (if (empty? children)
                    ;; Leaf node: evaluate
                    (let [val (evaluate (:lo node))]
                      (if (< val best-val)
                        {:frontier rest-f :best-node node :best-val val}
                        {:frontier rest-f :best-node best-node :best-val best-val}))
                    ;; Internal: add children to frontier
                    {:frontier (into rest-f children)
                     :best-node best-node :best-val best-val})))))
          {:frontier [root] :best-node root :best-val Double/MAX_VALUE}
          (range budget)))

;; =============================================================
;; MC UNCERTAINTY PROPAGATION
;; =============================================================

(defn mc-uncertainty-propagation
  "Monte Carlo uncertainty propagation.
   Catalog: O69. Pattern: MAP-OVER + REDUCE.
   Evaluates f over n random samples from the input distribution,
   returns mean and variance of output."
  [f sampler n]
  (let [samples (repeatedly n sampler)
        values  (mapv f samples)
        mean    (/ (reduce + values) (double n))
        variance (/ (reduce + (map (fn [v] (let [d (- v mean)] (* d d))) values))
                    (double n))]
    {:mean mean :variance variance :samples n}))

;; =============================================================
;; ROBUST OPTIMIZATION
;; =============================================================

(defn robust-optimization
  "Robust optimization — minimize worst-case over uncertainty set.
   Catalog: O74. Pattern: TRANSFORM-PROBLEM + COMPOSE.
   Transforms min f(x) into min max_{u in U} f(x, u)."
  [f uncertainty-sampler n-scenarios x0 & {:keys [max-iter] :or {max-iter 500}}]
  (let [robust-f (fn [x]
                   (apply max (map (fn [u] (f x u))
                                   (repeatedly n-scenarios uncertainty-sampler))))]
    (desc/gradient-descent robust-f x0 :max-iter max-iter :lr 0.01)))
