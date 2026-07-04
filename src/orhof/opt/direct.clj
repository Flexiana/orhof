(ns orhof.opt.direct
  "Derivative-free (direct) optimization methods.
   These methods do not use gradients or Hessians.
   Pattern: ITERATE with geometric operations on simplices/patterns."
  (:require [orhof.core :as c]
            [orhof.opt.bracketing :as brk]))

;; =============================================================
;; NELDER-MEAD (existing, moved from old orhof.optimize)
;; =============================================================

(defn nelder-mead
  "Nelder-Mead simplex method. Derivative-free optimization.
   Catalog: O36. Pattern: ITERATE + DELEGATE."
  [f x0 & {:keys [tol max-iter alpha-r gamma-e rho-c sigma-s]
            :or   {tol 1e-8 max-iter 1000
                   alpha-r 1.0 gamma-e 2.0 rho-c 0.5 sigma-s 0.5}}]
  (let [n       (count x0)
        simplex (vec (cons x0
                       (map (fn [i]
                              (update x0 i + (if (zero? (nth x0 i)) 0.00025
                                                 (* 0.05 (nth x0 i)))))
                            (range n))))
        step    (fn [simplex]
                  (let [scored       (sort-by :value (map (fn [x] {:x x :value (f x)}) simplex))
                        best         (first scored)
                        worst        (last scored)
                        second-worst (nth scored (dec (count scored)))
                        centroid     (c/v* (/ 1.0 n) (reduce c/v+ (map :x (butlast scored))))
                        xr (c/v+ centroid (c/v* alpha-r (c/v- centroid (:x worst))))
                        fr (f xr)]
                    (cond
                      (< fr (:value best))
                      (let [xe (c/v+ centroid (c/v* gamma-e (c/v- xr centroid)))
                            fe (f xe)]
                        (conj (vec (map :x (butlast scored)))
                              (if (< fe fr) xe xr)))

                      (< fr (:value second-worst))
                      (conj (vec (map :x (butlast scored))) xr)

                      :else
                      (let [xc (c/v+ centroid (c/v* rho-c (c/v- (:x worst) centroid)))
                            fc (f xc)]
                        (if (< fc (:value worst))
                          (conj (vec (map :x (butlast scored))) xc)
                          (vec (cons (:x best)
                                     (map (fn [s]
                                            (c/v+ (:x best)
                                                   (c/v* sigma-s (c/v- (:x s) (:x best)))))
                                          (rest scored)))))))))
        converged? (fn [simplex]
                     (let [vals (map f simplex)]
                       (< (- (apply max vals) (apply min vals)) tol)))]
    (let [result (c/iterative-solve
                  {:init-state {:simplex simplex :prev-value Double/MAX_VALUE :value (f x0)}
                   :max-iter   max-iter
                   :converged? (fn [state] (converged? (:simplex state)))
                   :step-fn    (fn [state]
                                 (let [new-simplex (step (:simplex state))
                                       best-x (apply min-key f new-simplex)]
                                   {:simplex    new-simplex
                                    :x          best-x
                                    :value      (f best-x)
                                    :prev-value (:value state)}))})]
      result)))

;; =============================================================
;; CYCLIC COORDINATE SEARCH
;; =============================================================

(defn cyclic-coordinate-search
  "Cyclic coordinate search — minimize along each axis in turn.
   Catalog: O32. Pattern: ITERATE (reduce over dimensions).
   Each dimension is optimized by 1D golden section search."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-6 max-iter 100}}]
  (c/iterative-solve
   {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE}
    :max-iter   max-iter
    :converged? (c/value-converged? tol)
    :step-fn    (fn [state]
                  ;; One full cycle: minimize along each coordinate axis
                  (let [x (reduce (fn [x dim]
                                    (let [f-1d (fn [t] (f (assoc x dim t)))
                                          xi   (nth x dim)
                                          t*   (brk/golden-section-search f-1d
                                                  (- xi 5.0) (+ xi 5.0) :tol 1e-8)]
                                      (assoc x dim t*)))
                                  (:x state)
                                  (range (count (:x state))))]
                    {:x x :value (f x) :prev-value (:value state)}))}))

