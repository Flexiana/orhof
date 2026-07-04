(ns orhof.val.reach
  "Reachability analysis: interval arithmetic, set propagation.
   Pattern: SET PROPAGATOR — propagate sets through dynamics."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

(defn interval-set
  "Represent a set as an axis-aligned box (interval in each dimension)."
  [lo hi]
  {:lo lo :hi hi})

(defn interval-contains?
  "Check if a point is inside an interval set."
  [iset point]
  (every? true? (map (fn [l h x] (and (<= l x) (<= x h)))
                      (:lo iset) (:hi iset) point)))

(defn interval-union
  "Compute the bounding box of two interval sets."
  [a b]
  (interval-set (mapv min (:lo a) (:lo b))
                (mapv max (:hi a) (:hi b))))

(defn linear-reachability
  "Forward reachability for a linear system x' = Ax + Bu + w.
   Catalog: V33-V36. Pattern: SET PROPAGATOR (iterate + propagate-sets).
   A: state matrix, B: input matrix,
   w-set/u-set/initial-set: interval sets, horizon: steps.
   Uses interval arithmetic for overapproximation."
  [A B w-set u-set initial-set horizon]
  (let [mat-interval-mult
        (fn [M iset]
          (let [lo (:lo iset) hi (:hi iset)]
            (interval-set
             (mapv (fn [row]
                     (reduce + (map (fn [mij lj hj]
                                      (if (>= mij 0) (* mij lj) (* mij hj)))
                                    row lo hi)))
                   M)
             (mapv (fn [row]
                     (reduce + (map (fn [mij lj hj]
                                      (if (>= mij 0) (* mij hj) (* mij lj)))
                                    row lo hi)))
                   M))))
        interval-sum
        (fn [a b]
          (interval-set (mapv + (:lo a) (:lo b))
                        (mapv + (:hi a) (:hi b))))
        step (fn [current-set]
               (let [ax (mat-interval-mult A current-set)
                     bu (mat-interval-mult B u-set)]
                 (interval-sum (interval-sum ax bu) w-set)))]
    (c/propagate-sets step initial-set horizon)))

;; =============================================================
;; ZONOTOPE PROPAGATION
;; =============================================================

(defn make-zonotope
  "Create a zonotope: center + generator matrix.
   Catalog: V35. Pattern: PARAMETERIZE.

   [R] -> [[R]] -> {:center [R], :generators [[R]]}

   A zonotope Z = {c + sum_i a_i * g_i : a_i in [-1, 1]}

   Example:
     (make-zonotope [0 0] [[1 0] [0 1]])
     ;=> {:center [0 0] :generators [[1 0] [0 1]]}"
  [center generators]
  {:center center :generators generators})

(defn zonotope-propagation
  "Forward reachability using zonotope set representation.
   Catalog: V35. Pattern: SET PROPAGATOR + ITERATE.

   [[R]] -> Zonotope -> Int -> [Zonotope]

   For linear system x' = Ax: center' = A*center, generators' = A*generators.

   Example:
     (zonotope-propagation A z0 5) ;=> [z0 z1 z2 z3 z4 z5]"
  [A z0 horizon]
  (let [step (fn [z]
               {:center    (m/mat-vec A (:center z))
                :generators (mapv (fn [g] (m/mat-vec A g)) (:generators z))})]
    (c/propagate-sets step z0 horizon)))

;; =============================================================
;; ELLIPSOID PROPAGATION
;; =============================================================

(defn make-ellipsoid
  "Create an ellipsoid: {x : (x-c)^T P^{-1} (x-c) <= 1}.
   Catalog: V36. Pattern: PARAMETERIZE.

   [R] -> [[R]] -> {:center [R], :shape [[R]]}

   Example:
     (make-ellipsoid [0 0] [[1 0] [0 1]])"
  [center shape-matrix]
  {:center center :shape shape-matrix})

(defn ellipsoid-propagation
  "Forward reachability using ellipsoid set representation.
   Catalog: V36. Pattern: SET PROPAGATOR + ITERATE.

   [[R]] -> Ellipsoid -> Int -> [Ellipsoid]

   For linear system x' = Ax: center' = A*c, shape' = A*P*A^T.

   Example:
     (ellipsoid-propagation A e0 5)"
  [A e0 horizon]
  (let [AT (m/mat-transpose A)
        step (fn [e]
               {:center (m/mat-vec A (:center e))
                :shape  (m/mat-mult A (m/mat-mult (:shape e) AT))})]
    (c/propagate-sets step e0 horizon)))

;; =============================================================
;; BFS/DFS REACHABILITY
;; =============================================================

(defn bfs-reachability
  "Breadth-first search reachability on a discrete graph.
   Catalog: V48. Pattern: ITERATE + DELEGATE.

   State -> (State -> [State]) -> #{State}
   -> {:reached? Bool, :visited #{State}, :path [State]}

   Example:
     (bfs-reachability :a (fn [n] [:b :c]) #{:c})
     ;=> {:reached? true :visited #{:a :b :c}}"
  [start expand-fn goal-set]
  (reduce (fn [{:keys [queue visited]} _]
            (if (empty? queue)
              (reduced {:reached? false :visited visited})
              (let [node     (first queue)
                    rest-q   (vec (rest queue))]
                (if (contains? goal-set node)
                  (reduced {:reached? true :visited (conj visited node) :node node})
                  (let [neighbors (remove visited (expand-fn node))
                        visited'  (into (conj visited node) neighbors)
                        queue'    (into rest-q neighbors)]
                    {:queue queue' :visited visited'})))))
          {:queue [start] :visited #{}}
          (range 10000)))

;; =============================================================
;; PROBABILISTIC REACHABILITY
;; =============================================================

(defn probabilistic-reachability
  "Probabilistic reachability — propagate probability distributions.
   Catalog: V51. Pattern: ITERATE + MAP-OVER + REDUCE.

   {State -> {State -> Prob}} -> {State -> Prob} -> Int -> [{State -> Prob}]

   transition-map: {from -> {to -> prob}}
   initial: {state -> probability}

   Example:
     (probabilistic-reachability {1 {2 0.8 1 0.2} 2 {3 1.0}} {1 1.0} 3)"
  [transition-map initial horizon]
  (let [step (fn [dist]
               (reduce (fn [new-dist [s p]]
                         (reduce (fn [nd [s' tp]]
                                   (update nd s' (fnil + 0.0) (* p tp)))
                                 new-dist
                                 (get transition-map s {})))
                       {}
                       dist))]
    (c/propagate-sets step initial horizon)))
