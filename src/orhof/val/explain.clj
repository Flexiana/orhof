(ns orhof.val.explain
  "Explainability and monitoring: sensitivity analysis, Shapley values,
   runtime monitoring.
   Pattern: SENSITIVITY ANALYZER + FUNCTION TRANSFORMER."
  (:require [orhof.core :as c]
            [orhof.val.spec :as spec]))

(defn robustness-sensitivity
  "Compute sensitivity of robustness to each disturbance dimension.
   Catalog: V53-V55. Pattern: SENSITIVITY ANALYZER.
   Uses finite differences on the robustness function."
  [system spec-fn disturbance dim & {:keys [h] :or {h 0.01}}]
  (let [n            (count disturbance)
        nominal-ds   (map vec (partition dim disturbance))
        nominal-traj (spec/rollout system nominal-ds)
        nominal-rob  (spec-fn nominal-traj)]
    (mapv (fn [i]
            (let [d+ (assoc disturbance i (+ (nth disturbance i) h))
                  ds+ (map vec (partition dim d+))
                  traj+ (spec/rollout system ds+)
                  rob+ (spec-fn traj+)]
              (/ (- rob+ nominal-rob) h)))
          (range n))))

(defn shapley-values
  "Approximate Shapley values for failure attribution.
   Catalog: V55. Pattern: SENSITIVITY ANALYZER + MAP-OVER + REDUCE.
   Estimates how much each disturbance dimension contributes to failure."
  [system spec-fn disturbance dim & {:keys [n-samples] :or {n-samples 100}}]
  (let [n        (count disturbance)
        baseline (vec (repeat n 0.0))]
    (mapv (fn [i]
            (let [contributions
                  (repeatedly n-samples
                              (fn []
                                (let [coalition (mapv (fn [j]
                                                       (if (or (= j i) (< (rand) 0.5))
                                                         false true))
                                                     (range n))
                                      d-without (mapv (fn [j]
                                                        (if (nth coalition j)
                                                          (nth disturbance j)
                                                          (nth baseline j)))
                                                      (range n))
                                      d-with    (assoc d-without i (nth disturbance i))
                                      traj-without (spec/rollout system (map vec (partition dim d-without)))
                                      traj-with    (spec/rollout system (map vec (partition dim d-with)))
                                      rob-without  (spec-fn traj-without)
                                      rob-with     (spec-fn traj-with)]
                                  (- rob-with rob-without))))]
              (/ (reduce + contributions) (double n-samples))))
          (range n))))

