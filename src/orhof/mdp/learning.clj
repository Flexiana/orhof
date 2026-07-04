(ns orhof.mdp.learning
  "Learning from data: MLE, Bayesian learning, KDE, EM, structure learning.
   Catalog: D10-D16. Pattern: MODEL BUILDER + ITERATE."
  (:require [orhof.core :as c]
            [orhof.math :as m]
            [orhof.diff :as d]))

;; =============================================================
;; MAXIMUM LIKELIHOOD ESTIMATION
;; =============================================================

(defn mle
  "Maximum Likelihood Estimation via gradient ascent on log-likelihood.
   Catalog: D10. Pattern: ITERATE + REDUCE.

   (Theta -> Datum -> R) -> [Datum] -> Theta -> Opts -> {:x Theta, :value R}

   log-lik-fn: (theta, datum) -> log-likelihood contribution.
   Maximizes sum of log-likelihoods over data.

   Example:
     (mle (fn [theta x] (- (* (- x theta) (- x theta)))) data [0.0])
     ;=> {:x [3.0] ...}"
  [log-lik-fn data theta0 & {:keys [max-iter lr] :or {max-iter 500 lr 0.01}}]
  (let [neg-ll (fn [theta] (- (reduce + (map #(log-lik-fn (first theta) %) data))))
        grad-f (d/gradient neg-ll)]
    (c/iterative-solve
     {:init-state {:x theta0 :value (neg-ll theta0) :grad (grad-f theta0)
                   :prev-value Double/MAX_VALUE}
      :max-iter max-iter
      :converged? (c/value-converged? 1e-6)
      :step-fn (fn [state]
                 (let [x-new (c/v+ (:x state) (c/v* (- lr) (:grad state)))]
                   {:x x-new :value (neg-ll x-new) :grad (grad-f x-new)
                    :prev-value (:value state)}))})))

;; =============================================================
;; BAYESIAN PARAMETER LEARNING
;; =============================================================

(defn bayesian-update
  "Bayesian parameter learning via sequential prior update.
   Catalog: D11. Pattern: REDUCE (fold over data).

   Prior -> [Datum] -> (Prior -> Datum -> Prior) -> Posterior

   update-fn takes (prior, datum) and returns updated prior.

   Example:
     (bayesian-update {:alpha 1 :beta 1} [1 1 0]
       (fn [prior d] (if (= d 1) (update prior :alpha + 1) (update prior :beta + 1))))
     ;=> {:alpha 3 :beta 2}"
  [prior data update-fn]
  (reduce update-fn prior data))

;; =============================================================
;; KERNEL DENSITY ESTIMATION
;; =============================================================

(defn kde
  "Kernel Density Estimation — nonparametric density estimation.
   Catalog: D12. Pattern: PARAMETERIZE + REDUCE.

   [R] -> {:keys [bandwidth kernel]} -> (R -> R)

   Returns a density function. Default kernel: Gaussian.

   Example:
     (def f (kde [0.0 1.0 2.0] :bandwidth 0.5))
     (f 1.0) ;=> ~0.5"
  [data & {:keys [bandwidth kernel]
            :or   {bandwidth 1.0}}]
  (let [kernel (or kernel (fn [u] (* (/ 1.0 (Math/sqrt (* 2 Math/PI)))
                                      (Math/exp (/ (- (* u u)) 2.0)))))
        n      (count data)
        h      bandwidth]
    (fn [x]
      (/ (reduce + (map (fn [xi] (kernel (/ (- x xi) h))) data))
         (* n h)))))

;; =============================================================
;; EM ALGORITHM (Gaussian Mixture Model)
;; =============================================================

(defn em-gmm
  "EM algorithm for Gaussian Mixture Model.
   Catalog: D13. Pattern: ITERATE (comp m-step e-step).

   [R] -> Int -> Opts -> {:means [R], :variances [R], :weights [R]}

   Example:
     (em-gmm [0.0 1.0 5.0 6.0] 2 :max-iter 50)
     ;=> {:means [0.5 5.5] :variances [0.25 0.25] :weights [0.5 0.5]}"
  [data k & {:keys [max-iter] :or {max-iter 100}}]
  (let [n (count data)
        ;; Initialize: spread means evenly, unit variance, equal weights
        init-means (mapv (fn [j] (nth data (int (* (/ (+ j 0.5) k) n)))) (range k))
        init-vars  (vec (repeat k 1.0))
        init-weights (vec (repeat k (/ 1.0 k)))
        ;; Gaussian PDF
        gauss (fn [x mu sigma2]
                (/ (Math/exp (/ (- (* (- x mu) (- x mu))) (* 2 sigma2)))
                   (Math/sqrt (* 2 Math/PI sigma2))))]
    (c/iterative-solve
     {:init-state {:means init-means :variances init-vars :weights init-weights
                   :value Double/MAX_VALUE :prev-value Double/MAX_VALUE}
      :max-iter max-iter
      :converged? (c/value-converged? 1e-8)
      :step-fn
      (fn [state]
        (let [{:keys [means variances weights]} state
              ;; E-step: compute responsibilities
              responsibilities
              (mapv (fn [xi]
                      (let [raw (mapv (fn [j] (* (nth weights j)
                                                  (gauss xi (nth means j) (max (nth variances j) 1e-10))))
                                      (range k))
                            Z   (max (reduce + raw) 1e-30)]
                        (mapv #(/ % Z) raw)))
                    data)
              ;; M-step: update parameters
              nk     (mapv (fn [j] (reduce + (map #(nth % j) responsibilities))) (range k))
              new-weights (mapv #(/ % n) nk)
              new-means   (mapv (fn [j]
                                  (/ (reduce + (map-indexed (fn [i xi] (* (nth (nth responsibilities i) j) xi))
                                                            data))
                                     (max (nth nk j) 1e-30)))
                                (range k))
              new-vars    (mapv (fn [j]
                                  (/ (reduce + (map-indexed (fn [i xi]
                                                              (let [d (- xi (nth new-means j))]
                                                                (* (nth (nth responsibilities i) j) d d)))
                                                            data))
                                     (max (nth nk j) 1e-30)))
                                (range k))
              ;; Log-likelihood for convergence check
              ll (reduce + (map (fn [xi]
                                  (Math/log (max 1e-300
                                                 (reduce + (map (fn [j]
                                                                  (* (nth new-weights j)
                                                                     (gauss xi (nth new-means j)
                                                                            (max (nth new-vars j) 1e-10))))
                                                                (range k))))))
                                data))]
          {:means new-means :variances new-vars :weights new-weights
           :value (- ll) :prev-value (:value state)}))})))

;; =============================================================
;; BAYESIAN NETWORK SCORING (BIC)
;; =============================================================

(defn bic-score
  "Bayesian Information Criterion score for a BN structure.
   Catalog: D14. Pattern: REDUCE + MAP-OVER.

   {Node -> [Parent]} -> [{Node -> Val}] -> R

   Lower BIC = better model. BIC = -2*LL + k*log(n)."
  [structure data]
  (let [n (count data)
        nodes (keys structure)
        ll (reduce + (map (fn [datum]
                            (reduce + (map (fn [node]
                                             (let [parents (get structure node [])
                                                   ;; Empirical conditional probability (simplified: counting)
                                                   parent-vals (mapv datum parents)
                                                   matching (filter (fn [d]
                                                                      (= parent-vals (mapv d parents)))
                                                                    data)
                                                   node-val (datum node)
                                                   count-match (count (filter #(= node-val (% node)) matching))
                                                   total (max (count matching) 1)]
                                               (Math/log (max 1e-300 (/ (double count-match) total)))))
                                           nodes)))
                          data))
        ;; Number of parameters: sum of (|vals(node)| - 1) * product(|vals(parent)|)
        k (reduce + (map (fn [node]
                           (let [parents (get structure node [])
                                 node-vals (count (distinct (map node data)))
                                 parent-configs (if (empty? parents) 1
                                                    (reduce * (map (fn [p] (count (distinct (map p data)))) parents)))]
                             (* (dec node-vals) parent-configs)))
                         nodes))]
    (+ (* -2.0 ll) (* k (Math/log n)))))

;; =============================================================
;; K2 STRUCTURE SEARCH
;; =============================================================

(defn k2-search
  "K2 greedy structure learning for Bayesian networks.
   Catalog: D15. Pattern: ITERATE + DELEGATE + BRANCH.

   [Keyword] -> [{Keyword -> Val}] -> Opts -> {Keyword -> [Keyword]}

   Greedily adds parent that most improves BIC score.

   Example:
     (k2-search [:a :b :c] data :max-parents 2)
     ;=> {:a [] :b [:a] :c [:a :b]}"
  [node-order data & {:keys [max-parents] :or {max-parents 3}}]
  (reduce (fn [structure node]
            (let [candidates (take-while #(not= % node) node-order)]
              ;; Greedily add best parent
              (reduce (fn [struct _]
                        (let [current-parents (get struct node [])
                              remaining (remove (set current-parents) candidates)
                              scores (map (fn [candidate]
                                            (let [new-struct (assoc struct node (conj current-parents candidate))]
                                              {:parent candidate :score (bic-score new-struct data)}))
                                          remaining)
                              current-score (bic-score struct data)]
                          (if (empty? scores)
                            (reduced struct)
                            (let [best (apply min-key :score scores)]
                              (if (< (:score best) current-score)
                                (assoc struct node (conj current-parents (:parent best)))
                                (reduced struct))))))
                      (assoc structure node [])
                      (range max-parents))))
          {}
          node-order))
