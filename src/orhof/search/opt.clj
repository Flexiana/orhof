(ns orhof.search.opt
  "Global and discrete optimization on orhof.search: Lipschitz branch and
   bound, DIRECT, the simplex method and integer programming.
   Pattern: TREE/GRAPH EXPLORER — each divides a region and keeps the best
   point found; `search` folds the pieces while a strategy picks the next one
   to divide. The simplex method is the one-child case: iterate + converge.

   Everything minimizes, like the rest of orhof."
  (:require [orhof.search :refer [search best-first converge]]))

;; =============================================================
;; LIPSCHITZ BRANCH AND BOUND
;; =============================================================

(defn lipschitz-min
  "Global minimum of f on [a, b] to within tol, given a Lipschitz constant L
   (|f(x) - f(y)| <= L |x - y|).
   Pattern: TREE/GRAPH EXPLORER (search + best-first). An interval is a node;
   its lower bound is f(midpoint) - L * half-width. An interval whose bound
   cannot beat the best point found is not divided.

   (R -> R) -> R -> R -> R -> {:keys [tol]} -> {:x R, :value R, :evals Int}

   Example:
     (lipschitz-min #(Math/sin %) 0.0 10.0 1.0)
     ;=> {:x ~4.712389 :value ~-1.0 :evals 6643}"
  [f a b L & {:keys [tol] :or {tol 1e-6}}]
  (let [cell (fn [lo hi]
               (let [x (/ (+ lo hi) 2.0), y (f x)]
                 {:lo lo :hi hi :x x :value y :bound (- y (* L (/ (- hi lo) 2.0)))}))
        {:keys [best evals]}
        (search (best-first :bound)
                (fn [{:keys [best evals]} cell]
                  {:best (if (< (:value cell) (:value best)) cell best) :evals (inc evals)})
                {:best {:value Double/POSITIVE_INFINITY} :evals 0}
                (fn [{:keys [best]} {:keys [lo hi x bound]}]
                  (when (< bound (- (:value best) tol))
                    [(cell lo x) (cell x hi)]))
                (cell a b))]
    {:x (:x best) :value (:value best) :evals evals}))

;; =============================================================
;; DIRECT (DIviding RECTangles)
;; =============================================================

(defn- potentially-optimal
  "Jones, Perttunen and Stuckman's rule, applied to the best rectangle of each
   size: rectangle j qualifies if some rate of change K > 0 makes
   y_j - K size_j the lowest bound of all, and lower than y_min - eps |y_min|."
  [rectangles eps]
  (let [best  (vals (reduce (fn [m r] (update m (:size r) #(if (and % (<= (:y %) (:y r))) % r)))
                            (sorted-map) rectangles))
        y-min (reduce min (map :y best))]
    (filter (fn [{size :size y :y}]
              (let [at-least (reduce max (/ (+ (- y y-min) (* eps (abs y-min))) size)
                                     (for [r best :when (< (:size r) size)]
                                       (/ (- y (:y r)) (- size (:size r)))))
                    at-most  (reduce min Double/POSITIVE_INFINITY
                                     (for [r best :when (> (:size r) size)]
                                       (/ (- (:y r) y) (- (:size r) size))))]
                (and (<= at-least at-most) (pos? at-most))))
            best)))

(defn potentially-optimal-first
  "DIRECT's selection rule as a strategy for orhof.search: take every
   potentially optimal rectangle of the current set, then select again.
   Catalog: O37. Pattern: TREE/GRAPH EXPLORER (a strategy is one function).

   R -> Strategy

   Rectangles are maps with :size (centre-to-corner distance) and :y (the
   value at the centre).

   Example:
     (search (potentially-optimal-first 1e-4) rf init expand root)"
  [eps]
  (fn
    ([] {:rectangles #{} :selected ()})
    ([{:keys [rectangles selected]}]
     (when-let [[r & more] (seq (or (seq selected) (potentially-optimal rectangles eps)))]
       [r {:rectangles (disj rectangles r) :selected more}]))
    ([frontier rectangles] (update frontier :rectangles into rectangles))))

(defn direct
  "DIRECT: global minimum of f over a box, without derivatives and without a
   Lipschitz constant. It divides the rectangles that could contain the
   minimum for some rate of change of f (Jones, Perttunen and Stuckman, 1993).
   Catalog: O37. Pattern: TREE/GRAPH EXPLORER (search + its own strategy).

   ([R] -> R) -> [[R] [R]] -> {:keys [eps max-evals]} -> {:x [R], :value R, :evals Int}

   bounds: [lower-bounds upper-bounds]
   Options:
     :max-evals  budget of function evaluations; the search finishes the
                 division in progress, so it may overshoot by a few (default 500)
     :eps        how much better than the best value a rectangle must promise
                 to be, relative to that value (default 1e-4)

   Example:
     (direct (fn [[x y]] (+ (* x x) (* y y))) [[-3.0 -2.0] [5.0 4.0]] :max-evals 200)
     ;=> {:x [~0.001 ~0.001] :value ~4e-6 :evals 201}"
  [f bounds & {:keys [eps max-evals] :or {eps 1e-4 max-evals 500}}]
  (let [[lower upper] bounds
        n      (count lower)
        scale  (fn [c] (mapv (fn [lo hi u] (+ lo (* u (- hi lo)))) lower upper c))
        g      (comp f scale)                                  ; f on the unit cube
        rect   (fn [c y depths]
                 {:c c :y y :depths depths
                  :size (* 0.5 (Math/sqrt (reduce + (map #(Math/pow 3.0 (* -2.0 %)) depths))))})
        thirds (fn [{:keys [c y depths]}]                      ; trisect along the longest sides, best samples first
                 (let [shallow (reduce min depths)
                       delta   (Math/pow 3.0 (- (inc shallow)))
                       probes  (for [i (range n) :when (= shallow (depths i))]
                                 (let [up (update c i + delta), down (update c i - delta)]
                                   {:i i :up up :down down :y-up (g up) :y-down (g down)}))]
                   (loop [[{:keys [i up down y-up y-down]} & more] (sort-by #(min (:y-up %) (:y-down %)) probes)
                          depths depths
                          out    []]
                     (if i
                       (let [depths (update depths i inc)]
                         (recur more depths (conj out (rect up y-up depths) (rect down y-down depths))))
                       (conj out (rect c y depths))))))
        root   (let [c (vec (repeat n 0.5))] (rect c (g c) (vec (repeat n 0))))
        {:keys [best evals]}
        (search (potentially-optimal-first eps)
                (fn [{:keys [best evals]} rectangle]           ; dividing is the step: fold the thirds it makes
                  (let [parts (thirds rectangle)
                        acc   {:best  (apply min-key :y best parts)
                               :evals (+ evals (dec (count parts)))
                               :parts parts}]
                    (if (>= (:evals acc) max-evals) (reduced acc) acc)))
                {:best root :evals 1}
                (fn [acc _] (:parts acc))
                root)]
    {:x (scale (:c best)) :value (:y best) :evals evals}))

;; =============================================================
;; SIMPLEX
;; =============================================================

(defn- pivot [{:keys [rows objective basis] :as tableau} r c]
  (let [row   (let [p ((rows r) c)] (mapv #(/ % p) (rows r)))
        clear (fn [other] (let [k (other c)] (if (zero? k) other (mapv #(- %1 (* k %2)) other row))))]
    (assoc tableau
           :rows (vec (map-indexed (fn [i other] (if (= i r) row (clear other))) rows))
           :objective (clear objective)
           :basis (assoc basis r c))))

(defn- simplex-step
  "From one vertex to the next by Bland's rule, which cannot cycle. A fixed
   point at the optimum, so the simplex method is converge over iterate."
  [{:keys [rows objective basis status] :as tableau}]
  (if-let [c (and (not status)
                  (first (filter #(neg? (objective %)) (range (dec (count objective))))))]
    (let [ratios (for [[i row] (map-indexed vector rows) :when (pos? (row c))]
                   [(/ (peek row) (row c)) (basis i) i])]
      (if (seq ratios)
        (pivot tableau (peek (first (sort ratios))) c)
        (assoc tableau :status :unbounded)))
    tableau))

(defn- solve [tableau] (converge = (iterate simplex-step tableau)))

(defn- priced
  "The tableau with the objective row for maximizing costs . x."
  [{:keys [rows basis] :as tableau} costs]
  (reduce (fn [t [i c]]
            (let [k ((:objective t) c)]
              (if (zero? k) t (update t :objective (fn [o] (mapv #(- %1 (* k %2)) o (rows i)))))))
          (assoc tableau :objective (conj (mapv - costs) 0))
          (map-indexed vector basis)))

(defn- plain
  "Exact arithmetic leaves BigInts behind; give back a long when it fits."
  [x]
  (if (and (instance? clojure.lang.BigInt x) (<= Long/MIN_VALUE x Long/MAX_VALUE)) (long x) x))

(defn- maximize
  "Two-phase tableau simplex for max c . x, A x <= b, x >= 0."
  [c A b]
  (let [n (count c), m (count A)
        negative (filterv #(neg? (b %)) (range m))             ; rows that need an artificial variable
        width    (+ n m (count negative))
        row      (fn [i]                                        ; [A_i | slack | artificial | b_i], sign-flipped if b_i < 0
                   (let [sign (if (neg? (b i)) -1 1)
                         art  (.indexOf ^java.util.List negative i)]
                     (cond-> (-> (into (mapv #(* sign %) (A i)) (repeat (- (inc width) n) 0))
                                 (assoc (+ n i) sign)
                                 (assoc width (* sign (b i))))
                       (>= art 0) (assoc (+ n m art) 1))))
        start    {:rows  (mapv row (range m))
                  :basis (mapv (fn [i]
                                 (let [art (.indexOf ^java.util.List negative i)]
                                   (if (>= art 0) (+ n m art) (+ n i))))
                               (range m))}
        artificial? #(>= % (+ n m))
        ;; phase 1: drive the artificial variables to zero
        phase1   (if (seq negative)
                   (solve (priced start (mapv #(if (artificial? %) -1 0) (range width))))
                   start)]
    (if (and (seq negative) (neg? (peek (:objective phase1))))
      {:status :infeasible}
      (let [;; an artificial variable left in the basis at zero is pivoted out, or its row is redundant
            cleaned (reduce (fn [t i]
                              (if (artificial? ((:basis t) i))
                                (if-let [c (first (filter #(not (zero? (((:rows t) i) %))) (range (+ n m))))]
                                  (pivot (assoc t :objective (vec (repeat (inc width) 0))) i c)
                                  (assoc-in t [:dead i] true))
                                t))
                            (dissoc phase1 :status) (range m))
            live    (remove #(get-in cleaned [:dead %]) (range m))
            trim    (fn [v] (conj (subvec v 0 (+ n m)) (peek v)))
            ;; phase 2: the real objective
            phase2  (solve (priced {:rows  (mapv #(trim ((:rows cleaned) %)) live)
                                    :basis (mapv (:basis cleaned) live)}
                                   (into (vec c) (repeat m 0))))]
        (if (:status phase2)
          {:status :unbounded}
          (let [x (reduce (fn [x [i c]] (if (< c n) (assoc x c (peek ((:rows phase2) i))) x))
                          (vec (repeat n 0)) (map-indexed vector (:basis phase2)))]
            {:status :optimal :x (mapv plain x) :value (plain (peek (:objective phase2)))}))))))

(defn linear-program
  "Minimizes c . x subject to A x <= b, x >= 0, by the two-phase tableau
   simplex method with Bland's rule.
   Catalog: O54. Pattern: ITERATE (converge over iterate: a vertex is a fixed
   point of the pivot step when it is optimal).

   [R] -> [[R]] -> [R] -> {:status :optimal, :x [R], :value R}
                        | {:status :infeasible} | {:status :unbounded}

   b may be negative, so `>=` rows (negate them) and `=` rows (a pair) can be
   stated. On integers and ratios the arithmetic is exact: the result is a
   vertex, not an approximation of one. Meant for small problems.

   Example:
     ;; minimize -3x - 5y  subject to  x <= 4, 2y <= 12, 3x + 2y <= 18
     (linear-program [-3 -5] [[1 0] [0 2] [3 2]] [4 12 18])
     ;=> {:status :optimal :x [2 6] :value -36}"
  [c A b]
  (let [result (maximize (mapv - c) (mapv vec A) (vec b))]
    (cond-> result
      (= :optimal (:status result)) (update :value -))))

;; =============================================================
;; INTEGER PROGRAMMING
;; =============================================================

(defn integer-program
  "Minimizes c . x subject to A x <= b, x >= 0 and x integer, by branch and
   bound: the bound of a node is its linear relaxation, and a fractional x_j
   splits it into x_j <= floor and x_j >= floor + 1.
   Catalog: O72. Pattern: TREE/GRAPH EXPLORER (search + best-first).

   [Int] -> [[Int]] -> [Int] -> {:status :optimal, :x [Int], :value Int}
                              | {:status :infeasible} | {:status :unbounded}

   Give integers or ratios, not doubles: the test for an integral x is exact.
   The feasible region must be bounded.

   Example:
     ;; a knapsack: maximize 5x + 4y  subject to  6x + 4y <= 24, x + 2y <= 6
     (integer-program [-5 -4] [[6 4] [1 2]] [24 6])
     ;=> {:status :optimal :x [4 0] :value -20}"
  [c A b]
  (let [A      (mapv vec A)
        b      (vec b)
        n      (count c)
        unit   (fn [j k] (assoc (vec (repeat n 0)) j k))
        relax  (fn [cuts]
                 (assoc (linear-program c (into A (map first cuts)) (into b (map second cuts)))
                        :cuts cuts))
        split  (fn [{:keys [x cuts]}]
                 (when-let [j (first (remove #(integer? (x %)) (range n)))]
                   (let [floor (- (x j) (mod (x j) 1))]
                     (filter #(= :optimal (:status %))
                             [(relax (conj cuts [(unit j 1) floor]))                  ; x_j <= floor
                              (relax (conj cuts [(unit j -1) (- (inc floor))]))]))))  ; x_j >= floor + 1
        better (fn [value best] (or (nil? (:value best)) (< value (:value best))))
        root   (relax [])]
    (if (not= :optimal (:status root))
      (dissoc root :cuts)
      (-> (search (best-first :value)
                  (fn [best {:keys [x value] :as node}]
                    (if (and (every? integer? x) (better value best)) node best))
                  {:status :infeasible}
                  (fn [best {:keys [value] :as node}]
                    (when (better value best) (split node)))
                  root)
          (dissoc :cuts)))))