(defn monitor-step
  "Pure monitor step function: (state, observation) -> state'.
   Catalog: V63. Pattern: FUNCTION TRANSFORMER.
   State is {:buffer [...] :verdict :safe/:unsafe/:unknown}."
  [spec-fn window-size]
  (fn [{:keys [buffer] :or {buffer []} :as state} observation]
    (let [buffer' (let [b (conj (or buffer []) observation)]
                    (if (> (count b) window-size)
                      (vec (drop 1 b))
                      b))
          rob     (spec-fn buffer')]
      {:buffer      buffer'
       :verdict     (cond (pos? rob) :safe
                          (neg? rob) :unsafe
                          :else :unknown)
       :robustness  rob
       :buffer-size (count buffer')})))

(defn make-monitor
  "Create a stateful runtime monitor from a specification.
   Returns (fn [observation] -> {:verdict :safe/:unsafe/:unknown, :robustness R}).
   Uses internal state to buffer observations."
  [spec-fn window-size]
  (let [state (atom {:buffer []})
        step  (monitor-step spec-fn window-size)]
    (fn [observation]
      (swap! state step observation)
      @state)))

;; =============================================================
;; PERMUTATION FEATURE IMPORTANCE
;; =============================================================

(defn permutation-importance
  "Permutation feature importance.
   Catalog: V54. Pattern: MAP-OVER + REDUCE.

   (X -> R) -> [X] -> [R] -> [R]

   For each feature, shuffles it across the dataset and measures
   the increase in prediction error.

   Example:
     (permutation-importance model data labels)
     ;=> [0.1 0.5] (feature 1 more important)"
  [model data labels & {:keys [n-repeats] :or {n-repeats 5}}]
  (let [n-features (count (first data))
        base-error (/ (reduce + (map (fn [x y] (Math/abs (- (model x) y))) data labels))
                      (double (count data)))]
    (mapv (fn [feat-idx]
            (let [errors (repeatedly n-repeats
                           (fn []
                             (let [shuffled-col (shuffle (mapv #(nth % feat-idx) data))
                                   permuted-data (mapv (fn [i]
                                                         (assoc (vec (nth data i)) feat-idx (nth shuffled-col i)))
                                                       (range (count data)))]
                               (/ (reduce + (map (fn [x y] (Math/abs (- (model x) y))) permuted-data labels))
                                  (double (count data))))))]
              (- (/ (reduce + errors) (double n-repeats)) base-error)))
          (range n-features))))

;; =============================================================
;; COUNTERFACTUAL EXPLANATION
;; =============================================================

(defn counterfactual
  "Find a counterfactual explanation: closest input with desired output.
   Catalog: V57. Pattern: TRANSFORM-PROBLEM + ITERATE.

   (X -> Class) -> X -> Class -> Opts -> {:x X}

   Minimizes distance to original while achieving desired class.

   Example:
     (counterfactual classifier [-1.0] :positive :max-iter 100)
     ;=> {:x [0.01]}"
  [model x0 desired-class & {:keys [max-iter lr lambda]
                                :or   {max-iter 200 lr 0.1 lambda 10.0}}]
  (let [dim (count x0)]
    (reduce (fn [{:keys [x]} _]
              (let [;; If we already have desired class, just minimize distance to x0
                    ;; Otherwise, take random step toward desired class
                    at-desired? (= (model x) desired-class)
                    x-new (if at-desired?
                             ;; Move toward x0 (minimize distance)
                             (mapv (fn [xi x0i] (+ xi (* lr (- x0i xi)))) x x0)
                             ;; Random perturbation + gradient toward boundary
                             (let [perturbed (mapv (fn [i]
                                                     (let [h 0.01
                                                           x+ (update (vec x) i + h)
                                                           x- (update (vec x) i - h)
                                                           pen+ (if (= (model x+) desired-class) 0.0 lambda)
                                                           pen- (if (= (model x-) desired-class) 0.0 lambda)]
                                                       (/ (- pen+ pen-) (* 2 h))))
                                                   (range dim))
                                   all-zero? (every? #(< (Math/abs %) 1e-10) perturbed)
                                   ;; If gradient is zero, use random exploration
                                   direction (if all-zero?
                                               (mapv (fn [_] (- (rand) 0.5)) (range dim))
                                               perturbed)]
                               (mapv (fn [xi gi] (- xi (* lr gi))) x direction)))]
                {:x x-new :class (model x-new)}))
            {:x x0 :class (model x0)}
            (range max-iter))))

;; =============================================================
;; CONFORMAL PREDICTION
;; =============================================================

(defn conformal-predictor
  "Create a conformal prediction set.
   Catalog: V60. Pattern: PARAMETERIZE + REDUCE.

   (X -> R) -> [{:x X :y R}] -> {:keys [alpha]} -> (X -> {:lo R :hi R})

   Calibrates nonconformity scores on calibration data, then
   returns prediction intervals at confidence level 1-alpha.

   Example:
     (def pred (conformal-predictor model cal-data :alpha 0.1))
     (pred 5.0) ;=> {:lo 4.8 :hi 5.2}"
  [model calibration-data & {:keys [alpha] :or {alpha 0.1}}]
  (let [;; Compute nonconformity scores: |y - model(x)|
        scores (sort (mapv (fn [{:keys [x y]}] (Math/abs (- y (model x)))) calibration-data))
        n      (count scores)
        ;; Quantile at level (1 - alpha)(1 + 1/n)
        q-level (min 1.0 (* (- 1 alpha) (+ 1 (/ 1.0 n))))
        q-idx   (min (dec n) (int (Math/ceil (* q-level n))))
        q       (nth scores q-idx)]
    (fn [x]
      (let [pred (model x)]
        {:lo (- pred q) :hi (+ pred q) :prediction pred}))))

;; =============================================================
;; ENSEMBLE DISAGREEMENT
;; =============================================================

(defn ensemble-disagreement
  "Measure uncertainty via ensemble model disagreement.
   Catalog: V61. Pattern: MAP-OVER + REDUCE.

   [(X -> R)] -> X -> {:mean R, :variance R}

   Example:
     (ensemble-disagreement [model1 model2 model3] [1.0 2.0])
     ;=> {:mean 5.0 :variance 0.5}"
  [models x]
  (let [predictions (mapv #(% x) models)
        mean (/ (reduce + predictions) (double (count predictions)))
        variance (/ (reduce + (map (fn [p] (let [d (- p mean)] (* d d))) predictions))
                    (double (count predictions)))]
    {:mean mean :variance variance}))
