(ns orhof.opt.population
  "Population-based optimization methods.
   Pattern: ITERATE over generations, each generation applies
   evaluate -> select -> recombine -> mutate pipeline."
  (:require [orhof.core :as c]))

(defn cross-entropy-method
  "Cross-entropy method for optimization.
   Maintains a Gaussian distribution, samples, selects elite, refits.
   Catalog: O41. Pattern: ITERATE + REDUCE + MAP-OVER."
  [f dim & {:keys [n-samples n-elite max-iter tol]
            :or   {n-samples 100 n-elite 10 max-iter 100 tol 1e-8}}]
  (c/iterative-solve
   {:init-state {:mu    (vec (repeat dim 0.0))
                 :sigma (vec (repeat dim 1.0))
                 :value Double/MAX_VALUE
                 :prev-value Double/MAX_VALUE
                 :iter  0}
    :max-iter   max-iter
    :converged? (fn [s] (< (apply max (:sigma s)) tol))
    :step-fn    (fn [state]
                  (let [{:keys [mu sigma]} state
                        samples (repeatedly n-samples
                                            (fn [] (mapv (fn [m s]
                                                           (+ m (* s (- (rand) 0.5) 2.0)))
                                                         mu sigma)))
                        scored  (sort-by :value (map (fn [x] {:x x :value (f x)}) samples))
                        elite   (take n-elite scored)
                        elite-x (map :x elite)
                        new-mu  (mapv (fn [j]
                                        (/ (reduce + (map #(nth % j) elite-x))
                                           (double n-elite)))
                                      (range dim))
                        new-sigma (mapv (fn [j]
                                          (let [m (nth new-mu j)]
                                            (Math/sqrt
                                             (/ (reduce + (map #(let [d (- (nth % j) m)]
                                                                  (* d d))
                                                               elite-x))
                                                (double n-elite)))))
                                        (range dim))]
                    {:mu         new-mu
                     :sigma      new-sigma
                     :value      (:value (first elite))
                     :x          (:x (first elite))
                     :prev-value (:value state)
                     :iter       (inc (:iter state))}))}))

(defn genetic-algorithm
  "Genetic algorithm with pluggable operators.
   Catalog: O43. Pattern: ITERATE + DELEGATE + MAP-OVER.

   fitness-fn:   individual -> score (lower is better)
   select-fn:    population -> parent
   crossover-fn: parent1 parent2 -> child
   mutate-fn:    individual -> individual"
  [fitness-fn dim & {:keys [pop-size max-iter select-fn crossover-fn mutate-fn]
                     :or   {pop-size 50 max-iter 200}}]
  (let [select-fn    (or select-fn
                         (fn [pop]
                           (let [tournament (repeatedly 3 #(rand-nth pop))]
                             (apply min-key :value tournament))))
        crossover-fn (or crossover-fn
                         (fn [p1 p2]
                           (mapv (fn [a b] (if (< (rand) 0.5) a b))
                                 (:x p1) (:x p2))))
        mutate-fn    (or mutate-fn
                         (fn [x] (mapv #(if (< (rand) 0.1)
                                          (+ % (* 0.1 (- (rand) 0.5)))
                                          %)
                                       x)))
        init-pop     (vec (repeatedly pop-size
                                      (fn [] (let [x (mapv (fn [_] (- (rand 10) 5)) (range dim))]
                                               {:x x :value (fitness-fn x)}))))]
    (c/iterative-solve
     {:init-state {:population init-pop
                   :best       (apply min-key :value init-pop)
                   :value      (:value (apply min-key :value init-pop))
                   :prev-value Double/MAX_VALUE
                   :iter       0}
      :max-iter   max-iter
      :converged? (c/value-converged? 1e-10)
      :step-fn    (fn [state]
                    (let [pop     (:population state)
                          new-pop (vec (repeatedly pop-size
                                                   (fn []
                                                     (let [p1 (select-fn pop)
                                                           p2 (select-fn pop)
                                                           child-x (mutate-fn (crossover-fn p1 p2))]
                                                       {:x child-x :value (fitness-fn child-x)}))))
                          best (apply min-key :value (cons (:best state) new-pop))]
                      {:population new-pop
                       :best       best
                       :x          (:x best)
                       :value      (:value best)
                       :prev-value (:value state)
                       :iter       (inc (:iter state))}))})))

(defn particle-swarm
  "Particle swarm optimization.
   Catalog: O45. Pattern: ITERATE + MAP-OVER + REDUCE."
  [f dim & {:keys [n-particles max-iter w c1 c2]
            :or   {n-particles 30 max-iter 200 w 0.7 c1 1.5 c2 1.5}}]
  (let [init-particles (vec (repeatedly n-particles
                                        (fn []
                                          (let [x (mapv (fn [_] (- (rand 10) 5)) (range dim))
                                                v (mapv (fn [_] (* 0.1 (- (rand) 0.5))) (range dim))]
                                            {:x x :v v :value (f x)
                                             :best-x x :best-value (f x)}))))
        global-best    (apply min-key :best-value init-particles)]
    (c/iterative-solve
     {:init-state {:particles   init-particles
                   :global-best global-best
                   :x           (:x global-best)
                   :value       (:best-value global-best)
                   :prev-value  Double/MAX_VALUE
                   :iter        0}
      :max-iter   max-iter
      :converged? (c/value-converged? 1e-10)
      :step-fn    (fn [state]
                    (let [gb (:global-best state)
                          new-particles
                          (mapv (fn [p]
                                  (let [r1 (rand) r2 (rand)
                                        v-new (c/v+ (c/v* w (:v p))
                                                     (c/v+ (c/v* (* c1 r1) (c/v- (:best-x p) (:x p)))
                                                            (c/v* (* c2 r2) (c/v- (:x gb) (:x p)))))
                                        x-new (c/v+ (:x p) v-new)
                                        val   (f x-new)
                                        [bx bv] (if (< val (:best-value p))
                                                  [x-new val]
                                                  [(:best-x p) (:best-value p)])]
                                    {:x x-new :v v-new :value val
                                     :best-x bx :best-value bv}))
                                (:particles state))
                          new-gb (apply min-key :best-value (cons gb new-particles))]
                      {:particles   new-particles
                       :global-best new-gb
                       :x           (:x new-gb)
                       :value       (:best-value new-gb)
                       :prev-value  (:value state)
                       :iter        (inc (:iter state))}))})))

(defn differential-evolution
  "Differential evolution.
   Catalog: O44. Pattern: ITERATE + MAP-OVER."
  [f dim & {:keys [pop-size max-iter F CR]
            :or   {pop-size 50 max-iter 200 F 0.8 CR 0.9}}]
  (let [init-pop (vec (repeatedly pop-size
                                  (fn [] (mapv (fn [_] (- (rand 10) 5)) (range dim)))))]
    (c/iterative-solve
     {:init-state {:population (mapv (fn [x] {:x x :value (f x)}) init-pop)
                   :value      (apply min (map f init-pop))
                   :prev-value Double/MAX_VALUE
                   :iter       0}
      :max-iter   max-iter
      :converged? (c/value-converged? 1e-10)
      :step-fn    (fn [state]
                    (let [pop (:population state)
                          n   (count pop)
                          new-pop
                          (mapv (fn [i]
                                  (let [target (:x (nth pop i))
                                        [a b c-idx] (repeatedly 3 #(rand-int n))
                                        mutant (c/v+ (:x (nth pop a))
                                                      (c/v* F (c/v- (:x (nth pop b))
                                                                      (:x (nth pop c-idx)))))
                                        trial  (mapv (fn [j]
                                                       (if (or (< (rand) CR) (= j (rand-int dim)))
                                                         (nth mutant j)
                                                         (nth target j)))
                                                     (range dim))
                                        fv     (f trial)]
                                    (if (<= fv (:value (nth pop i)))
                                      {:x trial :value fv}
                                      (nth pop i))))
                                (range n))
                          best (apply min-key :value new-pop)]
                      {:population new-pop
                       :x          (:x best)
                       :value      (:value best)
                       :prev-value (:value state)
                       :iter       (inc (:iter state))}))})))

;; =============================================================
;; FIREFLY ALGORITHM
;; =============================================================

(defn firefly-algorithm
  "Firefly algorithm — pairwise attraction toward brighter fireflies.
   Catalog: O46. Pattern: ITERATE + MAP-OVER.
   Each firefly moves toward all brighter fireflies, with attraction
   decreasing with distance."
  [f dim & {:keys [pop-size max-iter beta0 gamma alpha]
            :or   {pop-size 30 max-iter 200 beta0 1.0 gamma 1.0 alpha 0.2}}]
  (let [init-pop (vec (repeatedly pop-size
                                  (fn [] (let [x (mapv (fn [_] (- (rand 10) 5)) (range dim))]
                                           {:x x :value (f x)}))))]
    (c/iterative-solve
     {:init-state {:population init-pop
                   :best (apply min-key :value init-pop)
                   :value (:value (apply min-key :value init-pop))
                   :prev-value Double/MAX_VALUE}
      :max-iter   max-iter
      :converged? (c/value-converged? 1e-10)
      :step-fn    (fn [state]
                    (let [pop (:population state)
                          new-pop
                          (mapv (fn [fi]
                                  (let [x-new (reduce (fn [x fj]
                                                        (if (< (:value fj) (:value fi))
                                                          (let [r (c/vnorm (c/v- x (:x fj)))
                                                                beta (* beta0 (Math/exp (- (* gamma r r))))
                                                                rand-vec (mapv (fn [_] (* alpha (- (rand) 0.5))) (range dim))]
                                                            (c/v+ (c/v+ x (c/v* beta (c/v- (:x fj) x))) rand-vec))
                                                          x))
                                                      (:x fi)
                                                      pop)]
                                    {:x x-new :value (f x-new)}))
                                pop)
                          best (apply min-key :value (cons (:best state) new-pop))]
                      {:population new-pop :best best :x (:x best)
                       :value (:value best) :prev-value (:value state)}))})))

;; =============================================================
;; CUCKOO SEARCH
;; =============================================================

(defn cuckoo-search
  "Cuckoo search via Levy flights.
   Catalog: O47. Pattern: ITERATE + DELEGATE.
   New solutions generated via Levy flights (heavy-tailed steps).
   Worst nests are abandoned and replaced randomly."
  [f dim & {:keys [pop-size max-iter pa levy-scale]
            :or   {pop-size 25 max-iter 200 pa 0.25 levy-scale 0.5}}]
  (let [levy-step (fn [] (* levy-scale (/ (- (rand) 0.5) (max (Math/abs (- (rand) 0.5)) 0.01))))
        init-pop (vec (repeatedly pop-size
                                  (fn [] (let [x (mapv (fn [_] (- (rand 10) 5)) (range dim))]
                                           {:x x :value (f x)}))))]
    (c/iterative-solve
     {:init-state {:population (vec (sort-by :value init-pop))
                   :best (apply min-key :value init-pop)
                   :value (:value (apply min-key :value init-pop))
                   :prev-value Double/MAX_VALUE}
      :max-iter   max-iter
      :converged? (c/value-converged? 1e-10)
      :step-fn    (fn [state]
                    (let [pop (:population state)
                          new-pop (mapv (fn [nest]
                                          (let [x-new (mapv (fn [xi] (+ xi (levy-step))) (:x nest))
                                                v-new (f x-new)]
                                            (if (< v-new (:value nest))
                                              {:x x-new :value v-new}
                                              nest)))
                                        pop)
                          sorted    (vec (sort-by :value new-pop))
                          n-abandon (int (* pa pop-size))
                          kept      (vec (take (- pop-size n-abandon) sorted))
                          replaced  (vec (repeatedly n-abandon
                                                     (fn [] (let [x (mapv (fn [_] (- (rand 10) 5)) (range dim))]
                                                              {:x x :value (f x)}))))
                          final-pop (into kept replaced)
                          best      (apply min-key :value (cons (:best state) final-pop))]
                      {:population final-pop :best best :x (:x best)
                       :value (:value best) :prev-value (:value state)}))})))
