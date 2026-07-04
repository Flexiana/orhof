(ns orhof.val.spec
  "Temporal logic specifications and system simulation.
   Specifications ARE functions: trajectory -> robustness value.
   Temporal operators ARE higher-order functions on specifications.
   Pattern: FUNCTION TRANSFORMER."
  (:require [orhof.core :as c]))

(defn make-predicate-spec
  "Create a specification from a predicate on states.
   predicate: state -> boolean
   Returns a robustness function: state -> R (positive = satisfied)."
  [predicate margin-fn]
  (fn [state] (margin-fn state)))

(defn always
  "Temporal operator [] (always/globally).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (always spec) checks that spec holds at every time step.
   Robustness: min over all time steps."
  [spec]
  (fn [trajectory] (apply min (map spec trajectory))))

(defn eventually
  "Temporal operator <> (eventually/finally).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (eventually spec) checks that spec holds at some time step.
   Robustness: max over all time steps."
  [spec]
  (fn [trajectory] (apply max (map spec trajectory))))

(defn until-op
  "Temporal operator U (until).
   Catalog: V6. Pattern: FUNCTION TRANSFORMER.
   (until-op spec1 spec2): spec1 holds until spec2 becomes true.
   Robustness: max_j min(spec2(t_j), min_{i<j} spec1(t_i))"
  [spec1 spec2]
  (fn [trajectory]
    (let [n (count trajectory)]
      (apply max
             (for [j (range n)]
               (min (spec2 (nth trajectory j))
                    (if (zero? j) Double/MAX_VALUE
                        (apply min (map #(spec1 (nth trajectory %))
                                        (range j))))))))))

(defn spec-and
  "Conjunction: phi1 /\\ phi2. Robustness: min."
  [spec1 spec2]
  (fn [x] (min (spec1 x) (spec2 x))))

(defn spec-or
  "Disjunction: phi1 \\/ phi2. Robustness: max."
  [spec1 spec2]
  (fn [x] (max (spec1 x) (spec2 x))))

(defn spec-not
  "Negation: -phi. Robustness: negated."
  [spec]
  (fn [x] (- (spec x))))

(defn bounded-always
  "Bounded globally: [][a,b] phi."
  [spec a b]
  (fn [trajectory]
    (let [segment (subvec (vec trajectory) a (min (inc b) (count trajectory)))]
      (apply min (map spec segment)))))

(defn bounded-eventually
  "Bounded eventually: <>[a,b] phi."
  [spec a b]
  (fn [trajectory]
    (let [segment (subvec (vec trajectory) a (min (inc b) (count trajectory)))]
      (apply max (map spec segment)))))

;; =============================================================
;; System Simulation
;; =============================================================

(defn make-system
  "Create a dynamical system.
   dynamics: (fn [state disturbance] -> state')
   initial-state: the starting state"
  [dynamics initial-state]
  {:dynamics dynamics :initial-state initial-state})

(defn rollout
  "Simulate a system trajectory given a sequence of disturbances.
   Pattern: reduce over disturbances, collecting trajectory.
   Returns a vector of states."
  [system disturbances]
  (let [{:keys [dynamics initial-state]} system]
    (reduce (fn [trajectory d]
              (conj trajectory (dynamics (peek trajectory) d)))
            [initial-state]
            disturbances)))

;; =============================================================
;; REACHABILITY SPECIFICATION
;; =============================================================

(defn reachability-spec
  "Create a reachability specification: trajectory must stay in safe set.
   Catalog: V8. Pattern: PARAMETERIZE.

   {:lo R, :hi R} -> (Trajectory -> R)

   safe-set has :lo and :hi bounds. Robustness = min distance to boundary.

   Example:
     (def spec (reachability-spec {:lo -5.0 :hi 5.0}))
     (spec [0.0 1.0 2.0]) ;=> 3.0"
  [safe-set]
  (let [{:keys [lo hi]} safe-set]
    (always (fn [x]
              (let [x (if (number? x) x (first x))]
                (min (- x lo) (- hi x)))))))

;; =============================================================
;; CROSS-VALIDATION
;; =============================================================

(defn cross-validation
  "K-fold cross-validation for model assessment.
   Catalog: V4. Pattern: MAP-OVER + REDUCE.

   (Data -> Model) -> (Model -> Data -> R) -> Data -> {:keys [k]}
   -> {:scores [R], :mean R}

   fit-fn: training data -> model
   eval-fn: model -> test data -> score
   data: full dataset

   Example:
     (cross-validation fit-fn eval-fn data :k 5)
     ;=> {:scores [0.1 0.2 ...] :mean 0.15}"
  [fit-fn eval-fn data & {:keys [k] :or {k 5}}]
  (let [n        (count data)
        fold-size (int (Math/ceil (/ n k)))
        folds    (mapv vec (partition-all fold-size data))
        scores   (mapv (fn [i]
                         (let [test-fold  (nth folds i)
                               train-data (vec (apply concat (keep-indexed
                                                               (fn [j fold] (when (not= i j) fold))
                                                               folds)))
                               model      (fit-fn train-data)]
                           (eval-fn model test-fold)))
                       (range (count folds)))]
    {:scores scores
     :mean   (/ (reduce + scores) (double (count scores)))}))