;; =============================================================
;; POWELL'S METHOD
;; =============================================================

(defn powells-method
  "Powell's conjugate direction method.
   Catalog: O33. Pattern: ITERATE + DELEGATE.
   Like cyclic coordinate but updates search directions after
   each full cycle to build conjugate directions."
  [f x0 & {:keys [tol max-iter] :or {tol 1e-6 max-iter 100}}]
  (let [n    (count x0)
        ;; Initial directions: coordinate axes
        init-dirs (vec (for [i (range n)]
                         (assoc (c/v-zero n) i 1.0)))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE
                   :dirs init-dirs}
      :max-iter   max-iter
      :converged? (c/value-converged? tol)
      :step-fn    (fn [state]
                    (let [{:keys [x dirs]} state
                          ;; Minimize along each direction
                          [x-new _]
                          (reduce (fn [[x best-dim-idx] [i dir]]
                                    (let [f-1d (fn [t] (f (c/v+ x (c/v* t dir))))
                                          t*   (brk/golden-section-search f-1d -5.0 5.0 :tol 1e-8)
                                          x'   (c/v+ x (c/v* t* dir))]
                                      [x' i]))
                                  [x 0]
                                  (map-indexed vector dirs))
                          ;; Update directions: replace first with (x_new - x_old)
                          delta    (c/v- x-new x)
                          norm-d   (c/vnorm delta)
                          new-dirs (if (> norm-d 1e-10)
                                     (conj (vec (rest dirs)) (c/v* (/ 1.0 norm-d) delta))
                                     dirs)]
                      {:x x-new :value (f x-new) :prev-value (:value state)
                       :dirs new-dirs}))})))

;; =============================================================
;; HOOKE-JEEVES
;; =============================================================

(defn hooke-jeeves
  "Hooke-Jeeves pattern search.
   Catalog: O34. Pattern: ITERATE + DELEGATE.
   Two phases per iteration: exploratory moves (probe each axis)
   and pattern moves (extrapolate in the promising direction)."
  [f x0 & {:keys [step-size step-reduction tol max-iter]
            :or   {step-size 1.0 step-reduction 0.5 tol 1e-8 max-iter 1000}}]
  (let [n (count x0)
        ;; Exploratory move: try +/- step along each axis
        explore (fn [x h]
                  (reduce (fn [x dim]
                            (let [x+ (update x dim + h)
                                  x- (update x dim - h)
                                  fx (f x) f+ (f x+) f- (f x-)]
                              (cond (< f+ fx) x+
                                    (< f- fx) x-
                                    :else x)))
                          x
                          (range n)))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE
                   :h step-size :base x0}
      :max-iter   max-iter
      :converged? (fn [state] (< (:h state) tol))
      :step-fn    (fn [state]
                    (let [{:keys [x h base]} state
                          ;; Pattern move: extrapolate from base
                          x-pattern (c/v+ x (c/v- x base))
                          ;; Explore from pattern point
                          x-explored (explore x-pattern h)
                          v-explored (f x-explored)]
                      (if (< v-explored (:value state))
                        ;; Accept pattern + exploration
                        {:x x-explored :value v-explored :prev-value (:value state)
                         :h h :base x}
                        ;; Pattern failed: explore from current, maybe shrink
                        (let [x-explored2 (explore x h)
                              v-explored2 (f x-explored2)]
                          (if (< v-explored2 (:value state))
                            {:x x-explored2 :value v-explored2 :prev-value (:value state)
                             :h h :base x}
                            ;; No improvement: shrink step
                            {:x x :value (:value state) :prev-value (:value state)
                             :h (* h step-reduction) :base x})))))})))

;; =============================================================
;; GENERALIZED PATTERN SEARCH
;; =============================================================

