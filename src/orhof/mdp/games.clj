(ns orhof.mdp.games
  "Game theory and bandit methods.
   Pattern: EQUILIBRIUM SOLVER + PARAMETERIZED STRATEGY."
  (:require [orhof.core :as c]))

;; =============================================================
;; Bandits
;; =============================================================

(defn make-bandit
  "Create a multi-armed bandit problem.
   arms: vector of reward distributions (each is (fn [] -> reward))"
  [arms]
  {:arms arms :n-arms (count arms)})

(defn epsilon-greedy-bandit
  "Epsilon-greedy bandit strategy.
   Catalog: D57 variant. Pattern: ITERATE + PARAMETERIZE."
  [bandit & {:keys [epsilon n-rounds] :or {epsilon 0.1 n-rounds 1000}}]
  (let [n (:n-arms bandit)
        arms (:arms bandit)]
    (reduce (fn [{:keys [counts values total-reward]} _]
              (let [arm (if (< (rand) epsilon)
                          (rand-int n)
                          (apply max-key #(nth values %) (range n)))
                    r ((nth arms arm))
                    new-count (inc (nth counts arm))
                    new-value (+ (nth values arm)
                                 (/ (- r (nth values arm)) new-count))]
                {:counts (assoc counts arm new-count)
                 :values (assoc values arm new-value)
                 :total-reward (+ total-reward r)}))
            {:counts (vec (repeat n 0))
             :values (vec (repeat n 0.0))
             :total-reward 0.0}
            (range n-rounds))))

(defn ucb1-bandit
  "UCB1 bandit strategy.
   Catalog: D57. Pattern: ITERATE + PARAMETERIZE."
  [bandit & {:keys [n-rounds c] :or {n-rounds 1000 c 2.0}}]
  (let [n (:n-arms bandit)
        arms (:arms bandit)]
    (reduce (fn [{:keys [counts values total-reward]} t]
              (let [arm (if (some zero? counts)
                          (.indexOf counts 0)
                          (apply max-key
                                 (fn [i]
                                   (+ (nth values i)
                                      (* c (Math/sqrt (/ (Math/log t) (nth counts i))))))
                                 (range n)))
                    r ((nth arms arm))
                    new-count (inc (nth counts arm))
                    new-value (+ (nth values arm)
                                 (/ (- r (nth values arm)) new-count))]
                {:counts (assoc counts arm new-count)
                 :values (assoc values arm new-value)
                 :total-reward (+ total-reward r)}))
            {:counts (vec (repeat n 0))
             :values (vec (repeat n 0.0))
             :total-reward 0.0}
            (range n-rounds))))

(defn thompson-sampling
  "Thompson sampling for Bernoulli bandits.
   Catalog: D58. Pattern: ITERATE + GENERATE."
  [bandit & {:keys [n-rounds] :or {n-rounds 1000}}]
  (let [n (:n-arms bandit)
        arms (:arms bandit)
        sample-beta (fn [alpha beta]
                      (let [x (/ (double alpha) (+ alpha beta))]
                        (max 0.0 (min 1.0
                                      (+ x (* (/ 1.0 (Math/sqrt (+ alpha beta)))
                                              (- (rand) 0.5)))))))]
    (reduce (fn [{:keys [alphas betas total-reward]} _]
              (let [samples (mapv (fn [i] (sample-beta (nth alphas i) (nth betas i)))
                                  (range n))
                    arm     (apply max-key #(nth samples %) (range n))
                    r       ((nth arms arm))
                    success? (> r 0.5)]
                {:alphas (if success? (update alphas arm + 1.0) alphas)
                 :betas  (if success? betas (update betas arm + 1.0))
                 :total-reward (+ total-reward r)}))
            {:alphas (vec (repeat n 1.0))
             :betas  (vec (repeat n 1.0))
             :total-reward 0.0}
            (range n-rounds))))

;; =============================================================
;; Game Theory
;; =============================================================

(defn make-normal-form-game
  "Create a normal-form game.
   payoffs: nested map {player -> {action-profile -> payoff}}"
  [players actions payoffs]
  {:players players :actions actions :payoffs payoffs})

(defn iterated-best-response
  "Find Nash equilibrium via iterated best response.
   Catalog: D77 variant. Pattern: ITERATE (EQUILIBRIUM SOLVER).
   For 2-player games with finite actions."
  [game & {:keys [max-iter] :or {max-iter 100}}]
  (let [{:keys [players actions payoffs]} game
        init-strategies (zipmap players
                                (map (fn [p]
                                       (let [acts (actions p)
                                             n (count acts)]
                                         (zipmap acts (repeat (/ 1.0 n)))))
                                     players))]
    (reduce (fn [strategies _]
              (into {} (map (fn [p]
                              (let [acts (vec (keys (actions p)))
                                    expected (map (fn [a]
                                                    (let [other (first (remove #{p} players))
                                                          other-strat (strategies other)]
                                                      (reduce + (map (fn [[a-other prob]]
                                                                       (* prob (get-in payoffs [p [a a-other]] 0.0)))
                                                                     other-strat))))
                                                  acts)
                                    best-a (nth acts (apply max-key #(nth (vec expected) %) (range (count acts))))]
                                [p (zipmap acts (map #(if (= % best-a) 1.0 0.0) acts))]))
                            players)))
            init-strategies
            (range max-iter))))

;; =============================================================
;; FICTITIOUS PLAY
;; =============================================================

(defn fictitious-play
  "Fictitious play — best respond to empirical frequency of opponent.
   Catalog: D79. Pattern: ITERATE + REDUCE.

   Game -> Opts -> {Player -> {Action -> Prob}}

   Each player maintains a count of opponent actions and best-responds
   to the empirical distribution.

   Example:
     (fictitious-play matching-pennies-game :max-iter 1000)
     ;=> {:p1 {:H 0.52 :T 0.48} :p2 {:H 0.48 :T 0.52}}"
  [game & {:keys [max-iter] :or {max-iter 200}}]
  (let [{:keys [players actions payoffs]} game
        acts-per-player (into {} (map (fn [p] [p (vec (keys (actions p)))]) players))]
    (reduce (fn [{:keys [counts]} t]
              (let [;; Empirical frequencies from counts
                    freqs (into {} (map (fn [p]
                                          (let [total (max 1.0 (reduce + (vals (counts p))))]
                                            [p (into {} (map (fn [[a c]] [a (/ c total)])
                                                             (counts p)))]))
                                        players))
                    ;; Each player best-responds to opponent's frequency
                    chosen (into {} (map (fn [p]
                                           (let [other (first (remove #{p} players))
                                                 other-freq (freqs other)
                                                 acts (acts-per-player p)
                                                 expected (map (fn [a]
                                                                 (reduce + (map (fn [[a-o prob]]
                                                                                  (* prob (get-in payoffs [p [a a-o]] 0.0)))
                                                                                other-freq)))
                                                               acts)
                                                 best (nth acts (apply max-key #(nth (vec expected) %) (range (count acts))))]
                                             [p best]))
                                         players))
                    ;; Update counts
                    new-counts (reduce (fn [c [p a]] (update-in c [p a] (fnil inc 0)))
                                       counts chosen)]
                {:counts new-counts}))
            {:counts (into {} (map (fn [p]
                                     [p (into {} (map (fn [a] [a 0]) (acts-per-player p)))])
                                   players))}
            (range max-iter))
    ;; Convert final counts to probabilities
    (let [final (:counts (reduce (fn [{:keys [counts]} t]
                                    (let [freqs (into {} (map (fn [p]
                                                                (let [total (max 1.0 (reduce + (vals (counts p))))]
                                                                  [p (into {} (map (fn [[a c]] [a (/ c total)]) (counts p)))]))
                                                              players))
                                          chosen (into {} (map (fn [p]
                                                                 (let [other (first (remove #{p} players))
                                                                       other-freq (freqs other)
                                                                       acts (acts-per-player p)
                                                                       expected (map (fn [a]
                                                                                       (reduce + (map (fn [[a-o prob]]
                                                                                                        (* prob (get-in payoffs [p [a a-o]] 0.0)))
                                                                                                      other-freq)))
                                                                                     acts)
                                                                       best (nth acts (apply max-key #(nth (vec expected) %) (range (count acts))))]
                                                                   [p best]))
                                                               players))
                                          new-counts (reduce (fn [c [p a]] (update-in c [p a] (fnil inc 0))) counts chosen)]
                                      {:counts new-counts}))
                                  {:counts (into {} (map (fn [p] [p (into {} (map (fn [a] [a 0]) (acts-per-player p)))]) players))}
                                  (range max-iter)))]
      (into {} (map (fn [p]
                      (let [total (max 1.0 (reduce + (vals (final p))))]
                        [p (into {} (map (fn [[a c]] [a (/ (double c) total)]) (final p)))]))
                    players)))))

;; =============================================================
;; NASH EQUILIBRIUM (Support Enumeration)
;; =============================================================

(defn nash-support-enum
  "Find Nash equilibrium via support enumeration.
   Catalog: D77. Pattern: ITERATE + TRANSFORM-PROBLEM.

   Game -> {Player -> {Action -> Prob}}

   For 2-player games, checks each pair of support sets.
   Simplified: tries pure strategy profiles first, then uses
   iterated best response as fallback.

   Example:
     (nash-support-enum prisoners-dilemma)
     ;=> {:p1 {:C 0.0 :D 1.0} :p2 {:C 0.0 :D 1.0}}"
  [game]
  (let [{:keys [players actions payoffs]} game
        acts1 (vec (keys (actions (first players))))
        acts2 (vec (keys (actions (second players))))
        p1 (first players) p2 (second players)]
    ;; Check all pure strategy profiles
    (or (reduce (fn [_ [a1 a2]]
                  (let [;; Is (a1, a2) a Nash equilibrium?
                        u1 (get-in payoffs [p1 [a1 a2]] 0.0)
                        u2 (get-in payoffs [p2 [a2 a1]] 0.0)
                        ;; Check: no profitable deviation for p1
                        p1-stable? (every? (fn [a1']
                                              (<= (get-in payoffs [p1 [a1' a2]] 0.0) u1))
                                            acts1)
                        ;; Check: no profitable deviation for p2
                        p2-stable? (every? (fn [a2']
                                              (<= (get-in payoffs [p2 [a2' a1]] 0.0) u2))
                                            acts2)]
                    (when (and p1-stable? p2-stable?)
                      (reduced {p1 (zipmap acts1 (map #(if (= % a1) 1.0 0.0) acts1))
                                p2 (zipmap acts2 (map #(if (= % a2) 1.0 0.0) acts2))}))))
                nil
                (for [a1 acts1 a2 acts2] [a1 a2]))
        ;; Fallback: iterated best response
        (iterated-best-response game :max-iter 100))))