(defn generalized-pattern-search
  "Generalized pattern search (GPS).
   Catalog: O35. Pattern: ITERATE + DELEGATE + PARAMETERIZE.
   Polls a positive spanning set of directions. If no improvement,
   reduces the mesh size."
  [f x0 & {:keys [step-size step-reduction tol max-iter]
            :or   {step-size 1.0 step-reduction 0.5 tol 1e-8 max-iter 1000}}]
  (let [n (count x0)
        ;; Positive spanning set: +/- each coordinate axis (2n directions)
        directions (vec (concat
                         (for [i (range n)] (assoc (c/v-zero n) i 1.0))
                         (for [i (range n)] (assoc (c/v-zero n) i -1.0))))]
    (c/iterative-solve
     {:init-state {:x x0 :value (f x0) :prev-value Double/MAX_VALUE
                   :h step-size}
      :max-iter   max-iter
      :converged? (fn [state] (< (:h state) tol))
      :step-fn    (fn [state]
                    (let [{:keys [x value h]} state
                          ;; Poll all directions, take first improvement
                          improvement
                          (reduce (fn [_ dir]
                                    (let [x' (c/v+ x (c/v* h dir))
                                          v' (f x')]
                                      (if (< v' value)
                                        (reduced {:x x' :value v'})
                                        nil)))
                                  nil
                                  directions)]
                      (if improvement
                        ;; Found improvement: accept, keep or increase step
                        {:x (:x improvement) :value (:value improvement)
                         :prev-value value :h h}
                        ;; No improvement: shrink mesh
                        {:x x :value value :prev-value value
                         :h (* h step-reduction)})))})))

;; =============================================================
;; DIRECT (DIviding RECTangles)
;; =============================================================

(defn direct-search
  "DIRECT — Dividing Rectangles method.
   Catalog: O37. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Partitions the search space into hyperrectangles, evaluates
   centers, and identifies potentially optimal rectangles to divide.
   bounds: [lower-bounds upper-bounds] vectors."
  [f bounds & {:keys [max-iter max-evals]
                :or   {max-iter 100 max-evals 1000}}]
  (let [[lo hi] bounds
        n       (count lo)
        ;; Rectangle: {:center, :size (half-widths), :value}
        init-center (mapv (fn [l h] (/ (+ l h) 2.0)) lo hi)
        init-size   (mapv (fn [l h] (/ (- h l) 2.0)) lo hi)
        init-rect   {:center init-center :size init-size :value (f init-center)}
        ;; Split a rectangle along its longest dimension
        split-rect  (fn [rect]
                      (let [{:keys [center size value]} rect
                            ;; Find longest dimension
                            dim (apply max-key #(nth size %) (range n))
                            delta (/ (nth size dim) 3.0)
                            ;; Create three sub-rectangles
                            new-size (assoc size dim delta)
                            c-left  (update center dim - (* 2 delta))
                            c-right (update center dim + (* 2 delta))]
                        [{:center c-left  :size new-size :value (f c-left)}
                         {:center center  :size new-size :value value}
                         {:center c-right :size new-size :value (f c-right)}]))]
    ;; Iterate: split the best rectangles each round
    (reduce (fn [{:keys [rects best-x best-val evals]} _iter]
              (if (>= evals max-evals)
                (reduced {:x best-x :value best-val :evals evals :rects (count rects)})
                ;; Find potentially optimal rectangles (simplified: split the best)
                (let [best-rect (apply min-key :value rects)
                      rest-rects (vec (remove #{best-rect} rects))
                      new-rects (split-rect best-rect)
                      all-rects (into rest-rects new-rects)
                      new-best (apply min-key :value all-rects)]
                  {:rects   all-rects
                   :x       (:center new-best)
                   :value   (:value new-best)
                   :best-x  (:center new-best)
                   :best-val (:value new-best)
                   :evals   (+ evals 2)})))
            {:rects [init-rect] :x init-center :value (:value init-rect)
             :best-x init-center :best-val (:value init-rect) :evals 1}
            (range max-iter))))
